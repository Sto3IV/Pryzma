package net.pryzma.ctm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import net.neoforged.neoforge.common.util.TriState;

/**
 * Wraps a block model that a CTM rule can affect. During chunk building {@link #getModelData}
 * captures the block's position; {@link #getQuads} then swaps tile sprites and adds overlay quads.
 * The wrapped model always receives its own, untouched model data, so models that cache on it keep
 * working. Anywhere without a chunk context (items, falling blocks, breaking overlay) the wrapped
 * model is returned unchanged.
 */
public final class PrCtmModel extends BakedModelWrapper<BakedModel> {
    private static final ModelProperty<ModelData> INNER = new ModelProperty<>();
    private static final ModelProperty<PrCtmContext> CONTEXT = new ModelProperty<>();

    public PrCtmModel(BakedModel original) {
        super(original);
    }

    private static ModelData inner(ModelData data) {
        ModelData inner = data.get(INNER);
        return inner != null ? inner : data;
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
        ModelData inner = originalModel.getModelData(level, pos, state, data);
        if (!PryzmaCtm.enabled()) {
            return inner;
        }
        return ModelData.builder()
                .with(INNER, inner)
                .with(CONTEXT, new PrCtmContext(level, pos.immutable(), state))
                .build();
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        ChunkRenderTypeSet base = originalModel.getRenderTypes(state, rand, inner(data));
        PrCtmContext ctx = data.get(CONTEXT);
        if (ctx == null) {
            return base;
        }
        ctx.baseTypes = base;
        ChunkRenderTypeSet overlays = PryzmaCtm.overlayLayers(state);
        return overlays.isEmpty() ? base : ChunkRenderTypeSet.union(base, overlays);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData data,
            @Nullable RenderType renderType) {
        ModelData inner = inner(data);
        PrCtmContext ctx = data.get(CONTEXT);
        if (ctx == null || state == null || !PryzmaCtm.enabled()) {
            return originalModel.getQuads(state, side, rand, inner, renderType);
        }
        int slot = side == null ? 6 : side.get3DDataValue();
        if (!ctx.evaluated[slot]) {
            ctx.evaluated[slot] = true;
            // The chunk renderer fetches quads before it culls, so a hidden face is left alone.
            if (side == null || PryzmaCtm.faceVisible(ctx, side)) {
                // Its own random source: the caller's one must reach the render fetch untouched.
                for (BakedQuad quad : originalModel.getQuads(state, side, PryzmaCtm.modelRandom(ctx), inner, null)) {
                    BakedQuad[] replaced = PryzmaCtm.evaluate(ctx, quad, slot);
                    if (replaced != null && (replaced.length != 1 || replaced[0] != quad)) {
                        ctx.results.put(quad, replaced);
                    }
                }
            }
        }
        boolean baseLayer = renderType == null || ctx.baseTypes == null || ctx.baseTypes.contains(renderType);
        List<BakedQuad> out = null;
        if (baseLayer) {
            // Multipart models return NeoForge's ConcatenatedListView: iteration and toArray only.
            List<BakedQuad> quads = originalModel.getQuads(state, side, rand, inner, renderType);
            if (anyReplaced(quads, ctx)) {
                out = new ArrayList<>(quads.size() + 4);
                for (BakedQuad quad : quads) {
                    BakedQuad[] replaced = ctx.results.get(quad);
                    if (replaced == null) {
                        out.add(quad);
                    } else {
                        Collections.addAll(out, replaced);
                    }
                }
            } else if (renderType == null || ctx.overlays[slot] == null) {
                return quads;
            } else {
                out = new ArrayList<>(quads);
            }
        }
        if (renderType != null && ctx.overlays[slot] != null) {
            List<BakedQuad> overlays = ctx.overlays(slot, renderType);
            if (!overlays.isEmpty()) {
                if (out == null) {
                    out = new ArrayList<>(overlays.size());
                }
                out.addAll(overlays);
            }
        }
        return out == null ? List.of() : out;
    }

    private static boolean anyReplaced(List<BakedQuad> quads, PrCtmContext ctx) {
        if (ctx.results.isEmpty()) {
            return false;
        }
        for (BakedQuad quad : quads) {
            if (ctx.results.containsKey(quad)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public TextureAtlasSprite getParticleIcon(ModelData data) {
        return originalModel.getParticleIcon(inner(data));
    }

    @Override
    public TriState useAmbientOcclusion(BlockState state, ModelData data, RenderType renderType) {
        return originalModel.useAmbientOcclusion(state, inner(data), renderType);
    }
}
