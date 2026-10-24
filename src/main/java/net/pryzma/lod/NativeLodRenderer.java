package net.pryzma.lod;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import org.jetbrains.annotations.Nullable;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.pryzma.Pryzma;

/**
 * Farlands LOD meshes on the GPU: one VAO and VBO per macro-sector, each holding the sector's opaque quads followed
 * by its water quads ({@link LodMesh}). The vertex layout is the one shader packs' Distant Horizons programs read, so
 * the same VAO is drawn by the native pass here and by a shader pack's G-buffer pass ({@link LodShaderSupport}).
 *
 * <h2>Native pass</h2>
 * Drawn with the {@code pryzma:lod_terrain} core shader right after the sky and before vanilla terrain: the opaque
 * quads, then the water quads blended over them. The pass uses the game's projection with its depth range moved to
 * cover the LOD ({@link LodMath#withDepthRange}): the near plane sits just inside the vanilla radius, the far plane
 * beyond the farthest sector. LOD faces depth-test against each other with {@code GL_LEQUAL}. Afterwards the depth
 * buffer is cleared, so vanilla terrain, entities and everything after draw over the LOD exactly as they would over
 * the sky: vanilla and LOD depths never meet in one test and cannot z-fight. Inside the vanilla radius the shader
 * discards LOD fragments, so LOD terrain never shows through gaps in vanilla geometry there.
 *
 * <p>Everything here runs on the render thread.
 */
public final class NativeLodRenderer {
    public static final ResourceLocation SHADER = ResourceLocation.fromNamespaceAndPath(Pryzma.MODID, "lod_terrain");
    /** Water surfaces sit this far below their block top, as vanilla's still water does (14/16 of the block). */
    public static final float WATER_DROP = 0.125F;

    private static final Comparator<SectorMesh> NEAR_FIRST = Comparator.comparingDouble(mesh -> mesh.distance);
    private static final Long2ObjectOpenHashMap<SectorMesh> MESHES = new Long2ObjectOpenHashMap<>();
    private static final List<SectorMesh> VISIBLE = new ArrayList<>();
    private static final Matrix4f PROJECTION = new Matrix4f();
    private static final Matrix4f VIEW_PROJECTION = new Matrix4f();
    private static final FrustumIntersection FRUSTUM = new FrustumIntersection();

    @Nullable
    private static ShaderInstance shader;
    private static boolean shaderFailed;
    private static ByteBuffer staging;
    private static long gpuBytes;
    private static long quadTotal;
    private static int drawnLastFrame;
    private static boolean warnedProjection;

    private NativeLodRenderer() {
    }

    /** The GPU copy of one sector's mesh, with the state it was built from. */
    public static final class SectorMesh {
        final long key;
        final int sectorX;
        final int sectorZ;
        int vao = -1;
        int vbo = -1;
        int baseVertex = -1;
        int allocatedVertices;
        int tableIndex = -1;
        int opaqueQuads;
        int waterQuads;
        int minY;
        int maxY;
        long bytes;
        /** Detail level, store version and palette state the mesh was built with. */
        int level;
        int version;
        int colorGeneration;
        int colorEpoch;
        boolean fallbackColors;
        long builtTick;
        double distance;

        SectorMesh(long key) {
            this.key = key;
            this.sectorX = LodDataStore.keyX(key);
            this.sectorZ = LodDataStore.keyZ(key);
        }

        /** World x of the sector's minimum corner. */
        public double originX() {
            return (double) sectorX * LodDataStore.SECTOR_BLOCKS;
        }

        /** World z of the sector's minimum corner. */
        public double originZ() {
            return (double) sectorZ * LodDataStore.SECTOR_BLOCKS;
        }

        public int blockOriginX() {
            return sectorX * LodDataStore.SECTOR_BLOCKS;
        }

        public int blockOriginZ() {
            return sectorZ * LodDataStore.SECTOR_BLOCKS;
        }

        public int opaqueQuads() {
            return opaqueQuads;
        }

        public int waterQuads() {
            return waterQuads;
        }

        int quadCount() {
            return opaqueQuads + waterQuads;
        }

