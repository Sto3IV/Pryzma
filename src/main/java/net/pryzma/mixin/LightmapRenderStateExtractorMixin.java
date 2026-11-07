package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.pryzma.lightmap.PryzmaLightmap;

@Mixin(LightmapRenderStateExtractor.class)
abstract class LightmapRenderStateExtractorMixin {
    @Shadow private float blockLightFlicker;

    @Inject(method = "tick", at = @At("TAIL"))
    private void prTickFlicker(CallbackInfo ci) {
        PryzmaLightmap.onFlickerTick(this.blockLightFlicker);
    }
}
