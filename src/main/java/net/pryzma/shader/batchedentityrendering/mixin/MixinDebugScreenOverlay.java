package net.pryzma.shader.batchedentityrendering.mixin;

import net.pryzma.shader.batchedentityrendering.impl.BatchingDebugMessageHelper;
import net.pryzma.shader.batchedentityrendering.impl.DrawCallTrackingRenderBuffers;
import net.pryzma.shader.PryzmaShaders;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Adds entity batching debug information to the debug screen. Uses a priority of 1010
 * so that we apply after other mixins to the debug screen (such as the one that adds PryzmaShaders
 * shader pack information), so that the entity batching debug logic appears at the bottom.
 */
@Mixin(value = DebugScreenOverlay.class, priority = 1010)
public abstract class MixinDebugScreenOverlay {
	@Inject(method = "getGameInformation", at = @At("RETURN"))
	private void batchedentityrendering$appendStats(CallbackInfoReturnable<List<String>> cir) {
		List<String> messages = cir.getReturnValue();

		DrawCallTrackingRenderBuffers drawTracker = (DrawCallTrackingRenderBuffers) Minecraft.getInstance().renderBuffers();

		// blank line separator
		if (PryzmaShaders.getShaderConfig().areDebugOptionsEnabled()) {
			messages.add("");
			messages.add("[Entity Batching] " + BatchingDebugMessageHelper.getDebugMessage(drawTracker));
		}
	}
}
