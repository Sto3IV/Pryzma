package net.pryzma.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.pryzma.PryzmaConfig;
import net.pryzma.item.FlatItemBakedModel;

/**
 * Dropped Items: in Fast mode (or Default with Fast graphics), non-block 2D items face the player
 * as billboards without spinning into edge-on invisibility, render a single flat sprite, and strip
 * the extruded 1-pixel thickness so they render completely flat, matching classic Minecraft 1.2.5.
 */
@Mixin(ItemEntityRenderer.class)
abstract class ItemEntityRendererMixin {

    @WrapOperation(
            method = "render(Lnet/minecraft/world/entity/item/ItemEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionf;)V")
    )
    private void prDroppedItemRotation(PoseStack poseStack, Quaternionf rotation, Operation<Void> original,
            @Local(argsOnly = true) ItemEntity entity, @Local BakedModel bakedModel) {
        if (!PryzmaConfig.isDroppedItemsFancy() && !bakedModel.isGui3d()) {
            Camera camera = Minecraft.getInstance().getEntityRenderDispatcher().camera;
            float yaw = camera != null ? 180.0F - camera.getYRot() : 0.0F;
            original.call(poseStack, Axis.YP.rotationDegrees(yaw));
            return;
        }
        original.call(poseStack, rotation);
    }

    @WrapOperation(
            method = "renderMultipleFromCount(Lnet/minecraft/client/renderer/entity/ItemRenderer;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/item/ItemStack;Lnet/minecraft/client/resources/model/BakedModel;ZLnet/minecraft/util/RandomSource;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/ItemEntityRenderer;getRenderedAmount(I)I")
    )
    private static int prDroppedItemAmount(int count, Operation<Integer> original,
            @Local(argsOnly = true) boolean isGui3d) {
        if (!PryzmaConfig.isDroppedItemsFancy() && !isGui3d) {
            return 1;
        }
        return original.call(count);
    }

    @WrapOperation(
            method = "renderMultipleFromCount(Lnet/minecraft/client/renderer/entity/ItemRenderer;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/item/ItemStack;Lnet/minecraft/client/resources/model/BakedModel;ZLnet/minecraft/util/RandomSource;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/ItemRenderer;render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V")
    )
    private static void prFlattenDroppedItemModel(ItemRenderer renderer, ItemStack itemStack,
            ItemDisplayContext displayContext, boolean leftHand, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight, int packedOverlay, BakedModel bakedModel,
            Operation<Void> original, @Local(argsOnly = true) boolean isGui3d) {
        if (!PryzmaConfig.isDroppedItemsFancy() && !isGui3d) {
            bakedModel = FlatItemBakedModel.of(bakedModel);
        }
        original.call(renderer, itemStack, displayContext, leftHand, poseStack, bufferSource, packedLight, packedOverlay, bakedModel);
    }
}