        private void upload(LodMesh mesh) {
            account(-1);
            opaqueQuads = mesh.opaqueQuads();
            waterQuads = mesh.waterQuads();
            minY = mesh.minY();
            maxY = mesh.maxY();
            bytes = mesh.byteSize();
            account(1);
            if (FarlandsGpuEngine.isSupported()) {
                FarlandsGpuEngine.uploadSector(key, this, mesh);
            } else {
                ByteBuffer data = stage(mesh.vertices());
                if (vao < 0) {
                    vao = GlStateManager._glGenVertexArrays();
                    vbo = GlStateManager._glGenBuffers();
                }
                BufferUploader.invalidate();
                GlStateManager._glBindVertexArray(vao);
                GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
                GlStateManager._glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_STATIC_DRAW);
                LodVertexFormat.FORMAT.setupBufferState();
                GlStateManager._glBindVertexArray(0);
                GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            }
        }

        private void release() {
            if (FarlandsGpuEngine.isSupported()) {
                FarlandsGpuEngine.removeSector(key);
            }
            if (vao >= 0) {
                GlStateManager._glDeleteVertexArrays(vao);
                vao = -1;
            }
            if (vbo >= 0) {
                GlStateManager._glDeleteBuffers(vbo);
                vbo = -1;
            }
            baseVertex = -1;
            allocatedVertices = 0;
            tableIndex = -1;
            account(-1);
            opaqueQuads = 0;
            waterQuads = 0;
            bytes = 0;
        }

