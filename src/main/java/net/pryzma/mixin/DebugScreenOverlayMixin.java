package net.pryzma.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.pryzma.gui.PrDebugOverlay;

/** The Pryzma lines of the F3 screen, added to both columns as vanilla builds them. */
@Mixin(DebugScreenOverlay.class)
abstract class DebugScreenOverlayMixin {
    @Inject(method = "getGameInformation()Ljava/util/List;", at = @At("RETURN"))
    private void prGameInformation(CallbackInfoReturnable<List<String>> cir) {
        PrDebugOverlay.left(cir.getReturnValue());
    }

    @Inject(method = "getSystemInformation()Ljava/util/List;", at = @At("RETURN"))
    private void prSystemInformation(CallbackInfoReturnable<List<String>> cir) {
        PrDebugOverlay.right(cir.getReturnValue());
    }
}
