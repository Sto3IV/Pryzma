package net.pryzma.render;

import java.util.ArrayList;
import java.util.Map;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.pryzma.Pryzma;
import net.pryzma.PryzmaConfig;

/**
 * Render Regions: the sections of an 8x8 chunk column share one {@link VboRegion} per unsorted
 * terrain layer (solid, cutout mipped, cutout), and each region draws its visible sections with a
 * single multi-draw. Vertices of those layers are stored relative to the region origin (chunk
 * workers translate them after meshing), so one ChunkOffset per region replaces the per-section
 * uniform. Translucent and tripwire keep vanilla's per-section draws: they need their sort order.
 *
 * <p>A region is created by the first upload into it and deleted when its last range is released.
 * Everything but {@link #toRegionSpace} runs on the render thread.
 */
public final class PrRenderRegionManager {
    public static final int REGION_SHIFT = 7;
    private static final int REGION_MASK = (1 << REGION_SHIFT) - 1;

    private static final RenderType[] LAYERS = RenderType.chunkBufferLayers().stream()
            .filter(PrRenderRegionManager::isRegionLayer).toArray(RenderType[]::new);
    @SuppressWarnings("unchecked")
    private static final Long2ObjectOpenHashMap<VboRegion>[] REGIONS = new Long2ObjectOpenHashMap[LAYERS.length];
    /** Regions with draws queued in the current layer pass, in the order they were first reached. */
    private static final ArrayList<VboRegion> PENDING = new ArrayList<>();

    /** Whether the sections of the current view area use regions; fixed when they are created. */
    private static volatile boolean active;

    static {
        for (int i = 0; i < REGIONS.length; i++) {
            REGIONS[i] = new Long2ObjectOpenHashMap<>();
        }
    }

    private PrRenderRegionManager() {
    }

    public static boolean isRegionLayer(RenderType layer) {
        return !layer.sortOnUpload();
    }

    /** ViewArea.createSections: the option applies to the sections about to be created, until the next rebuild. */
    public static void beginViewArea() {
        if (active != PryzmaConfig.prRenderRegions) {
            active = PryzmaConfig.prRenderRegions;
            Pryzma.LOGGER.info("Render regions {}", active ? "on (8x8 chunks)" : "off");
        }
    }

    /** RenderSection.setOrigin: the section's region-layer buffers follow it into the region at its new origin. */
    public static void assign(SectionRenderDispatcher.RenderSection section, int originX, int originZ) {
        if (!active) {
            return;
        }
        long key = ChunkPos.asLong(originX >> REGION_SHIFT, originZ >> REGION_SHIFT);
        for (int i = 0; i < LAYERS.length; i++) {
            ((IPrVertexBuffer) section.getBuffer(LAYERS[i])).pryzma$setRegionSlot(i, key);
        }
    }

    /** SectionCompiler.compile, on a chunk worker: region-layer vertices become relative to the region origin. */
    public static void toRegionSpace(SectionPos pos, Map<RenderType, MeshData> layers) {
        if (!active) {
            return;
        }
        float dx = pos.minBlockX() & REGION_MASK;
        float dy = pos.minBlockY();
        float dz = pos.minBlockZ() & REGION_MASK;
        for (Map.Entry<RenderType, MeshData> entry : layers.entrySet()) {
            if (isRegionLayer(entry.getKey())) {
                MeshData mesh = entry.getValue();
                MeshData.DrawState state = mesh.drawState();
                VertexFormat format = state.format();
                VboRegion.translate(mesh.vertexBuffer(), state.vertexCount(), format.getVertexSize(),
                        format.getOffset(VertexFormatElement.POSITION), dx, dy, dz);
            }
        }
    }

    /** The region of {@code key} for region layer {@code layer}, created on first use. */
    public static VboRegion acquire(int layer, long key) {
        VboRegion region = REGIONS[layer].get(key);
        if (region == null) {
            region = new VboRegion(LAYERS[layer], key);
            REGIONS[layer].put(key, region);
        }
        return region;
    }

    /** Frees a buffer's range; a region left without ranges is deleted. */
    public static void release(int layer, VboRegion region, VboRange range) {
        region.release(range);
        if (region.isEmpty() && !region.isDeleted()) {
            region.deleteGlBuffers();
            REGIONS[layer].remove(region.getKey(), region);
        }
    }

    /** VertexBuffer.draw during a layer pass: the range joins its region's multi-draw. */
    public static void queue(VboRegion region, VertexFormat.Mode mode, VboRange range) {
        if (!region.hasPendingDraws()) {
            PENDING.add(region);
        }
        region.drawArrays(mode, range);
    }

    /** End of renderSectionLayer's section loop: one ChunkOffset upload and one multi-draw per region. */
    public static void flush(ShaderInstance shader, double camX, double camY, double camZ) {
        if (PENDING.isEmpty()) {
            return;
        }
        Uniform offset = shader == null ? null : shader.CHUNK_OFFSET;
        for (int i = 0, n = PENDING.size(); i < n; i++) {
            VboRegion region = PENDING.get(i);
            if (offset != null) {
                long key = region.getKey();
                offset.set((float) ((ChunkPos.getX(key) << REGION_SHIFT) - camX), (float) -camY,
                        (float) ((ChunkPos.getZ(key) << REGION_SHIFT) - camZ));
                offset.upload();
            }
            region.finishDraw();
        }
        PENDING.clear();
        if (offset != null) {
            offset.set(0.0F, 0.0F, 0.0F);
        }
    }

    /** ViewArea.releaseAllBuffers: the sections have released their ranges; delete whatever is left. */
    public static void releaseAll() {
        for (Long2ObjectOpenHashMap<VboRegion> regions : REGIONS) {
            for (VboRegion region : regions.values()) {
                region.deleteGlBuffers();
            }
            regions.clear();
        }
        PENDING.clear();
    }
}
