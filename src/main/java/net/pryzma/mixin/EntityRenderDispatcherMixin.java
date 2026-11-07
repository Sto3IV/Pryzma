package net.pryzma.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.pryzma.entity.PrEntityRenderStateDuck;
import net.pryzma.entity.model.PrCemContext;
import net.pryzma.entity.model.PrCemModels;
import net.pryzma.entity.texture.PrEntityTextures;
import net.pryzma.render.PrEmissiveSubmitNodeCollector;

/**
 * Entity texture and model features around each entity render: the entity is known to the
 * texture mapping and to CEM animations, a random model variant replaces the renderer, a CEM model
 * texture replaces the renderer's texture, and an entity that used a texture with an emissive
 * counterpart is rendered a second time at full brightness, drawing only the emissive layers.
 */
@Mixin(EntityRenderDispatcher.class)
abstract class EntityRenderDispatcherMixin {
    @Inject(method = "getRenderer(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/client/renderer/entity/EntityRenderer;", at = @At("RETURN"), cancellable = true)
    private <T extends Entity> void prCemVariant(T entity, CallbackInfoReturnable<EntityRenderer<? super T, ?>> cir) {
        EntityRenderer<? super T, ?> base = cir.getReturnValue();
        if (base != null) {
            @SuppressWarnings("unchecked")
            EntityRenderer<? super T, ?> chosen = (EntityRenderer<? super T, ?>) PrCemModels.select(entity, base);
            if (chosen != base) {
                cir.setReturnValue(chosen);
            }
        }
    }

    @SuppressWarnings("unchecked")
    @WrapOperation(method = "submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/client/renderer/state/level/CameraRenderState;DDDLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V"))
    private <S extends EntityRenderState> void prSubmitEntity(EntityRenderer<?, S> renderer, S renderState,
            PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera, Operation<Void> original) {
        Entity entity = renderState instanceof PrEntityRenderStateDuck duck ? duck.prGetEntity() : null;
        PrEntityTextures.Scope textures = entity != null ? PrEntityTextures.begin(entity) : null;
        PrCemContext.Scope cem = entity != null ? PrCemContext.begin(entity, renderer, 0.0f) : null;
        try {
            if (entity != null) {
                Identifier modelTexture = PrCemModels.modelTexture(entity);
                if (modelTexture != null) {
                    Identifier from = null;
                    if (renderer instanceof LivingEntityRenderer<?, ?, ?> living && renderState instanceof LivingEntityRenderState livingState) {
                        try {
                            from = ((LivingEntityRenderer<?, LivingEntityRenderState, ?>) living).getTextureLocation(livingState);
                        } catch (Throwable ignored) {
                        }
                    }
                    PrEntityTextures.setModelTexture(from, modelTexture);
                }
            }
            original.call(renderer, renderState, pose, collector, camera);
            if (PrEntityTextures.needsEmissivePass()) {
                SubmitNodeCollector emissive = PrEmissiveSubmitNodeCollector.wrap(collector);
                PrEntityTextures.setEmissivePass(true);
                try {
                    PrCemContext.nextPass();
                    original.call(renderer, renderState, pose, emissive, camera);
                } finally {
                    PrEntityTextures.setEmissivePass(false);
                }
            }
        } finally {
            if (cem != null) PrCemContext.end(cem);
            if (textures != null) PrEntityTextures.end(textures);
        }
    }
}
