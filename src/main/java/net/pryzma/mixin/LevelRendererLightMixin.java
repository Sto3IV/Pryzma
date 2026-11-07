package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.pryzma.light.PrDynamicLights;

/**
 * Dynamic light integration into LevelRenderer: updates active light sources per frame.
 */
@Mixin(LevelRenderer.class)
abstract class LevelRendererLightMixin {
    @Shadow private ClientLevel level;

    @Inject(method = "render", at = @At("HEAD"))
    private void prDynamicLightsUpdate(CallbackInfo ci) {
        PrDynamicLights.update((LevelRenderer) (Object) this, this.level);
    }
}
