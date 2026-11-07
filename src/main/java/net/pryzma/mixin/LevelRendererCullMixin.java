package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.LevelRenderer;
import net.pryzma.render.PrEntityCulling;

/**
 * Phase B5: Hooks LevelRenderer to populate {@link PrEntityCulling} after chunk occlusion
 * is resolved, and wraps entity rendering to discard occluded entities.
 */
@Mixin(LevelRenderer.class)
abstract class LevelRendererCullMixin {

    @Inject(method = "resetLevelRenderData", at = @At("HEAD"))
    private void prResetOnAllChanged(CallbackInfo ci) {
        PrEntityCulling.reset();
    }
}
