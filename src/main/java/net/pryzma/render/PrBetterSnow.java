package net.pryzma.render;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RedstoneWireBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;

/**
 * Universal Better Snow engine for Vanilla and modded blocks.
 * Dynamically synthesizes a ground snow layer under plants, flowers, fences, walls, stairs, and slabs
 * whenever bordered by snow blocks or layers.
 */
public final class PrBetterSnow {
    private static final ThreadLocal<Boolean> RENDERING_SNOW = ThreadLocal.withInitial(() -> false);
    private static volatile BlockState snowLayerState;
    private static volatile BlockStateModel snowModel;

    private PrBetterSnow() {
    }

    private static BlockState getSnowLayerState() {
        BlockState state = snowLayerState;
        if (state == null) {
            state = Blocks.SNOW.defaultBlockState();
            snowLayerState = state;
        }
        return state;
    }

    public static boolean isRenderingSnow() {
        return RENDERING_SNOW.get();
    }

    public static void clearCaches() {
        snowModel = null;
        snowLayerState = null;
    }

    /**
     * Determines whether {@code state} can receive Better Snow underneath/around it.
     */
    public static boolean isSnowEligible(BlockState state, BlockAndTintGetter level, BlockPos pos) {
        if (state.isAir()) {
            return false;
        }
        if (state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK)) {
            return false;
        }

        // Fences, walls, glass panes, iron bars
        if (state.getBlock() instanceof FenceBlock
                || state.getBlock() instanceof WallBlock
                || state.getBlock() instanceof IronBarsBlock
                || state.getBlock() instanceof CrossCollisionBlock
                || state.getBlock() instanceof FenceGateBlock) {
            return true;
        }

        // Stairs & Slabs (must be bottom half)
        if (state.getBlock() instanceof StairBlock) {
            return state.hasProperty(BlockStateProperties.HALF) && state.getValue(BlockStateProperties.HALF) == Half.BOTTOM;
        }
        if (state.getBlock() instanceof SlabBlock) {
            return state.hasProperty(BlockStateProperties.SLAB_TYPE) && state.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.BOTTOM;
        }

        // Plants, flowers, saplings, crops
        if (state.getBlock() instanceof BushBlock || state.getBlock() instanceof SugarCaneBlock) {
            if (state.getBlock() instanceof DoublePlantBlock) {
                return state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                        && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER;
            }
            return true;
        }

        // Floor attachments: levers, buttons, redstone wire, lanterns, flower pots
        if (state.getBlock() instanceof RedstoneWireBlock || state.getBlock() instanceof FlowerPotBlock) {
            return true;
        }
        if (state.getBlock() instanceof LeverBlock || state.getBlock() instanceof ButtonBlock) {
            return state.hasProperty(BlockStateProperties.ATTACH_FACE) && state.getValue(BlockStateProperties.ATTACH_FACE) == AttachFace.FLOOR;
        }
        if (state.getBlock() instanceof DiodeBlock) {
            return true;
        }
        if (state.getBlock() instanceof LanternBlock) {
            return state.hasProperty(BlockStateProperties.HANGING) && !state.getValue(BlockStateProperties.HANGING);
        }

        // Tag fallback
        return state.is(BlockTags.FLOWERS) || state.is(BlockTags.CROPS) || state.is(BlockTags.SAPLINGS);
    }

    /**
     * Checks whether at least one horizontal neighbor is a snow block or layer.
     */
    public static boolean hasSnowNeighbours(BlockAndTintGetter level, BlockPos pos) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos neighborPos = pos.relative(dir);
            BlockState neighbor = level.getBlockState(neighborPos);
            if (neighbor.is(Blocks.SNOW) || neighbor.is(Blocks.SNOW_BLOCK)) {
                return true;
            }
            if (neighbor.hasProperty(BlockStateProperties.SNOWY) && neighbor.getValue(BlockStateProperties.SNOWY)) {
                return true;
            }
        }
        return false;
    }

    public static boolean shouldRender(BlockAndTintGetter level, BlockState state, BlockPos pos) {
        return isSnowEligible(state, level, pos) && hasSnowNeighbours(level, pos);
    }

    /**
     * Performs auxiliary rendering of a 1-layer snow model at {@code pos},
     * negating the host block's offset so the snow layer sits flush on the grid.
     */
    public static void renderSnow(
            ModelBlockRenderer renderer,
            BlockQuadOutput output,
            float x,
            float y,
            float z,
            BlockAndTintGetter level,
            BlockState hostState,
            BlockPos pos,
            long seed
    ) {
        RENDERING_SNOW.set(true);
        try {
            BlockState snowState = getSnowLayerState();
            BlockStateModel model = snowModel;
            if (model == null) {
                model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(snowState);
                snowModel = model;
            }
            if (model != null) {
                Vec3 offset = hostState.getOffset(pos);
                float ox = (float) (x - offset.x);
                float oy = (float) (y - offset.y);
                float oz = (float) (z - offset.z);
                renderer.tesselateBlock(output, ox, oy, oz, level, pos, snowState, model, seed);
            }
        } finally {
            RENDERING_SNOW.set(false);
        }
    }
}