        private void account(int sign) {
            gpuBytes += sign * bytes;
            quadTotal += sign * (long) quadCount();
        }
    }

    // ------------------------------------------------------------------ shader

    /**
     * The LOD's core shader, created from the current resources on its first use: with the option off it is never
     * compiled, and nothing is added to the game's shader reload. Null when it failed to load (logged once per load).
     */
    @Nullable
    private static ShaderInstance shader() {
        if (shader == null && !shaderFailed) {
            try {
                shader = new ShaderInstance(Minecraft.getInstance().getResourceManager(), SHADER, LodVertexFormat.FORMAT);
            } catch (IOException | RuntimeException e) {
                shaderFailed = true;
                Pryzma.LOGGER.error("Farlands LOD shader {} failed to load; distant terrain is not drawn", SHADER, e);
            }
        }
        return shader;
    }

    /** Resources changed: the shader is built again from them when next used. Render thread. */
    static void onResourcesReloaded() {
        releaseShader();
        FarlandsGpuEngine.onResourcesReloaded();
    }

    private static void releaseShader() {
        if (shader != null) {
            shader.close();
            shader = null;
        }
        shaderFailed = false;
    }

    // ------------------------------------------------------------------ meshes

    @Nullable
    static SectorMesh get(long key) {
        return MESHES.get(key);
    }

    /**
     * Installs a finished mesh for sector {@code key}, replacing the previous one. An empty or failed mesh
     * ({@code mesh == null}) keeps only the build state, so the sector is not scheduled again until it changes.
     */
    static void accept(long key, int level, int version, int colorGeneration, int colorEpoch, boolean fallbackColors,
            @Nullable LodMesh mesh, long tick) {
        SectorMesh target = MESHES.get(key);
        if (target == null) {
            target = new SectorMesh(key);
            MESHES.put(key, target);
        }
        target.level = level;
        target.version = version;
        target.colorGeneration = colorGeneration;
        target.colorEpoch = colorEpoch;
        target.fallbackColors = fallbackColors;
        target.builtTick = tick;
        if (mesh == null || mesh.isEmpty()) {
            target.release();
        } else {
            target.upload(mesh);
        }
    }

    static void remove(long key) {
        SectorMesh mesh = MESHES.remove(key);
        if (mesh != null) {
            mesh.release();
        }
    }

    /** Deletes the meshes of sectors farther than {@code radius} blocks from the camera. */
    static void retainWithin(double cameraX, double cameraZ, double radius) {
        Iterator<SectorMesh> it = MESHES.values().iterator();
        while (it.hasNext()) {
            SectorMesh mesh = it.next();
            if (LodMath.sectorDistance(cameraX, cameraZ, mesh.sectorX, mesh.sectorZ) > radius) {
                mesh.release();
                it.remove();
            }
        }
    }

    /** Deletes every mesh, the staging buffer and the shader: a stopped LOD holds no GPU resources. */
    static void clear() {
        FarlandsGpuEngine.clear();
        for (SectorMesh mesh : MESHES.values()) {
            mesh.release();
        }
        MESHES.clear();
        VISIBLE.clear();
        gpuBytes = 0;
        quadTotal = 0;
        drawnLastFrame = 0;
        staging = null;
        releaseShader();
    }

    static int meshCount() {
        return MESHES.size();
    }

    static it.unimi.dsi.fastutil.longs.LongSet meshKeys() {
        return MESHES.keySet();
    }

    static long quadCount() {
        return quadTotal;
    }

    static long gpuBytes() {
        return gpuBytes;
    }

    static int drawnLastFrame() {
        return drawnLastFrame;
    }

    /** Copies heap vertex data into the direct buffer GL uploads from; the buffer grows and is reused. */
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

    // ------------------------------------------------------------------ culling and drawing, shared by both passes

    /**
     * Collects the meshes worth drawing this frame into a list sorted nearest first: those with quads, outside the
     * vanilla radius, inside the frustum of {@code projection * modelView}.
     *
     * @return the visible meshes (a reused list, valid until the next call), empty when nothing lies beyond the clip
     *         radius; {@link #beyondClipLastCollect()} tells whether anything lay beyond it, in view or not
     */
    static List<SectorMesh> collectVisible(Matrix4fc modelView, Matrix4fc projection, Vec3 camera, float clipRadius) {
        VIEW_PROJECTION.set(projection).mul(modelView);
        FRUSTUM.set(VIEW_PROJECTION);
        double cameraX = camera.x;
        double cameraY = camera.y;
        double cameraZ = camera.z;
        beyondClip = false;
        VISIBLE.clear();
        for (SectorMesh mesh : MESHES.values()) {
            if (mesh.quadCount() == 0 || LodMath.insideClip(cameraX, cameraZ, mesh.sectorX, mesh.sectorZ, clipRadius)) {
                continue;
            }
            beyondClip = true;
            float minX = (float) (mesh.originX() - cameraX);
            float minZ = (float) (mesh.originZ() - cameraZ);
            if (!FRUSTUM.testAab(minX, (float) (mesh.minY - cameraY), minZ,
                    minX + LodDataStore.SECTOR_BLOCKS, (float) (mesh.maxY - cameraY), minZ + LodDataStore.SECTOR_BLOCKS)) {
                continue;
            }
            mesh.distance = LodMath.sectorDistance(cameraX, cameraZ, mesh.sectorX, mesh.sectorZ);
            VISIBLE.add(mesh);
        }
        VISIBLE.sort(NEAR_FIRST);
        drawnLastFrame = VISIBLE.size();
        return VISIBLE;
    }

    private static boolean beyondClip;

    /**
     * Farthest point of the LOD terrain that is in view this frame, in blocks from the camera: the far corner of the
     * farthest sector that passes the same frustum test the draw uses. This is what the fog has to close at; it can be
     * well inside the configured LOD radius, because the intake only reads chunks the world has saved.
     *
     * @return 0 when nothing would be drawn
     */
    static float horizon(Matrix4fc modelView, Matrix4fc projection, Vec3 camera, float clipRadius) {
        VIEW_PROJECTION.set(projection).mul(modelView);
        FRUSTUM.set(VIEW_PROJECTION);
        float horizon = 0.0F;
        for (SectorMesh mesh : MESHES.values()) {
            if (mesh.quadCount() == 0 || LodMath.insideClip(camera.x, camera.z, mesh.sectorX, mesh.sectorZ, clipRadius)) {
                continue;
            }
            float minX = (float) (mesh.originX() - camera.x);
            float minZ = (float) (mesh.originZ() - camera.z);
            if (!FRUSTUM.testAab(minX, (float) (mesh.minY - camera.y), minZ,
                    minX + LodDataStore.SECTOR_BLOCKS, (float) (mesh.maxY - camera.y), minZ + LodDataStore.SECTOR_BLOCKS)) {
                continue;
            }
            horizon = Math.max(horizon, (float) LodMath.sectorFarthest(camera.x, camera.z, mesh.sectorX, mesh.sectorZ));
        }
        return horizon;
    }

    /** Whether the last {@link #collectVisible} found LOD terrain beyond the clip radius, in view or not. */
    static boolean beyondClipLastCollect() {
        return beyondClip;
    }

    /** Whether any mesh exists at all. */
    static boolean hasMeshes() {
        return quadTotal > 0;
    }

    /**
     * Draws the opaque or the water quads of {@code mesh} with the bound program, using the shared quad index buffer.
     * The index buffer binding is VAO state; it is bound with the mesh's VAO.
     */
    static void draw(SectorMesh mesh, boolean water, RenderSystem.AutoStorageIndexBuffer indices) {
        int first = water ? mesh.opaqueQuads : 0;
        int quads = water ? mesh.waterQuads : mesh.opaqueQuads;
        if (quads == 0) {
            return;
        }
        if (FarlandsGpuEngine.isSupported() && mesh.baseVertex >= 0) {
            FarlandsGpuEngine.drawMesh(mesh, water);
            return;
        }
        if (mesh.vao >= 0) {
            GlStateManager._glBindVertexArray(mesh.vao);
            indices.bind((first + quads) * 6);
            VertexFormat.IndexType type = indices.type();
            GlStateManager._drawElements(GL11.GL_TRIANGLES, quads * 6, type.asGLType, (long) first * 6 * type.bytes);
        }
    }

    // ------------------------------------------------------------------ native pass

    /**
     * Draws the visible sectors, opaque then water, and clears the depth buffer behind them.
     *
     * @param modelView  the camera rotation, as the level renderer passes it
     * @param projection the game's projection for this frame; not modified
     * @param clipRadius radius around the camera left to vanilla terrain ({@link LodMath#clipRadius})
     * @return whether LOD terrain exists beyond the vanilla radius, in view or not, i.e. whether the LOD now owns
     *         the horizon and the terrain fog may move out to it
     */
    static boolean render(Matrix4f modelView, Matrix4f projection, Vec3 camera, float clipRadius, int lodRadiusBlocks,
            float fogStart, float fogEnd) {
        drawnLastFrame = 0;
        if (quadTotal == 0) {
            return false;
        }
        ShaderInstance program = shader();
        if (program == null) {
            return false;
        }
        if (!LodMath.withDepthRange(projection, LodMath.nearPlane(clipRadius), LodMath.farPlane(lodRadiusBlocks), PROJECTION)
                && !warnedProjection) {
            warnedProjection = true;
            Pryzma.LOGGER.warn("Farlands LOD: the projection is not a perspective one; the LOD keeps the game's depth range");
        }

        if (FarlandsGpuEngine.isSupported()) {
            drawnLastFrame = FarlandsGpuEngine.render(program, modelView, PROJECTION, camera, clipRadius,
                    lodRadiusBlocks, fogStart, fogEnd);
            return drawnLastFrame > 0;
        }

        List<SectorMesh> visible = collectVisible(modelView, PROJECTION, camera, clipRadius);
        if (visible.isEmpty()) {
            return beyondClip;
        }

        Minecraft mc = Minecraft.getInstance();
        LightTexture lightTexture = mc.gameRenderer.lightTexture();
        lightTexture.turnOnLightLayer();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        program.setDefaultUniforms(VertexFormat.Mode.QUADS, modelView, PROJECTION, mc.getWindow());
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
        Uniform inst = program.getUniform("UseInstancedOffset");
        if (inst != null) {
            inst.set(0);
        }
        program.apply();

        Uniform offset = program.CHUNK_OFFSET;
        Uniform origin = program.getUniform("SectorOrigin");
        RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        BufferUploader.invalidate();
        boolean anyWater = false;
        for (SectorMesh mesh : visible) {
            setSector(offset, origin, mesh, camera, 0.0F);
            draw(mesh, false, indices);
            anyWater |= mesh.waterQuads > 0;
        }
        if (anyWater) {
            // Water over the opaque LOD, nearest sectors last would be ideal; sectors rarely overlap on screen.
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            RenderSystem.depthMask(false);
            for (int i = visible.size() - 1; i >= 0; i--) {
                SectorMesh mesh = visible.get(i);
                if (mesh.waterQuads > 0) {
                    setSector(offset, origin, mesh, camera, WATER_DROP);
                    draw(mesh, true, indices);
                }
            }
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
        }
        if (offset != null) {
            offset.set(0.0F, 0.0F, 0.0F);
        }
        program.clear();
        VertexBuffer.unbind();
        lightTexture.turnOffLightLayer();

        // Vanilla depths start from scratch: nothing after this point can test against an LOD depth.
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        return true;
    }

    /** Points the vanilla program at {@code mesh}: camera-relative offset (y bias removed) and the sector origin. */
    private static void setSector(@Nullable Uniform offset, @Nullable Uniform origin, SectorMesh mesh, Vec3 camera, float drop) {
        if (offset != null) {
            offset.set((float) (mesh.originX() - camera.x), (float) (-NativeLodMesher.Y_BIAS - drop - camera.y),
                    (float) (mesh.originZ() - camera.z));
            offset.upload();
        }
        if (origin != null) {
            origin.set(mesh.blockOriginX(), mesh.blockOriginZ());
            origin.upload();
        }
    }
}
