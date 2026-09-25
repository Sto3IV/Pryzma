package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.pryzma.PryzmaConfig;

/**
 * FEEDBACK_BUTTONS. The cells are removed before {@code GridLayout.arrangeElements()}, so the
 * menu re-centres exactly instead of leaving a gap.
 */
@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin {
    /** No server links: "Give Feedback" and "Report Bugs" form one row, dropped as a whole. */
    @WrapWithCondition(method = "createPauseMenu", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/PauseScreen;addFeedbackButtons(Lnet/minecraft/client/gui/screens/Screen;Lnet/minecraft/client/gui/layouts/GridLayout$RowHelper;)V"))
    private boolean prKeepFeedbackRow(Screen lastScreen, GridLayout.RowHelper rowHelper) {
        return PryzmaConfig.prFeedbackButtons;
    }

    /** With server links: "Feedback..." is dropped and "Server Links" takes the full row. */
    @WrapOperation(method = "createPauseMenu", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/layouts/GridLayout$RowHelper;addChild(Lnet/minecraft/client/gui/layouts/LayoutElement;)Lnet/minecraft/client/gui/layouts/LayoutElement;"))
    private LayoutElement prAddPauseChild(GridLayout.RowHelper rowHelper, LayoutElement child, Operation<LayoutElement> original) {
        if (!PryzmaConfig.prFeedbackButtons && child instanceof AbstractWidget widget
                && widget.getMessage().getContents() instanceof TranslatableContents contents) {
            if ("menu.feedback".equals(contents.getKey())) {
                return child;
            }
            if ("menu.server_links".equals(contents.getKey())) {
                widget.setWidth(204);
                return rowHelper.addChild(child, 2);
            }
        }
        return original.call(rowHelper, child);
    }
}
