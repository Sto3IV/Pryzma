package net.pryzma.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.pryzma.PryzmaConfig;

/**
 * Dropped Items: in Fast mode (or Default with Fast graphics), non-block 2D items face the player
 * as billboards without spinning into edge-on invisibility, render a single flat sprite, and strip
 * the extruded 1-pixel thickness so they render completely flat, matching classic Minecraft 1.2.5.
 */
@Mixin(ItemEntityRenderer.class)
abstract class ItemEntityRendererMixin {

    @Inject(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("HEAD")
    )
    private void prDroppedItemCount(ItemEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
            CameraRenderState camera, CallbackInfo ci) {
        if (!PryzmaConfig.isDroppedItemsFancy() && state.item.getModelBoundingBox().getZsize() <= 0.0625f) {
            state.count = 1;
        }
    }

    @WrapOperation(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;rotate(Lcom/mojang/math/Axis;F)V")
    )
    private void prDroppedItemRotation(PoseStack poseStack, Axis axis, float angle, Operation<Void> original,
            @Local(argsOnly = true) ItemEntityRenderState state,
            @Local(argsOnly = true) CameraRenderState camera) {
        if (!PryzmaConfig.isDroppedItemsFancy() && state.item.getModelBoundingBox().getZsize() <= 0.0625f) {
            float yaw = 180.0F - camera.yRot;
            original.call(poseStack, Axis.YP, yaw);
            return;
        }
        original.call(poseStack, axis, angle);
    }
}
