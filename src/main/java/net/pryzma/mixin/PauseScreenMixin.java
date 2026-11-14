package net.pryzma.mixin;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.pryzma.PryzmaConfig;

@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {
    protected PauseScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init()V", at = @At("RETURN"))
    private void prAdjustPauseScreenButtons(CallbackInfo ci) {
        if (PryzmaConfig.prFeedbackButtons) {
            return;
        }

        List<AbstractWidget> toRemove = new ArrayList<>();
        List<AbstractWidget> iconRow = new ArrayList<>();
        int rowY = -1;

        for (GuiEventListener child : this.children()) {
            if (child instanceof AbstractWidget widget) {
                // The icon row is 20x20 buttons (ModsButton, Report Bugs, Give Feedback, FriendsButton, Player Reporting)
                if (widget.getWidth() == 20 && widget.getHeight() == 20) {
                    if (rowY == -1) {
                        rowY = widget.getY();
                    }
                    if (Math.abs(widget.getY() - rowY) <= 4) {
                        if (isFeedbackOrBugWidget(widget)) {
                            toRemove.add(widget);
                        } else {
                            iconRow.add(widget);
                        }
                    }
                }
            }
        }

        if (toRemove.isEmpty()) {
            return;
        }

        for (AbstractWidget widget : toRemove) {
            this.removeWidget(widget);
        }

        // Re-center remaining icon buttons horizontally around width / 2
        iconRow.sort((a, b) -> Integer.compare(a.getX(), b.getX()));
        int count = iconRow.size();
        if (count > 0) {
            int gap = 4;
            int totalWidth = count * 20 + (count - 1) * gap;
            int startX = this.width / 2 - totalWidth / 2;
            for (int i = 0; i < count; i++) {
                iconRow.get(i).setX(startX + i * (20 + gap));
            }
        }
    }

    private static boolean isFeedbackOrBugWidget(AbstractWidget w) {
        return matchesFeedbackOrBug(w.getMessage());
    }

    private static boolean matchesFeedbackOrBug(Component msg) {
        if (msg == null) {
            return false;
        }
        if (msg.getContents() instanceof TranslatableContents tc) {
            String key = tc.getKey().toLowerCase();
            if (key.contains("feedback") || key.contains("reportbugs") || key.contains("report_bugs")) {
                return true;
            }
        }
        String str = msg.getString().toLowerCase();
        return str.contains("feedback") || str.contains("report bug") || str.contains("отзыв") || str.contains("ошибк");
    }
}
