package net.pryzma.shader.mixin.fabulous;

import net.pryzma.shader.PryzmaShaders;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class MixinDisableFabulousGraphics {
	@Inject(method = "onResourceManagerReload", at = @At("HEAD"))
	private void pryzma$disableFabulousGraphicsOnResourceReload(CallbackInfo ci) {
		pryzma$disableFabulousGraphics();
	}

	// This method is called whenever the user tries to change the graphics mode.
	// We can still revert / intercept the change at the head of the method.
	@Inject(method = "allChanged", at = @At("HEAD"))
	private void pryzma$disableFabulousGraphicsOnLevelRendererReload(CallbackInfo ci) {
		pryzma$disableFabulousGraphics();
	}

	@Unique
	private void pryzma$disableFabulousGraphics() {
		Options options = Minecraft.getInstance().options;

		if (!PryzmaShaders.getShaderConfig().areShadersEnabled()) {
			// Nothing to do here, shaders are disabled.
			return;
		}

		if (options.graphicsMode().get() == GraphicsStatus.FABULOUS) {
			// Disable fabulous graphics when shaders are enabled.
			options.graphicsMode().set(GraphicsStatus.FANCY);
		}
	}
}
