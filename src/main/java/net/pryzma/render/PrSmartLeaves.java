package net.pryzma.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Smart leaves (Trees: Smart): leaves whose model is a plain cube hide the faces they share with
 * the same leaves, and draw every remaining face on both sides so the canopy does not look
 * hollow from outside. Other leaves models are left alone, as in 1.x.
 */
public final class PrSmartLeaves {
    private static final Set<BlockState> CUBES = ConcurrentHashMap.newKeySet();

    private PrSmartLeaves() {
    }

    /** Whether {@code state} hides its face towards {@code adjacent} under Smart leaves. */
    public static boolean culls(BlockState state, BlockState adjacent) {
        return PrRenderHooks.isTreesSmart() && adjacent.getBlock() == state.getBlock() && CUBES.contains(state);
    }

    /** Wraps every plain-cube leaves model; runs after the CTM wrapping, so it wraps that too. */
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        CUBES.clear();
        Map<ModelResourceLocation, BakedModel> models = event.getModels();
        RandomSource random = RandomSource.create();
        for (Block block : BuiltInRegistries.BLOCK) {
            if (!(block instanceof LeavesBlock)) {
                continue;
            }
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                ModelResourceLocation mrl = BlockModelShaper.stateToModelLocation(id, state);
                BakedModel model = models.get(mrl);
                if (model != null && isPlainCube(model, state, random)) {
                    models.put(mrl, new DoubleSided(model));
                    CUBES.add(state);
                }
            }
        }
    }

    private static boolean isPlainCube(BakedModel model, BlockState state, RandomSource random) {
        random.setSeed(42L);
        if (!model.getQuads(state, null, random, ModelData.EMPTY, null).isEmpty()) {
            return false;
        }
        for (Direction side : Direction.values()) {
            random.setSeed(42L);
            if (model.getQuads(state, side, random, ModelData.EMPTY, null).size() != 1) {
                return false;
            }
        }
        return true;
    }

    /** Adds a back-facing copy of each face quad while Smart leaves are on. */
    static final class DoubleSided extends BakedModelWrapper<BakedModel> {
        private final Map<BakedQuad, BakedQuad> reversed = new ConcurrentHashMap<>();

        DoubleSided(BakedModel original) {
            super(original);
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData data,
                @Nullable RenderType renderType) {
            List<BakedQuad> quads = originalModel.getQuads(state, side, rand, data, renderType);
            if (side == null || quads.isEmpty() || !PrRenderHooks.isTreesSmart()) {
                return quads;
            }
            List<BakedQuad> out = new ArrayList<>(quads.size() * 2);
            for (BakedQuad quad : quads) {
                out.add(quad);
                out.add(reversed.computeIfAbsent(quad, DoubleSided::reverse));
            }
            return out;
        }

        /** Same quad with its vertex order reversed, so it faces the other way. */
        private static BakedQuad reverse(BakedQuad quad) {
            int[] data = quad.getVertices();
            int stride = data.length / 4;
            int[] out = new int[data.length];
            for (int v = 0; v < 4; v++) {
                System.arraycopy(data, v * stride, out, (3 - v) * stride, stride);
            }
            return new BakedQuad(out, quad.getTintIndex(), quad.getDirection(), quad.getSprite(), quad.isShade(),
                    quad.hasAmbientOcclusion());
        }
    }
}
