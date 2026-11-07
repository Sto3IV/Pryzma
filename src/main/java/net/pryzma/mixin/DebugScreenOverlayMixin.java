package net.pryzma.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.pryzma.gui.PrDebugOverlay;
import net.pryzma.render.PrF3RenderCache;

/**
 * The Pryzma lines of the F3 screen ({@link PrDebugOverlay}) and its cache invalidation.
 */
@Mixin(DebugScreenOverlay.class)
abstract class DebugScreenOverlayMixin {

    @Inject(method = "extractLines", at = @At("HEAD"))
    private void prExtractLines(GuiGraphicsExtractor extractor, List<String> lines, boolean left, int y, CallbackInfo ci) {
        if (left) {
            PrDebugOverlay.left(lines);
        } else {
            PrDebugOverlay.right(lines);
        }
    }

    /** The chart line changes with every toggle, and a reopened F3 shows fresh text. */
    @Inject(method = {"toggleNetworkCharts", "toggleFpsCharts", "toggleProfilerChart", "reset"}, at = @At("TAIL"))
    private void prInvalidate(CallbackInfo ci) {
        PrF3RenderCache.invalidate();
    }
}
