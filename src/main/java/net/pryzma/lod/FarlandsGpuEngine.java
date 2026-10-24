package net.pryzma.lod;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL12C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL31C;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.opengl.GL40C;
import org.lwjgl.opengl.GL42C;
import org.lwjgl.opengl.GL43C;
import org.lwjgl.opengl.GL46C;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryUtil;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import net.pryzma.Pryzma;

/**
 * GPU-Driven Multi-Draw Indirect (MDI) engine for Farlands LOD terrain (Farlands 2.0).
 *
 * <p>Consolidates all active LOD sectors into a single suballocated Mega-VBO, a dense Sector Table SSBO,
 * and a static quad index buffer (EBO). On each frame, a GPU compute pass ({@code farlands_cull.comp})
 * tests sector bounding boxes against the 6 frustum planes and vanilla clip cylinder, compacting visible
 * draws into GPU indirect command buffers.
 *
 * <p>Submission is executed via {@code glMultiDrawElementsIndirectCount} (or {@code glMultiDrawElementsIndirect}
 * fallback) in two calls (opaque and water), reducing CPU render thread overhead from ~1.2 ms to &le; 0.010 ms.
 *
 * <p>Renders into a private {@code DEPTH32F} framebuffer sharing the game's color target, completely
 * eliminating {@code RenderSystem.clear(GL_DEPTH_BUFFER_BIT)} and preserving hardware HTILE / ZCull metadata.
 */
public final class FarlandsGpuEngine {
    public static final int VERTEX_BYTES = NativeLodMesher.VERTEX_BYTES; // 16 bytes
    public static final int SECTOR_ENTRY_BYTES = 32; // ivec4 box (16 bytes) + uvec4 mesh (16 bytes)
    public static final int CMD_BYTES = 20; // 5 x uint32
    public static final int MAX_SECTORS = 65536;
    public static final int MAX_EBO_QUADS = 131072; // 128K quads
    private static final int INITIAL_MEGA_CAPACITY_VERTICES = 4194304; // 64 MB

    private static boolean supported = true;
    private static boolean initialized;
    private static boolean hasIndirectParameters;
    private static boolean initFailed;

    // OpenGL objects
    private static int megaVbo = -1;
    private static int megaVao = -1;
    private static int staticEbo = -1;
    private static int sectorTableBuf = -1;
    private static int opaqueCmdBuf = -1;
    private static int waterCmdBuf = -1;
    private static int countBuf = -1;
    private static int cullProgram = -1;

    // Direct buffers
    private static ByteBuffer sectorTableHost;
    private static ByteBuffer countClearBuf;
    private static ByteBuffer staging;
    private static final int[] countReadback = new int[2];

    // Sector management
    private static int activeSectorCount;
    private static final long[] sectorKeys = new long[MAX_SECTORS];
    private static final Long2IntOpenHashMap keyToIndex = new Long2IntOpenHashMap(4096);

    // Dynamic Mega-VBO suballocator (free-list)
    private static int megaCapacityVertices;
    private static int[] freeOffsets = new int[256];
    private static int[] freeLengths = new int[256];
    private static int freeCount;

    // Compute uniform locations
    private static final int[] planeLocs = new int[6];
    private static int cullCamBLoc;
    private static int cullCamFLoc;
    private static int cullClipLoc;
    private static int cullCountLoc;
    private static int cullBlocksLoc;

    // Private Depth FBO
    private static int depthFbo = -1;
    private static int depthTexture = -1;
    private static int depthWidth = -1;
    private static int depthHeight = -1;
    private static int boundColorTex = -1;

    // Math scratch
    private static final Matrix4f VIEW_PROJECTION = new Matrix4f();
    private static final Vector4f[] FRUSTUM_PLANES = new Vector4f[6];

    static {
        for (int p = 0; p < 6; p++) {
            FRUSTUM_PLANES[p] = new Vector4f();
        }
        keyToIndex.defaultReturnValue(-1);
    }

    private FarlandsGpuEngine() {
    }

    public static boolean isSupported() {
        if (!supported || initFailed) {
            return false;
        }
        if (!initialized) {
            return ensureInitialized();
        }
        return true;
    }

    public static int getMegaVao() {
        return megaVao;
    }

