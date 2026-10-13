package net.pryzma.render;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
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
import net.minecraft.world.level.block.RedStoneWireBlock;
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
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Universal Better Snow engine for Vanilla and modded blocks.
 * Dynamically synthesizes a ground snow layer under plants, flowers, fences, walls, stairs, and slabs
 * whenever bordered by snow blocks or layers.
 */
public final class PrBetterSnow {
    private static final ThreadLocal<Boolean> RENDERING_SNOW = ThreadLocal.withInitial(() -> false);
    private static volatile BlockState snowLayerState;
    private static volatile BakedModel snowModel;

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
     * Checks whether {@code state} can receive an auxiliary snow layer beneath it.
     */
    public static boolean isSnowEligible(BlockState state, BlockAndTintGetter level, BlockPos pos) {
        if (state.isSolidRender(level, pos)) {
            return false;
        }
        if (state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW)) {
            return false;
        }

        BlockPos belowPos = pos.below();
        BlockState below = level.getBlockState(belowPos);
        boolean sturdy = below.isFaceSturdy(level, belowPos, Direction.UP)
                || below.isSolidRender(level, belowPos)
                || (below.getBlock() instanceof StairBlock && below.getValue(StairBlock.HALF) == Half.TOP)
                || (below.getBlock() instanceof SlabBlock && below.getValue(SlabBlock.TYPE) == SlabType.TOP);
        if (!sturdy) {
            return false;
        }

        if (state.getBlock() instanceof BushBlock) {
            if (state.getBlock() instanceof DoublePlantBlock) {
                return state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER;
            }
            return true;
        }
        if (state.getBlock() instanceof CrossCollisionBlock
                || state.getBlock() instanceof FenceBlock
                || state.getBlock() instanceof FenceGateBlock
                || state.getBlock() instanceof WallBlock
                || state.getBlock() instanceof FlowerPotBlock
                || state.getBlock() instanceof SugarCaneBlock
                || state.getBlock() instanceof IronBarsBlock) {
            return true;
        }
        if (state.getBlock() instanceof StairBlock) {
            return state.getValue(StairBlock.HALF) == Half.TOP;
        }
        if (state.getBlock() instanceof SlabBlock) {
            return state.getValue(SlabBlock.TYPE) == SlabType.TOP;
        }
        if (state.getBlock() instanceof LanternBlock) {
            return !state.getValue(LanternBlock.HANGING);
        }
        if (state.getBlock() instanceof LeverBlock) {
            return state.getValue(LeverBlock.FACE) == AttachFace.FLOOR;
        }
        if (state.getBlock() instanceof ButtonBlock) {
            return state.getValue(ButtonBlock.FACE) == AttachFace.FLOOR;
        }
        if (state.getBlock() instanceof RedStoneWireBlock || state.getBlock() instanceof DiodeBlock) {
            return true;
        }

        return state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.SAPLINGS)
                || state.is(BlockTags.CROPS)
                || state.is(BlockTags.FENCES)
                || state.is(BlockTags.WALLS);
    }

    /**
     * Checks if any horizontal neighbor has snow, indicating that snow should connect here.
     * Aligned with OptiFine: only checks horizontal neighbors at the same Y level for snow blocks/layers.
     */
    public static boolean hasSnowNeighbours(BlockAndTintGetter level, BlockPos pos) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos nPos = pos.relative(dir);
            BlockState nState = level.getBlockState(nPos);
            if (nState.is(Blocks.SNOW) || nState.is(Blocks.SNOW_BLOCK) || nState.is(Blocks.POWDER_SNOW)) {
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
            BlockAndTintGetter level,
            BlockState hostState,
            BlockPos pos,
            PoseStack poseStack,
            VertexConsumer consumer,
            RandomSource random,
            long seed,
            int packedOverlay,
            @Nullable RenderType renderType
    ) {
        RENDERING_SNOW.set(true);
        try {
            BlockState snowState = getSnowLayerState();
            BakedModel model = snowModel;
            if (model == null) {
                model = Minecraft.getInstance().getBlockRenderer().getBlockModel(snowState);
                snowModel = model;
            }
            if (model != null) {
                poseStack.pushPose();
                Vec3 offset = hostState.getOffset(level, pos);
                if (offset.x != 0.0 || offset.y != 0.0 || offset.z != 0.0) {
                    poseStack.translate(-offset.x, -offset.y, -offset.z);
                }
                renderer.tesselateBlock(
                        level,
                        model,
                        snowState,
                        pos,
                        poseStack,
                        consumer,
                        true,
                        random,
                        seed,
                        packedOverlay,
                        ModelData.EMPTY,
                        null
                );
                poseStack.popPose();
            }
        } finally {
            RENDERING_SNOW.set(false);
        }
    }

    public static void renderSnow(
            ModelBlockRenderer renderer,
            BlockAndTintGetter level,
            BlockPos pos,
            PoseStack poseStack,
            VertexConsumer consumer,
            RandomSource random,
            long seed,
            int packedOverlay,
            @Nullable RenderType renderType
    ) {
        renderSnow(renderer, level, level.getBlockState(pos), pos, poseStack, consumer, random, seed, packedOverlay, renderType);
    }
}
