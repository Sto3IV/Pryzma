package net.pryzma.shader.mixin.sky;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.commands.RenderPass;
import net.minecraft.client.OptionInstance;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.state.level.WeatherRenderState;
import net.pryzma.shader.PryzmaShaders;
import net.pryzma.shader.pipeline.WorldRenderingPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Allows pipelines to disable weather rendering and weather particles.
 */
@Mixin(LevelRenderer.class)
public class MixinLevelRenderer_SunMoonToggle {

	@WrapWithCondition(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/WeatherEffectRenderer;render(Lnet/minecraft/client/renderer/state/level/WeatherRenderState;Lcom/mojang/renderpearl/api/commands/RenderPass;)V"))
	private boolean pryzma$disableWeather(WeatherEffectRenderer instance, WeatherRenderState state, RenderPass renderPass) {
		return PryzmaShaders.getPipelineManager().getPipeline().map(WorldRenderingPipeline::shouldRenderWeather).orElse(true);
	}

	@WrapOperation(method = "tickRain", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;", ordinal = 1))
	private Object disableRainParticles(OptionInstance<?> instance, Operation<ParticleStatus> original) {
		if (!PryzmaShaders.getPipelineManager().getPipeline().map(WorldRenderingPipeline::shouldRenderWeatherParticles).orElse(true)) {
			return ParticleStatus.MINIMAL;
		}
		return original.call(instance);
	}
}
