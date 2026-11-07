package net.pryzma.shader.mixin;

import net.pryzma.shader.PryzmaShaders;
import net.pryzma.shader.gl.state.GlBindingCache;
import net.pryzma.shader.pipeline.WorldRenderingPipeline;
import net.pryzma.shader.uniforms.CapturedRenderingState;
import net.pryzma.shader.uniforms.SystemTimeUniforms;
import net.minecraft.util.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.resources.model.ModelManager;
import org.lwjgl.opengl.GL11C;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {
	@Inject(method = "update", at = @At("HEAD"))
	private void pryzma$startFrame(DeltaTracker deltaTracker, CallbackInfo ci) {
		CapturedRenderingState.INSTANCE.setRealTickDelta(deltaTracker.getGameTimeDeltaPartialTick(true));
		SystemTimeUniforms.COUNTER.beginFrame();
		SystemTimeUniforms.TIMER.beginFrame(Util.getNanos());
		GlBindingCache.INSTANCE.invalidate();
	}

	@Inject(method = "<init>", at = @At("TAIL"))
	private void pryzma$logSystem(Minecraft minecraft, FirstPersonHandsAndItemsRenderer handsRenderer, ModelManager modelManager, ItemModelResolver itemModelResolver, CallbackInfo ci) {
		PryzmaShaders.logger.info("Hardware information:");
		PryzmaShaders.logger.info("CPU: " + System.getProperty("os.arch"));
		PryzmaShaders.logger.info("GPU: " + GL11C.glGetString(GL11C.GL_RENDERER) + " (Supports OpenGL " + GL11C.glGetString(GL11C.GL_VERSION) + ")");
		PryzmaShaders.logger.info("OS: " + System.getProperty("os.name") + " (" + System.getProperty("os.version") + ")");
	}

	@Inject(method = "renderLevel", at = @At("TAIL"))
	private void pryzma$runColorSpace(CallbackInfo ci) {
		PryzmaShaders.getPipelineManager().getPipeline().ifPresent(WorldRenderingPipeline::finalizeGameRendering);
	}
}
