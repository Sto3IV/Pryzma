package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.pryzma.PryzmaConfig;

/**
 * TELEMETRY_ATTRIBUTION. "Telemetry Data" and "Credits &amp; Attribution" form the last row of the
 * two-column grid; skipping both before {@code arrangeElements()} leaves four full rows, centred.
 * Credits stay reachable through the title screen's copyright link.
 */
@Mixin(OptionsScreen.class)
public abstract class OptionsScreenMixin {
    /** The skipped button is still returned: init() goes on to set the telemetry button's active state and tooltip. */
    @WrapOperation(method = "init()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/layouts/GridLayout$RowHelper;addChild(Lnet/minecraft/client/gui/layouts/LayoutElement;)Lnet/minecraft/client/gui/layouts/LayoutElement;"))
    private LayoutElement prAddOptionsChild(GridLayout.RowHelper rowHelper, LayoutElement child, Operation<LayoutElement> original) {
        if (!PryzmaConfig.prTelemetryAttribution && child instanceof AbstractWidget widget
                && widget.getMessage().getContents() instanceof TranslatableContents contents
                && ("options.telemetry".equals(contents.getKey()) || "options.credits_and_attribution".equals(contents.getKey()))) {
            return child;
        }
        return original.call(rowHelper, child);
    }
}
