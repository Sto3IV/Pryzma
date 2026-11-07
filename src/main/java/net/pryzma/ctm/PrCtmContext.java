package net.pryzma.ctm;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Per-block state of one chunk build: where the block is, and the CTM results already computed
 * for it, so all parts see the same decision. Confined to one build thread.
 */
final class PrCtmContext {
    final BlockAndTintGetter level;
    final BlockPos pos;
    final BlockState state;

    /** Original quad to its replacement; absent means "unchanged". Filled per evaluated side. */
    final Map<BakedQuad, BakedQuad[]> results = new IdentityHashMap<>();
    /** Overlay quads per side (index 6 = no cull face). */
    @SuppressWarnings("unchecked")
    final List<BakedQuad>[] overlays = new List[7];
    final boolean[] evaluated = new boolean[7];

    PrCtmContext(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        this.level = level;
        this.pos = pos;
        this.state = state;
    }

    void addOverlay(int sideSlot, BakedQuad quad) {
        List<BakedQuad> list = overlays[sideSlot];
        if (list == null) {
            list = new ArrayList<>(4);
            overlays[sideSlot] = list;
        }
        list.add(quad);
    }

    List<BakedQuad> overlays(int sideSlot) {
        List<BakedQuad> list = overlays[sideSlot];
        return list == null ? List.of() : list;
    }
}
