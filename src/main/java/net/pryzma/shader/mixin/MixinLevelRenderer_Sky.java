package net.pryzma.shader.mixin;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.world.level.material.FogType;
import net.pryzma.shader.PryzmaShaders;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRenderer.class)
public class MixinLevelRenderer_Sky {
	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void preRenderSky(GpuBufferSlice ubo, SkyRenderState state, CallbackInfo ci) {
		if (PryzmaShaders.getCurrentPack().isEmpty()) {
			Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
			if (camera.getFluidInCamera() != FogType.NONE) {
				ci.cancel();
			}
		}
	}
}
