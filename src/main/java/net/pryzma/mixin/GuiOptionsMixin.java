package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.entity.Entity;
import net.pryzma.PryzmaConfig;
import net.pryzma.render.PrRenderHooks;

/** Vignette and Held Item Tooltips. */
@Mixin(Hud.class)
abstract class GuiOptionsMixin {
    @Inject(method = "extractVignette", at = @At("HEAD"), cancellable = true)
    private void prVignette(GuiGraphicsExtractor extractor, Entity entity, CallbackInfo ci) {
        if (!PrRenderHooks.isVignetteEnabled(true)) {
            ci.cancel();
        }
    }

    @Inject(method = "extractSelectedItemName", at = @At("HEAD"), cancellable = true)
    private void prHeldItemTooltips(GuiGraphicsExtractor extractor, CallbackInfo ci) {
        if (!PryzmaConfig.prHeldItemTooltips) {
            ci.cancel();
        }
    }
}
