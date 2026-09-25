package net.pryzma.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.pryzma.PryzmaConfig;
import net.pryzma.render.PrRenderHooks;

/** Vignette and Held Item Tooltips. */
@Mixin(Gui.class)
abstract class GuiOptionsMixin {
    @ModifyExpressionValue(method = "renderCameraOverlays",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;useFancyGraphics()Z"))
    private boolean prVignette(boolean fancy) {
        return PrRenderHooks.isVignetteEnabled(fancy);
    }

    @Inject(method = "renderSelectedItemName(Lnet/minecraft/client/gui/GuiGraphics;I)V", at = @At("HEAD"), cancellable = true)
    private void prHeldItemTooltips(GuiGraphics graphics, int yShift, CallbackInfo ci) {
        if (!PryzmaConfig.prHeldItemTooltips) {
            ci.cancel();
        }
    }
}
