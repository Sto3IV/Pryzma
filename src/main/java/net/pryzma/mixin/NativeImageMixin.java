package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.platform.NativeImage;

import net.pryzma.gui.PrQuickInfoStats;

/** Native image memory held right now, the "+images" part of Quick Info's native line. */
@Mixin(NativeImage.class)
abstract class NativeImageMixin {
    @Shadow
    private long pixels;
    @Shadow
    @Final
    private long size;

    @Inject(method = {"<init>(Lcom/mojang/blaze3d/platform/NativeImage$Format;IIZ)V",
            "<init>(Lcom/mojang/blaze3d/platform/NativeImage$Format;IIZJ)V"}, at = @At("TAIL"))
    private void prTrackAlloc(CallbackInfo ci) {
        if (this.pixels != 0L) {
            PrQuickInfoStats.IMAGE_BYTES.addAndGet(this.size);
        }
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void prTrackFree(CallbackInfo ci) {
        if (this.pixels != 0L) {
            PrQuickInfoStats.IMAGE_BYTES.addAndGet(-this.size);
        }
    }
}
