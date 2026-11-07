package net.pryzma.shader.mixin;

import com.mojang.renderpearl.api.commands.RenderPass;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.QuadParticleFeatureRenderer;
import net.minecraft.client.renderer.oit.OitStage;
import net.pryzma.shader.PryzmaShaders;
import net.pryzma.shader.pipeline.WorldRenderingPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Ensures that all particles are rendered with the textured_lit shader program / PARTICLES phase.
 */
@Mixin(QuadParticleFeatureRenderer.class)
public class MixinParticleEngine {
	@Inject(method = "executeGroup", at = @At("HEAD"))
	private void pryzma$beginDrawingParticles(FeatureFrameContext context, OitStage stage, RenderPass pass, int i, List<?> list, boolean b, CallbackInfo ci) {
		PryzmaShaders.getPipelineManager().getPipeline().ifPresent(pipeline -> pipeline.setPhase(WorldRenderingPhase.PARTICLES));
	}

	@Inject(method = "executeGroup", at = @At("RETURN"))
	private void pryzma$finishDrawingParticles(FeatureFrameContext context, OitStage stage, RenderPass pass, int i, List<?> list, boolean b, CallbackInfo ci) {
		PryzmaShaders.getPipelineManager().getPipeline().ifPresent(pipeline -> pipeline.setPhase(WorldRenderingPhase.NONE));
	}
}
