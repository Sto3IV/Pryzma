package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.texture.MipmapGenerator;
import net.pryzma.render.PrMipmaps;

/**
 * Replaces vanilla unweighted mipmap blending with alpha-weighted linear downsampling.
 */
@Mixin(MipmapGenerator.class)
abstract class MipmapGeneratorMixin {
    @Inject(method = "alphaBlend", at = @At("HEAD"), cancellable = true)
    private static void prAlphaBlend(int col1, int col2, int col3, int col4, boolean transparent, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(PrMipmaps.alphaBlend(col1, col2, col3, col4));
    }
}
