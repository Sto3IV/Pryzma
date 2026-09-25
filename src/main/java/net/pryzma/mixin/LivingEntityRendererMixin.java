package net.pryzma.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.Entity;
import net.pryzma.entity.model.PrCemContext;

/** CEM animations read the limb swing, age and head angles the renderer poses its model with. */
@Mixin(LivingEntityRenderer.class)
abstract class LivingEntityRendererMixin {
    @WrapOperation(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V"))
    private void prCemPose(EntityModel<Entity> model, Entity entity, float limbSwing, float limbSpeed, float age, float headYaw,
            float headPitch, Operation<Void> original) {
        PrCemContext.onSetupAnim(limbSwing, limbSpeed, age, headYaw, headPitch);
        original.call(model, entity, limbSwing, limbSpeed, age, headYaw, headPitch);
    }
}
