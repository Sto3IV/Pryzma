package net.pryzma.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.extensions.BlockStateModelPartExtension;
import net.neoforged.neoforge.client.model.DelegateBlockStateModel;

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
        Map<BlockState, BlockStateModel> models = event.getBakingResult().blockStateModels();
        RandomSource random = RandomSource.create();
        for (Block block : BuiltInRegistries.BLOCK) {
            if (!(block instanceof LeavesBlock)) {
                continue;
            }
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                BlockStateModel model = models.get(state);
                if (model != null && isPlainCube(model, state, random)) {
                    models.put(state, new DoubleSided(model));
                    CUBES.add(state);
                }
            }
        }
    }

    private static boolean isPlainCube(BlockStateModel model, BlockState state, RandomSource random) {
        List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(null, BlockPos.ZERO, state, random, parts);
        if (parts.isEmpty()) {
            return false;
        }
        for (BlockStateModelPart part : parts) {
            if (!part.getQuads(null).isEmpty()) {
                return false;
            }
            for (Direction side : Direction.values()) {
                if (part.getQuads(side).size() != 1) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Adds a back-facing copy of each face quad while Smart leaves are on. */
    static final class DoubleSided extends DelegateBlockStateModel {
        DoubleSided(BlockStateModel original) {
            super(original);
        }

        @Override
        public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockStateModelPart> parts) {
            List<BlockStateModelPart> rawParts = new ArrayList<>();
            this.delegate.collectParts(level, pos, state, random, rawParts);
            if (!PrRenderHooks.isTreesSmart()) {
                parts.addAll(rawParts);
                return;
            }
            for (BlockStateModelPart part : rawParts) {
                parts.add(new DoubleSidedPart(part));
            }
        }
    }

    private static final class DoubleSidedPart implements BlockStateModelPart, BlockStateModelPartExtension {
        private final BlockStateModelPart delegate;
        private final Map<BakedQuad, BakedQuad> reversed = new ConcurrentHashMap<>();

        DoubleSidedPart(BlockStateModelPart delegate) {
            this.delegate = delegate;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable Direction side) {
            List<BakedQuad> quads = delegate.getQuads(side);
            if (side == null || quads.isEmpty() || !PrRenderHooks.isTreesSmart()) {
                return quads;
            }
            List<BakedQuad> out = new ArrayList<>(quads.size() * 2);
            for (BakedQuad quad : quads) {
                out.add(quad);
                out.add(reversed.computeIfAbsent(quad, DoubleSidedPart::reverse));
            }
            return out;
        }

        @Override
        public Material.@Nullable Baked particleMaterial() {
            return delegate.particleMaterial();
        }

        @Override
        public int materialFlags() {
            return delegate.materialFlags();
        }

        @Override
        public boolean useAmbientOcclusion() {
            return delegate.useAmbientOcclusion();
        }

        private static BakedQuad reverse(BakedQuad quad) {
            return new BakedQuad(
                quad.position3(), quad.position2(), quad.position1(), quad.position0(),
                quad.packedUV3(), quad.packedUV2(), quad.packedUV1(), quad.packedUV0(),
                quad.direction() != null ? quad.direction().getOpposite() : null,
                quad.materialInfo(),
                quad.bakedNormals(),
                quad.bakedColors()
            );
        }
    }
}
