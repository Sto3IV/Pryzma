package net.pryzma.shader.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.device.GpuDevice;
import net.pryzma.shader.PryzmaShaders;
import net.pryzma.shader.gl.GLDebug;
import net.pryzma.shader.gl.ShaderRenderSystem;
import net.pryzma.shader.samplers.ShaderSamplers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderSystem.class)
public class MixinRenderSystem {
	@Inject(method = "initRenderer(Lcom/mojang/renderpearl/api/device/GpuDevice;)V", at = @At("RETURN"), remap = false)
	private static void pryzma$onRendererInit(GpuDevice device, CallbackInfo ci) {
		PryzmaShaders.duringRenderSystemInit();
		GLDebug.reloadDebugState();
		ShaderRenderSystem.initRenderer();
		ShaderSamplers.initRenderer();
		PryzmaShaders.onRenderSystemInit();
	}
}
