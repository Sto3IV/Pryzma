package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.pryzma.render.PrEntityCulling;

/**
 * Phase B5: Hooks LevelRenderer to populate {@link PrEntityCulling} after chunk occlusion
 * is resolved, and wraps {@link EntityRenderDispatcher#shouldRender} to discard occluded entities.
 */
@Mixin(LevelRenderer.class)
abstract class LevelRendererCullMixin {

    @Shadow
    private ObjectArrayList<SectionRenderDispatcher.RenderSection> visibleSections;

    @Inject(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;setupRender(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;ZZ)V",
            shift = At.Shift.AFTER))
    private void prStartEntityCullingFrame(CallbackInfo ci) {
        PrEntityCulling.onFrameStart(this.visibleSections);
    }

    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;shouldRender(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/culling/Frustum;DDD)Z"))
    private boolean prShouldRenderEntity(EntityRenderDispatcher dispatcher, Entity entity, Frustum frustum, double camX, double camY, double camZ, Operation<Boolean> original) {
        return original.call(dispatcher, entity, frustum, camX, camY, camZ) && PrEntityCulling.shouldRender(entity);
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void prEndEntityCullingFrame(CallbackInfo ci) {
        PrEntityCulling.onFrameEnd();
    }

    @Inject(method = "allChanged", at = @At("HEAD"))
    private void prResetOnAllChanged(CallbackInfo ci) {
        PrEntityCulling.reset();
    }

    @Inject(method = "setLevel", at = @At("HEAD"))
    private void prResetOnSetLevel(ClientLevel level, CallbackInfo ci) {
        PrEntityCulling.reset();
    }
}
