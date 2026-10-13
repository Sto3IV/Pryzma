package net.pryzma.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.pryzma.PryzmaConfig;
import net.pryzma.render.PrBetterGrass;
import net.pryzma.render.PrBetterSnow;

/**
 * Alternate Blocks modifier, Better Grass quad replacement, and Better Snow auxiliary tessellation.
 */
@Mixin(ModelBlockRenderer.class)
abstract class ModelBlockRendererMixin {
    @ModifyVariable(
        method = "tesselateBlock(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;JILnet/neoforged/neoforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V",
        at = @At("HEAD"),
        argsOnly = true
    )
    private long prAlternateBlocks(long seed) {
        return PryzmaConfig.prAlternateBlocks ? seed : 0L;
    }

    @Inject(
        method = "tesselateBlock(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;JILnet/neoforged/neoforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V",
        at = @At("HEAD")
    )
    private void prCaptureContext(
        BlockAndTintGetter level,
        BakedModel model,
        BlockState state,
        BlockPos pos,
        PoseStack poseStack,
        VertexConsumer consumer,
        boolean checkSides,
        RandomSource random,
        long seed,
        int packedOverlay,
        ModelData modelData,
        RenderType renderType,
        CallbackInfo ci
    ) {
        PrBetterGrass.setContext(level, pos, state);
    }

    @Inject(
        method = "tesselateBlock(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;JILnet/neoforged/neoforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V",
        at = @At("RETURN")
    )
    private void prRenderBetterSnowAndClear(
        BlockAndTintGetter level,
        BakedModel model,
        BlockState state,
        BlockPos pos,
        PoseStack poseStack,
        VertexConsumer consumer,
        boolean checkSides,
        RandomSource random,
        long seed,
        int packedOverlay,
        ModelData modelData,
        RenderType renderType,
        CallbackInfo ci
    ) {
        try {
            if (PryzmaConfig.prBetterSnow && !PrBetterSnow.isRenderingSnow() && PrBetterSnow.shouldRender(level, state, pos)) {
                PrBetterSnow.renderSnow((ModelBlockRenderer) (Object) this, level, state, pos, poseStack, consumer, random, seed, packedOverlay, renderType);
            }
        } finally {
            PrBetterGrass.clearContext();
        }
    }

    @WrapOperation(
        method = {
            "tesselateWithAO(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;JILnet/neoforged/neoforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V",
            "tesselateWithoutAO(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;JILnet/neoforged/neoforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V"
        },
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/model/BakedModel;getQuads(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;Lnet/minecraft/util/RandomSource;Lnet/neoforged/neoforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)Ljava/util/List;")
    )
    private List<BakedQuad> prBetterGrassQuads(
        BakedModel model,
        BlockState state,
        Direction direction,
        RandomSource random,
        ModelData modelData,
        RenderType renderType,
        Operation<List<BakedQuad>> original
    ) {
        if (PrBetterSnow.isRenderingSnow()) {
            if (direction != null && direction.getAxis().isHorizontal()) {
                BlockAndTintGetter level = PrBetterGrass.getCurrentLevel();
                BlockPos pos = PrBetterGrass.getCurrentPos();
                if (level != null && pos != null) {
                    BlockPos adjPos = pos.relative(direction);
                    BlockState adjState = level.getBlockState(adjPos);
                    if (PrBetterSnow.shouldRender(level, adjState, adjPos)) {
                        return java.util.Collections.emptyList();
                    }
                }
            }
            return original.call(model, state, direction, random, modelData, renderType);
        }
        if (direction != null && direction.getAxis().isHorizontal() && PryzmaConfig.isBetterGrass()) {
            List<BakedQuad> custom = PrBetterGrass.getFaceQuads(model, state, direction, random, modelData, renderType);
            if (custom != null) {
                return custom;
            }
        }
        return original.call(model, state, direction, random, modelData, renderType);
    }
}