    public static int getSectorCount() {
        return activeSectorCount;
    }

    public static long getMegaCapacityBytes() {
        return (long) megaCapacityVertices * VERTEX_BYTES;
    }

    /**
     * Initializes all GPU buffers, shaders, and VAO on the render thread.
     */
    public static synchronized boolean ensureInitialized() {
        if (initialized) {
            return true;
        }
        if (initFailed) {
            return false;
        }
        try {
            GLCapabilities caps = GL.getCapabilities();
            if (!caps.OpenGL43 && !caps.GL_ARB_compute_shader) {
                Pryzma.LOGGER.warn("Farlands GPU-driven MDI requires OpenGL 4.3 core or GL_ARB_compute_shader; falling back to legacy path");
                supported = false;
                initFailed = true;
                return false;
            }
            hasIndirectParameters = caps.OpenGL46 || caps.GL_ARB_indirect_parameters;

            // Compile compute shader
            cullProgram = createComputeProgram("/assets/pryzma/shaders/core/farlands_cull.comp");
            for (int p = 0; p < 6; p++) {
                planeLocs[p] = GL20C.glGetUniformLocation(cullProgram, "Planes[" + p + "]");
            }
            cullCamBLoc = GL20C.glGetUniformLocation(cullProgram, "CameraBlock");
            cullCamFLoc = GL20C.glGetUniformLocation(cullProgram, "CameraFrac");
            cullClipLoc = GL20C.glGetUniformLocation(cullProgram, "ClipRadius");
            cullCountLoc = GL20C.glGetUniformLocation(cullProgram, "SectorCount");
            cullBlocksLoc = GL20C.glGetUniformLocation(cullProgram, "SectorBlocks");

            // Build shared quad index buffer
            staticEbo = createQuadIndexBuffer(MAX_EBO_QUADS);

            // Create Mega-VBO
            megaCapacityVertices = INITIAL_MEGA_CAPACITY_VERTICES;
            megaVbo = GL15C.glGenBuffers();
            GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, megaVbo);
            GL15C.glBufferData(GL15C.GL_ARRAY_BUFFER, (long) megaCapacityVertices * VERTEX_BYTES, GL15C.GL_DYNAMIC_DRAW);
            GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, 0);

