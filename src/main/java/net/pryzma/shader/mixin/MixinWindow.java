package net.pryzma.shader.mixin;

import com.mojang.renderpearl.api.device.GpuDebugOptions;
import net.minecraft.client.Minecraft;
import net.pryzma.shader.PryzmaShaders;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Minecraft.class)
public class MixinWindow {
	@ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/api/device/GpuBackend;createDevice(Lcom/mojang/renderpearl/api/device/GpuDebugOptions;)Lcom/mojang/renderpearl/api/device/GpuDevice;"))
	private GpuDebugOptions pryzma$enableDebugContext(GpuDebugOptions original) {
		if (PryzmaShaders.getShaderConfig().areDebugOptionsEnabled()) {
			PryzmaShaders.logger.info("GPU debug context activated.");
			return new GpuDebugOptions(Math.max(original.logLevel(), 1), true, true, true);
		}
		return original;
	}
}
