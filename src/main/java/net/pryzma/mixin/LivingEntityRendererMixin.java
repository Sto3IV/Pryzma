package net.pryzma.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.pryzma.entity.model.PrCemContext;

/** CEM animations read the limb swing, age and head angles the renderer poses its model with. */
@Mixin(LivingEntityRenderer.class)
abstract class LivingEntityRendererMixin {
    @WrapOperation(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Ljava/lang/Object;)V"))
    private void prCemPose(EntityModel<?> model, Object stateObj, Operation<Void> original) {
        if (stateObj instanceof LivingEntityRenderState state) {
            PrCemContext.onSetupAnim(state.walkAnimationPos, state.walkAnimationSpeed, state.ageInTicks, state.yRot, state.xRot);
        }
        original.call(model, stateObj);
    }
}
