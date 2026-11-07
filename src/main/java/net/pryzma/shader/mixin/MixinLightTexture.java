package net.pryzma.shader.mixin;

import net.pryzma.shader.uniforms.CapturedRenderingState;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightmapRenderStateExtractor.class)
public class MixinLightTexture {
	@Inject(method = "extract", at = @At("RETURN"))
	private void pryzma$captureDarkness(LightmapRenderState state, float partialTick, CallbackInfo ci) {
		CapturedRenderingState.INSTANCE.setDarknessLightFactor(state.darknessEffectScale);
	}
}
