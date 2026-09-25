package net.pryzma.ctm;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;

/**
 * Per-block state of one chunk build: where the block is, and the CTM results already computed
 * for it, so every render layer pass sees the same decision. Confined to one build thread.
 */
final class PrCtmContext {
    final BlockAndTintGetter level;
    final BlockPos pos;
    final BlockState state;
    ChunkRenderTypeSet baseTypes;

    /** Original quad to its replacement; absent means "unchanged". Filled per evaluated side. */
    final Map<BakedQuad, BakedQuad[]> results = new IdentityHashMap<>();
    /** Overlay quads per side (index 6 = no cull face), then per layer. */
    @SuppressWarnings("unchecked")
    final Map<RenderType, List<BakedQuad>>[] overlays = new Map[7];
    final boolean[] evaluated = new boolean[7];

    PrCtmContext(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        this.level = level;
        this.pos = pos;
        this.state = state;
    }

    void addOverlay(int sideSlot, RenderType layer, BakedQuad quad) {
        Map<RenderType, List<BakedQuad>> byLayer = overlays[sideSlot];
        if (byLayer == null) {
            byLayer = new IdentityHashMap<>(2);
            overlays[sideSlot] = byLayer;
        }
        byLayer.computeIfAbsent(layer, l -> new ArrayList<>(4)).add(quad);
    }

    List<BakedQuad> overlays(int sideSlot, RenderType layer) {
        Map<RenderType, List<BakedQuad>> byLayer = overlays[sideSlot];
        if (byLayer == null) {
            return List.of();
        }
        List<BakedQuad> quads = byLayer.get(layer);
        return quads == null ? List.of() : quads;
    }
}
