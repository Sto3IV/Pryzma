package net.pryzma.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.pryzma.entity.model.PrCemContext;
import net.pryzma.entity.model.PrCemModels;
import net.pryzma.entity.model.PrCemRender;
import net.pryzma.entity.texture.PrEntityTextures;

/**
 * Entity texture and model features around each entity render: the entity is known to the
 * texture mapping and to CEM animations, a random model variant replaces the renderer, a CEM model
 * texture replaces the renderer's texture, and an entity that used a texture with an emissive
 * counterpart is rendered a second time at full brightness, drawing only the emissive layers.
 */
@Mixin(EntityRenderDispatcher.class)
abstract class EntityRenderDispatcherMixin {
    @Inject(method = "getRenderer", at = @At("RETURN"), cancellable = true)
    private <T extends Entity> void prCemVariant(T entity, CallbackInfoReturnable<EntityRenderer<? super T>> cir) {
        EntityRenderer<? super T> base = cir.getReturnValue();
        if (base != null) {
            @SuppressWarnings("unchecked")
            EntityRenderer<? super T> chosen = (EntityRenderer<? super T>) PrCemModels.select(entity, base);
            if (chosen != base) {
                cir.setReturnValue(chosen);
            }
        }
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private void prRenderEntity(EntityRenderer<Entity> renderer, Entity entity, float yaw, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int light, Operation<Void> original) {
        PrEntityTextures.Scope textures = PrEntityTextures.begin(entity);
        PrCemContext.Scope cem = PrCemContext.begin(entity, renderer, partialTick);
        PrCemRender.Scope cemRender = PrCemRender.begin(buffers);
        try {
            ResourceLocation modelTexture = PrCemModels.modelTexture(entity);
            if (modelTexture != null) {
                PrEntityTextures.setModelTexture(renderer.getTextureLocation(entity), modelTexture);
            }
            int dynLight = net.pryzma.light.PrDynamicLights.getEntityLight(entity, light);
            original.call(renderer, entity, yaw, partialTick, pose, buffers, dynLight);
            if (PrEntityTextures.needsEmissivePass()) {
                MultiBufferSource emissive = PrEntityTextures.beginEmissivePass(buffers);
                PrCemRender.begin(emissive);
                // setupAnim runs again in the second render; the CEM pose must be applied again on top.
                PrCemContext.nextPass();
                original.call(renderer, entity, yaw, partialTick, pose, emissive, LightTexture.FULL_BRIGHT);
            }
        } finally {
            PrCemRender.end(cemRender);
            PrCemContext.end(cem);
            PrEntityTextures.end(textures);
        }
    }
}
