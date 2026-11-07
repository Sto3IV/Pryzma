package net.pryzma.shader.mixin.vertices.block_rendering;

import net.minecraft.client.renderer.block.BlockModelLighter;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.world.level.CardinalLighting;
import net.pryzma.shader.shaderpack.materialmap.WorldRenderingSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Allows the vanilla directional shading effect to be fully disabled by shader packs. This is needed by many packs
 * because they implement their own lighting effects, which visually clash with vanilla's directional shading lighting.
 */
@Mixin(BlockModelLighter.class)
public class MixinBlockModelLighter {
	@Inject(method = "getDirectionalBrightness", at = @At("HEAD"), cancellable = true)
	private static void pryzma$maybeDisableDirectionalShading(CardinalLighting lighting, BakedQuad quad, Direction direction, CallbackInfoReturnable<Float> cir) {
		if (WorldRenderingSettings.INSTANCE.shouldDisableDirectionalShading()) {
			cir.setReturnValue(1.0f);
		}
	}
}