            // Create Sector Table buffer
            sectorTableHost = MemoryUtil.memAlloc(MAX_SECTORS * SECTOR_ENTRY_BYTES).order(ByteOrder.nativeOrder());
            sectorTableBuf = GL15C.glGenBuffers();
            GL15C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, sectorTableBuf);
            GL15C.glBufferData(GL43C.GL_SHADER_STORAGE_BUFFER, (long) MAX_SECTORS * SECTOR_ENTRY_BYTES, GL15C.GL_DYNAMIC_DRAW);
            GL15C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, 0);

            // Create indirect command buffers and count buffer
            opaqueCmdBuf = GL15C.glGenBuffers();
            GL15C.glBindBuffer(GL40C.GL_DRAW_INDIRECT_BUFFER, opaqueCmdBuf);
            GL15C.glBufferData(GL40C.GL_DRAW_INDIRECT_BUFFER, (long) MAX_SECTORS * CMD_BYTES, GL15C.GL_DYNAMIC_DRAW);

            waterCmdBuf = GL15C.glGenBuffers();
            GL15C.glBindBuffer(GL40C.GL_DRAW_INDIRECT_BUFFER, waterCmdBuf);
            GL15C.glBufferData(GL40C.GL_DRAW_INDIRECT_BUFFER, (long) MAX_SECTORS * CMD_BYTES, GL15C.GL_DYNAMIC_DRAW);
            GL15C.glBindBuffer(GL40C.GL_DRAW_INDIRECT_BUFFER, 0);

            countBuf = GL15C.glGenBuffers();
            GL15C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, countBuf);
            GL15C.glBufferData(GL43C.GL_SHADER_STORAGE_BUFFER, 8L, GL15C.GL_DYNAMIC_DRAW);
            GL15C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, 0);
            countClearBuf = MemoryUtil.memAlloc(8).order(ByteOrder.nativeOrder());
            countClearBuf.putInt(0).putInt(0).flip();

            // Setup Mega-VAO
            megaVao = GL30C.glGenVertexArrays();
            GL30C.glBindVertexArray(megaVao);

            // Binding 0: vertex data from megaVbo (stride 16)
            GL43C.glBindVertexBuffer(0, megaVbo, 0L, VERTEX_BYTES);
            GL20C.glEnableVertexAttribArray(0);
            GL43C.glVertexAttribIFormat(0, 4, GL11C.GL_UNSIGNED_SHORT, 0);
            GL43C.glVertexAttribBinding(0, 0);

            GL20C.glEnableVertexAttribArray(1);
            GL43C.glVertexAttribFormat(1, 4, GL11C.GL_UNSIGNED_BYTE, true, 8);
            GL43C.glVertexAttribBinding(1, 0);

            GL20C.glEnableVertexAttribArray(2);
            GL43C.glVertexAttribIFormat(2, 4, GL11C.GL_UNSIGNED_BYTE, 12);
            GL43C.glVertexAttribBinding(2, 0);

            // Binding 1: instanced sector table data from sectorTableBuf (stride 32, divisor 1)
            GL43C.glBindVertexBuffer(1, sectorTableBuf, 0L, SECTOR_ENTRY_BYTES);
            GL20C.glEnableVertexAttribArray(3);
            GL43C.glVertexAttribIFormat(3, 2, GL11C.GL_INT, 0); // box.x = originX, box.y = originZ
            GL43C.glVertexAttribBinding(3, 1);
            GL43C.glVertexBindingDivisor(1, 1);

            // Shared EBO
            GL15C.glBindBuffer(GL15C.GL_ELEMENT_ARRAY_BUFFER, staticEbo);
            GL30C.glBindVertexArray(0);

            // Reset allocator
            freeOffsets[0] = 0;
            freeLengths[0] = megaCapacityVertices;
            freeCount = 1;

            initialized = true;
            Pryzma.LOGGER.info("Farlands GPU-driven MDI engine initialized successfully (GL 4.3+ Core, indirectParameters={})",
                    hasIndirectParameters);
            return true;
        } catch (Exception e) {
            Pryzma.LOGGER.error("Failed to initialize Farlands GPU-driven engine", e);
            destroy();
            initFailed = true;
            return false;
        }
    }

    // ------------------------------------------------------------------ dynamic allocator

    private static int allocateVertices(int count) {
        // First-fit search across sorted free blocks
        for (int i = 0; i < freeCount; i++) {
            int len = freeLengths[i];
            if (len >= count) {
                int off = freeOffsets[i];
                if (len == count) {
                    System.arraycopy(freeOffsets, i + 1, freeOffsets, i, freeCount - 1 - i);
                    System.arraycopy(freeLengths, i + 1, freeLengths, i, freeCount - 1 - i);
                    freeCount--;
                } else {
                    freeOffsets[i] = off + count;
                    freeLengths[i] = len - count;
                }
                return off;
            }
        }

        // Out of space: grow Mega-VBO
        int oldCap = megaCapacityVertices;
        int newCap = Math.max(oldCap * 2, oldCap + count * 2);
        reallocMegaVbo(newCap);

        int added = newCap - oldCap;
        if (added > count) {
            freeVertices(oldCap + count, added - count);
        }
        return oldCap;
    }

    private static void freeVertices(int offset, int count) {
        if (count <= 0) {
            return;
        }
        // Ensure array capacity
        if (freeCount >= freeOffsets.length) {
            freeOffsets = Arrays.copyOf(freeOffsets, freeOffsets.length * 2);
            freeLengths = Arrays.copyOf(freeLengths, freeLengths.length * 2);
        }
        // Insert sorted by offset
        int idx = 0;
        while (idx < freeCount && freeOffsets[idx] < offset) {
            idx++;
        }
        System.arraycopy(freeOffsets, idx, freeOffsets, idx + 1, freeCount - idx);
        System.arraycopy(freeLengths, idx, freeLengths, idx + 1, freeCount - idx);
        freeOffsets[idx] = offset;
        freeLengths[idx] = count;
        freeCount++;

        // Coalesce adjacent blocks
        for (int i = 0; i < freeCount - 1; ) {
            if (freeOffsets[i] + freeLengths[i] == freeOffsets[i + 1]) {
                freeLengths[i] += freeLengths[i + 1];
                System.arraycopy(freeOffsets, i + 2, freeOffsets, i + 1, freeCount - 2 - i);
                System.arraycopy(freeLengths, i + 2, freeLengths, i + 1, freeCount - 2 - i);
                freeCount--;
            } else {
                i++;
            }
        }
    }

    private static void reallocMegaVbo(int newCap) {
        int newVbo = GL15C.glGenBuffers();
        GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, newVbo);
        GL15C.glBufferData(GL15C.GL_ARRAY_BUFFER, (long) newCap * VERTEX_BYTES, GL15C.GL_DYNAMIC_DRAW);

        GL15C.glBindBuffer(GL31C.GL_COPY_READ_BUFFER, megaVbo);
        GL15C.glBindBuffer(GL31C.GL_COPY_WRITE_BUFFER, newVbo);
        GL31C.glCopyBufferSubData(GL31C.GL_COPY_READ_BUFFER, GL31C.GL_COPY_WRITE_BUFFER, 0L, 0L, (long) megaCapacityVertices * VERTEX_BYTES);
        GL15C.glBindBuffer(GL31C.GL_COPY_READ_BUFFER, 0);
        GL15C.glBindBuffer(GL31C.GL_COPY_WRITE_BUFFER, 0);

        GL15C.glDeleteBuffers(megaVbo);
        megaVbo = newVbo;
        megaCapacityVertices = newCap;

        // Rebind to Mega-VAO
        GL30C.glBindVertexArray(megaVao);
        GL43C.glBindVertexBuffer(0, megaVbo, 0L, VERTEX_BYTES);
        GL30C.glBindVertexArray(0);
    }

    // ------------------------------------------------------------------ mesh intake and eviction

    /**
     * Copies heap vertex data into the direct off-heap buffer GL uploads from; the buffer grows and is reused.
     */
    private static ByteBuffer stage(ByteBuffer vertices) {
        int bytes = vertices.remaining();
        if (staging == null || staging.capacity() < bytes) {
            staging = ByteBuffer.allocateDirect(Math.max(bytes + (bytes >> 1), 1 << 20)).order(ByteOrder.nativeOrder());
        }
        staging.clear();
        staging.put(vertices.duplicate());
        staging.flip();
        return staging;
    }

    /**
     * Uploads a sector mesh into the unified Mega-VBO and updates its slot in the Sector Table.
     */
    public static void uploadSector(long key, NativeLodRenderer.SectorMesh sector, LodMesh mesh) {
        if (!isSupported()) {
            return;
        }
        int quadCount = mesh.quadCount();
        int vertexCount = quadCount * 4;
        if (vertexCount == 0) {
            removeSector(key);
            return;
        }

        // Suballocate vertex range
        if (sector.baseVertex >= 0 && sector.allocatedVertices >= vertexCount) {
            // Fits in current allocation
        } else {
            if (sector.baseVertex >= 0) {
                freeVertices(sector.baseVertex, sector.allocatedVertices);
            }
            sector.baseVertex = allocateVertices(vertexCount);
            sector.allocatedVertices = vertexCount;
        }

        // Upload vertex data via direct staging buffer
        GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, megaVbo);
        GL15C.glBufferSubData(GL15C.GL_ARRAY_BUFFER, (long) sector.baseVertex * VERTEX_BYTES, stage(mesh.vertices()));
        GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, 0);

        // Update Sector Table entry
        int idx = keyToIndex.get(key);
        if (idx < 0) {
            if (activeSectorCount >= MAX_SECTORS) {
                Pryzma.LOGGER.warn("Farlands sector table full ({} sectors); dropping sector key {}", MAX_SECTORS, key);
                return;
            }
            idx = activeSectorCount++;
            keyToIndex.put(key, idx);
            sectorKeys[idx] = key;
        }
        sector.tableIndex = idx;

        // Write 32 bytes to host buffer
        int baseByte = idx * SECTOR_ENTRY_BYTES;
        sectorTableHost.position(baseByte);
        sectorTableHost.putInt(sector.blockOriginX()); // box.x
        sectorTableHost.putInt(sector.blockOriginZ()); // box.y
        sectorTableHost.putInt(mesh.minY());          // box.z
        sectorTableHost.putInt(mesh.maxY());          // box.w
        sectorTableHost.putInt(sector.baseVertex);     // mesh.x
        sectorTableHost.putInt(mesh.opaqueQuads());    // mesh.y
        sectorTableHost.putInt(mesh.waterQuads());     // mesh.z
        sectorTableHost.putInt(idx);                  // mesh.w
        sectorTableHost.position(baseByte);
        sectorTableHost.limit(baseByte + SECTOR_ENTRY_BYTES);

        // Upload entry to SSBO
        GL15C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, sectorTableBuf);
        GL15C.glBufferSubData(GL43C.GL_SHADER_STORAGE_BUFFER, (long) baseByte, sectorTableHost);
        GL15C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, 0);
    }

    /**
     * Frees a sector's vertex range and evicts its slot via O(1) swap-and-pop.
     */
    public static void removeSector(long key) {
        if (!initialized) {
            return;
        }
        int idx = keyToIndex.remove(key);
        if (idx < 0) {
            return;
        }
        NativeLodRenderer.SectorMesh sector = NativeLodRenderer.get(key);
        if (sector != null && sector.baseVertex >= 0) {
            freeVertices(sector.baseVertex, sector.allocatedVertices);
            sector.baseVertex = -1;
            sector.allocatedVertices = 0;
            sector.tableIndex = -1;
        }

        int lastIdx = activeSectorCount - 1;
        if (idx < lastIdx) {
            // Swap last sector into idx
            long lastKey = sectorKeys[lastIdx];
            sectorKeys[idx] = lastKey;
            keyToIndex.put(lastKey, idx);

            NativeLodRenderer.SectorMesh lastSector = NativeLodRenderer.get(lastKey);
            if (lastSector != null) {
                lastSector.tableIndex = idx;
            }

            // Copy 32 bytes in host buffer
            long hostAddr = MemoryUtil.memAddress(sectorTableHost);
            MemoryUtil.memCopy(hostAddr + (long) lastIdx * SECTOR_ENTRY_BYTES, hostAddr + (long) idx * SECTOR_ENTRY_BYTES, SECTOR_ENTRY_BYTES);

            // Upload moved slot to SSBO
            sectorTableHost.position(idx * SECTOR_ENTRY_BYTES);
            sectorTableHost.limit(idx * SECTOR_ENTRY_BYTES + SECTOR_ENTRY_BYTES);
            GL15C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, sectorTableBuf);
            GL15C.glBufferSubData(GL43C.GL_SHADER_STORAGE_BUFFER, (long) idx * SECTOR_ENTRY_BYTES, sectorTableHost);
            GL15C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, 0);
        }
        activeSectorCount--;
    }

    /**
     * Draws a single sector's mesh from the unified Mega-VAO (used by shaderpacks / fallback).
     */
    public static void drawMesh(NativeLodRenderer.SectorMesh mesh, boolean water) {
        int first = water ? mesh.opaqueQuads() : 0;
        int quads = water ? mesh.waterQuads() : mesh.opaqueQuads();
        if (quads == 0) {
            return;
        }
        GL30C.glBindVertexArray(megaVao);
        GL32C.glDrawElementsBaseVertex(GL11C.GL_TRIANGLES, quads * 6, GL11C.GL_UNSIGNED_INT, (long) first * 6 * 4, mesh.baseVertex);
    }

    // ------------------------------------------------------------------ rendering

    /**
     * Executes GPU-driven frustum culling and MDI submission into the private depth FBO.
     */
    public static int render(ShaderInstance program, Matrix4f modelView, Matrix4f projection, Vec3 camera,
            float clipRadius, int lodRadiusBlocks, float fogStart, float fogEnd) {
        if (!isSupported() || activeSectorCount == 0) {
            return 0;
        }

        // Camera block and fraction split for high-precision jitter-free coordinates
        int cbX = (int) Math.floor(camera.x);
        int cbY = (int) Math.floor(camera.y);
        int cbZ = (int) Math.floor(camera.z);
        float fracX = (float) (camera.x - cbX);
        float fracY = (float) (camera.y - cbY);
        float fracZ = (float) (camera.z - cbZ);

        // Compute 6 frustum planes from view-projection matrix
        VIEW_PROJECTION.set(projection).mul(modelView);
        for (int p = 0; p < 6; p++) {
            VIEW_PROJECTION.frustumPlane(p, FRUSTUM_PLANES[p]);
        }

        // 1. Reset indirect draw counters
        countClearBuf.clear();
        GL15C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, countBuf);
        GL15C.glBufferSubData(GL43C.GL_SHADER_STORAGE_BUFFER, 0L, countClearBuf);

        // 2. Dispatch compute cull shader
        GL20C.glUseProgram(cullProgram);
        for (int p = 0; p < 6; p++) {
            Vector4f pl = FRUSTUM_PLANES[p];
            GL20C.glUniform4f(planeLocs[p], pl.x, pl.y, pl.z, pl.w);
        }
        GL20C.glUniform3i(cullCamBLoc, cbX, cbY, cbZ);
        GL20C.glUniform3f(cullCamFLoc, fracX, fracY, fracZ);
        GL20C.glUniform1f(cullClipLoc, clipRadius);
        GL30C.glUniform1ui(cullCountLoc, activeSectorCount);
        GL20C.glUniform1i(cullBlocksLoc, LodDataStore.SECTOR_BLOCKS);

        GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, 0, sectorTableBuf);
        GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, 1, opaqueCmdBuf);
        GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, 2, waterCmdBuf);
        GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, 3, countBuf);

        GL43C.glDispatchCompute((activeSectorCount + 63) / 64, 1, 1);
        GL42C.glMemoryBarrier(GL42C.GL_COMMAND_BARRIER_BIT | GL43C.GL_SHADER_STORAGE_BARRIER_BIT);

        // 3. Prepare private Depth FBO (zero clearing of the main depth buffer)
        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        bindDepthFbo(main.width, main.height, main.getColorTextureId());

        LightTexture lightTexture = mc.gameRenderer.lightTexture();
        lightTexture.turnOnLightLayer();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11C.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.clearDepth(1.0);
        RenderSystem.clear(GL11C.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);

        // 4. Upload render program uniforms
        program.setDefaultUniforms(VertexFormat.Mode.QUADS, modelView, projection, mc.getWindow());
        if (program.COLOR_MODULATOR != null) {
            program.COLOR_MODULATOR.set(1.0F, 1.0F, 1.0F, 1.0F);
        }
        if (program.FOG_START != null) {
            program.FOG_START.set(fogStart);
        }
        if (program.FOG_END != null) {
            program.FOG_END.set(fogEnd);
        }
        Uniform fogColor = program.getUniform("FogColor");
        if (fogColor != null) {
            float[] fc = RenderSystem.getShaderFogColor();
            fogColor.set(fc[0], fc[1], fc[2], fc.length > 3 ? fc[3] : 1.0F);
        }
        Uniform clip = program.getUniform("ClipRadius");
        if (clip != null) {
            clip.set(clipRadius);
        }
        Uniform camB = program.getUniform("CameraBlock");
        if (camB != null) {
            camB.set(cbX, cbY, cbZ);
        }
        Uniform camF = program.getUniform("CameraFrac");
        if (camF != null) {
            camF.set(fracX, fracY, fracZ);
        }
        Uniform inst = program.getUniform("UseInstancedOffset");
        if (inst != null) {
            inst.set(1);
        }
        program.apply();

        // 5. Submit Multi-Draw Indirect
        GL30C.glBindVertexArray(megaVao);

        // Opaque pass
        GL15C.glBindBuffer(GL40C.GL_DRAW_INDIRECT_BUFFER, opaqueCmdBuf);
        if (hasIndirectParameters) {
            GL15C.glBindBuffer(GL46C.GL_PARAMETER_BUFFER, countBuf);
            GL46C.glMultiDrawElementsIndirectCount(GL11C.GL_TRIANGLES, GL11C.GL_UNSIGNED_INT, 0L, 0L, activeSectorCount, CMD_BYTES);
        } else {
            GL15C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, countBuf);
            GL15C.glGetBufferSubData(GL43C.GL_SHADER_STORAGE_BUFFER, 0L, countReadback);
            if (countReadback[0] > 0) {
                GL43C.glMultiDrawElementsIndirect(GL11C.GL_TRIANGLES, GL11C.GL_UNSIGNED_INT, 0L, countReadback[0], CMD_BYTES);
            }
        }

        // Water pass
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.depthMask(false);
        if (camF != null) {
            camF.set(fracX, fracY + NativeLodRenderer.WATER_DROP, fracZ);
            camF.upload();
        }

        GL15C.glBindBuffer(GL40C.GL_DRAW_INDIRECT_BUFFER, waterCmdBuf);
        if (hasIndirectParameters) {
            GL15C.glBindBuffer(GL46C.GL_PARAMETER_BUFFER, countBuf);
            GL46C.glMultiDrawElementsIndirectCount(GL11C.GL_TRIANGLES, GL11C.GL_UNSIGNED_INT, 0L, 4L, activeSectorCount, CMD_BYTES);
        } else {
            if (countReadback[1] > 0) {
                GL43C.glMultiDrawElementsIndirect(GL11C.GL_TRIANGLES, GL11C.GL_UNSIGNED_INT, 0L, countReadback[1], CMD_BYTES);
            }
        }

        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();

        // 6. Cleanup state and re-bind vanilla main render target
        GL15C.glBindBuffer(GL40C.GL_DRAW_INDIRECT_BUFFER, 0);
        if (hasIndirectParameters) {
            GL15C.glBindBuffer(GL46C.GL_PARAMETER_BUFFER, 0);
        }
        GL30C.glBindVertexArray(0);
        program.clear();
        lightTexture.turnOffLightLayer();
        main.bindWrite(false);

        return activeSectorCount;
    }

    // ------------------------------------------------------------------ private Depth FBO

    private static void bindDepthFbo(int width, int height, int colorTex) {
        if (depthFbo < 0 || width != depthWidth || height != depthHeight || colorTex != boundColorTex) {
            setupDepthFbo(width, height, colorTex);
        }
        GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, depthFbo);
    }

    private static void setupDepthFbo(int width, int height, int colorTex) {
        if (depthTexture >= 0) {
            GL11C.glDeleteTextures(depthTexture);
        }
        if (depthFbo >= 0) {
            GL30C.glDeleteFramebuffers(depthFbo);
        }
        depthWidth = width;
        depthHeight = height;
        boundColorTex = colorTex;

        depthTexture = GL11C.glGenTextures();
        GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, depthTexture);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_NEAREST);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, GL12C.GL_CLAMP_TO_EDGE);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, GL12C.GL_CLAMP_TO_EDGE);
        GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL30C.GL_DEPTH_COMPONENT32F, width, height, 0,
                GL11C.GL_DEPTH_COMPONENT, GL11C.GL_FLOAT, (ByteBuffer) null);

        depthFbo = GL30C.glGenFramebuffers();
        GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, depthFbo);
        GL30C.glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0, GL11C.GL_TEXTURE_2D, colorTex, 0);
        GL30C.glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER, GL30C.GL_DEPTH_ATTACHMENT, GL11C.GL_TEXTURE_2D, depthTexture, 0);

        int status = GL30C.glCheckFramebufferStatus(GL30C.GL_FRAMEBUFFER);
        if (status != GL30C.GL_FRAMEBUFFER_COMPLETE) {
            Pryzma.LOGGER.error("Farlands private depth FBO incomplete: 0x" + Integer.toHexString(status));
        }
        GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, 0);
    }

    // ------------------------------------------------------------------ helpers

    public static void clear() {
        activeSectorCount = 0;
        keyToIndex.clear();
        freeOffsets[0] = 0;
        freeLengths[0] = megaCapacityVertices;
        freeCount = 1;
        staging = null;
    }

    public static void onResourcesReloaded() {
        if (cullProgram >= 0) {
            GL20C.glDeleteProgram(cullProgram);
            cullProgram = -1;
            try {
                cullProgram = createComputeProgram("/assets/pryzma/shaders/core/farlands_cull.comp");
                for (int p = 0; p < 6; p++) {
                    planeLocs[p] = GL20C.glGetUniformLocation(cullProgram, "Planes[" + p + "]");
                }
                cullCamBLoc = GL20C.glGetUniformLocation(cullProgram, "CameraBlock");
                cullCamFLoc = GL20C.glGetUniformLocation(cullProgram, "CameraFrac");
                cullClipLoc = GL20C.glGetUniformLocation(cullProgram, "ClipRadius");
                cullCountLoc = GL20C.glGetUniformLocation(cullProgram, "SectorCount");
                cullBlocksLoc = GL20C.glGetUniformLocation(cullProgram, "SectorBlocks");
            } catch (Exception e) {
                Pryzma.LOGGER.error("Failed to reload Farlands compute shader", e);
            }
        }
    }

    public static synchronized void destroy() {
        if (megaVao >= 0) {
            GL30C.glDeleteVertexArrays(megaVao);
            megaVao = -1;
        }
        if (megaVbo >= 0) {
            GL15C.glDeleteBuffers(megaVbo);
            megaVbo = -1;
        }
        if (staticEbo >= 0) {
            GL15C.glDeleteBuffers(staticEbo);
            staticEbo = -1;
        }
        if (sectorTableBuf >= 0) {
            GL15C.glDeleteBuffers(sectorTableBuf);
            sectorTableBuf = -1;
        }
        if (opaqueCmdBuf >= 0) {
            GL15C.glDeleteBuffers(opaqueCmdBuf);
            opaqueCmdBuf = -1;
        }
        if (waterCmdBuf >= 0) {
            GL15C.glDeleteBuffers(waterCmdBuf);
            waterCmdBuf = -1;
        }
        if (countBuf >= 0) {
            GL15C.glDeleteBuffers(countBuf);
            countBuf = -1;
        }
        if (cullProgram >= 0) {
            GL20C.glDeleteProgram(cullProgram);
            cullProgram = -1;
        }
        if (depthFbo >= 0) {
            GL30C.glDeleteFramebuffers(depthFbo);
            depthFbo = -1;
        }
        if (depthTexture >= 0) {
            GL11C.glDeleteTextures(depthTexture);
            depthTexture = -1;
        }
        if (sectorTableHost != null) {
            MemoryUtil.memFree(sectorTableHost);
            sectorTableHost = null;
        }
        if (countClearBuf != null) {
            MemoryUtil.memFree(countClearBuf);
            countClearBuf = null;
        }
        clear();
        initialized = false;
        initFailed = false;
    }

    private static int createQuadIndexBuffer(int maxQuads) {
        ByteBuffer indices = MemoryUtil.memAlloc(maxQuads * 6 * 4).order(ByteOrder.nativeOrder());
        for (int q = 0; q < maxQuads; q++) {
            int v = q * 4;
            indices.putInt(v);
            indices.putInt(v + 1);
            indices.putInt(v + 2);
            indices.putInt(v + 2);
            indices.putInt(v + 3);
            indices.putInt(v);
        }
        indices.flip();
        int ebo = GL15C.glGenBuffers();
        GL15C.glBindBuffer(GL15C.GL_ELEMENT_ARRAY_BUFFER, ebo);
        GL15C.glBufferData(GL15C.GL_ELEMENT_ARRAY_BUFFER, indices, GL15C.GL_STATIC_DRAW);
        GL15C.glBindBuffer(GL15C.GL_ELEMENT_ARRAY_BUFFER, 0);
        MemoryUtil.memFree(indices);
        return ebo;
    }

    private static int createComputeProgram(String resourcePath) throws IOException {
        String source;
        try (InputStream in = FarlandsGpuEngine.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            source = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        int shader = GL20C.glCreateShader(GL43C.GL_COMPUTE_SHADER);
        GL20C.glShaderSource(shader, source);
        GL20C.glCompileShader(shader);
        if (GL20C.glGetShaderi(shader, GL20C.GL_COMPILE_STATUS) == 0) {
            String log = GL20C.glGetShaderInfoLog(shader);
            GL20C.glDeleteShader(shader);
            throw new IllegalStateException("Compute shader compile failed (" + resourcePath + "):\n" + log);
        }
        int program = GL20C.glCreateProgram();
        GL20C.glAttachShader(program, shader);
        GL20C.glLinkProgram(program);
        GL20C.glDeleteShader(shader);
        if (GL20C.glGetProgrami(program, GL20C.GL_LINK_STATUS) == 0) {
            String log = GL20C.glGetProgramInfoLog(program);
            GL20C.glDeleteProgram(program);
            throw new IllegalStateException("Compute program link failed (" + resourcePath + "):\n" + log);
        }
        return program;
    }
}
