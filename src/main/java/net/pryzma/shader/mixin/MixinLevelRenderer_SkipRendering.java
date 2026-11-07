package net.pryzma.shader.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.LevelRenderer;
import net.pryzma.shader.PryzmaShaders;
import net.pryzma.shader.pipeline.ShadedWorldRenderingPipeline;

@Mixin(LevelRenderer.class)
public class MixinLevelRenderer_SkipRendering {
	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void pryzma$skipAllRendering(CallbackInfo ci) {
		if (PryzmaShaders.getPipelineManager().getPipelineNullable() instanceof ShadedWorldRenderingPipeline pipeline && pipeline.skipAllRendering()) {
			ci.cancel();
		}
	}
}
