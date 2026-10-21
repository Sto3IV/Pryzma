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
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.item.ItemEntity;
import net.pryzma.PryzmaConfig;

/**
 * Dropped Items: in Fast mode (or Default with Fast graphics), non-block 2D items face the player
 * as billboards without spinning into edge-on invisibility and render a single flat sprite,
 * matching classic OptiFine 1.4-1.7 dropped items.
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
}
