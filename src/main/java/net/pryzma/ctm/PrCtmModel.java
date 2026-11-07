package net.pryzma.ctm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.extensions.BlockStateModelPartExtension;
import net.neoforged.neoforge.client.model.DelegateBlockStateModel;

/**
 * Wraps a block model that a CTM rule can affect. During chunk building {@link #collectParts}
 * captures the block context; the returned {@link PrCtmPart} wrappers evaluate CTM replacements
 * and add overlay quads per face.
 */
public final class PrCtmModel extends DelegateBlockStateModel {
    public PrCtmModel(BlockStateModel original) {
        super(original);
    }

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockStateModelPart> parts) {
        if (!PryzmaCtm.enabled()) {
            this.delegate.collectParts(level, pos, state, random, parts);
            return;
        }
        List<BlockStateModelPart> rawParts = new ArrayList<>();
        this.delegate.collectParts(level, pos, state, random, rawParts);
        if (rawParts.isEmpty()) {
            return;
        }
        PrCtmContext ctx = new PrCtmContext(level, pos.immutable(), state);
        for (int i = 0; i < rawParts.size(); i++) {
            parts.add(new PrCtmPart(rawParts.get(i), ctx, rawParts, i == 0));
        }
    }

    private static final class PrCtmPart implements BlockStateModelPart, BlockStateModelPartExtension {
        private final BlockStateModelPart delegate;
        private final PrCtmContext ctx;
        private final List<BlockStateModelPart> allParts;
        private final boolean isFirst;

        PrCtmPart(BlockStateModelPart delegate, PrCtmContext ctx, List<BlockStateModelPart> allParts, boolean isFirst) {
            this.delegate = delegate;
            this.ctx = ctx;
            this.allParts = allParts;
            this.isFirst = isFirst;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable Direction direction) {
            int slot = direction == null ? 6 : direction.get3DDataValue();
            if (!ctx.evaluated[slot]) {
                ctx.evaluated[slot] = true;
                if (direction == null || PryzmaCtm.faceVisible(ctx, direction)) {
                    for (BlockStateModelPart p : allParts) {
                        for (BakedQuad quad : p.getQuads(direction)) {
                            BakedQuad[] replaced = PryzmaCtm.evaluate(ctx, quad, slot);
                            if (replaced != null && (replaced.length != 1 || replaced[0] != quad)) {
                                ctx.results.put(quad, replaced);
                            }
                        }
                    }
                }
            }

            List<BakedQuad> quads = delegate.getQuads(direction);
            List<BakedQuad> overlays = isFirst ? ctx.overlays(slot) : List.of();
            boolean hasOverlays = !overlays.isEmpty();
            boolean hasReplaced = anyReplaced(quads, ctx);

            if (!hasReplaced && !hasOverlays) {
                return quads;
            }

            List<BakedQuad> out = new ArrayList<>(quads.size() + overlays.size() + 2);
            if (hasReplaced) {
                for (BakedQuad quad : quads) {
                    BakedQuad[] replaced = ctx.results.get(quad);
                    if (replaced == null) {
                        out.add(quad);
                    } else {
                        Collections.addAll(out, replaced);
                    }
                }
            } else {
                out.addAll(quads);
            }
            if (hasOverlays) {
                out.addAll(overlays);
            }
            return out;
        }

        private static boolean anyReplaced(List<BakedQuad> quads, PrCtmContext ctx) {
            for (BakedQuad quad : quads) {
                if (ctx.results.containsKey(quad)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean useAmbientOcclusion() {
            return delegate.useAmbientOcclusion();
        }

        @Override
        public Material.Baked particleMaterial() {
            return delegate.particleMaterial();
        }

        @Override
        public int materialFlags() {
            return delegate.materialFlags();
        }
    }
}
