package net.pryzma.util;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.pryzma.Config;

/**
 * Runtime helper for PauseScreen layout adjustments.
 * Governs pause menu feedback buttons and server links widening according to Pryzma configuration.
 */
public class PauseScreenHelper {

    /**
     * Intercepts the addition of the FEEDBACK_SUBSCREEN button ("menu.feedback") in PauseScreen.createPauseMenu.
     * When feedback buttons are disabled, skips adding the element to the grid.
     */
    public static LayoutElement addFeedbackSubscreen(GridLayout.RowHelper helper, LayoutElement button) {
        if (Config.isFeedbackButtons()) {
            return helper.addChild(button);
        }
        // Discarded by the following POP instruction in bytecode; not added to GridLayout
        return button;
    }

    /**
     * Intercepts the addition of the SERVER_LINKS button ("menu.server_links") in PauseScreen.createPauseMenu.
     * When feedback buttons are disabled, widens the Server Links button to full width (204 px, span 2)
     * so it cleanly occupies the row without leaving any empty holes.
     */
    public static LayoutElement addServerLinks(GridLayout.RowHelper helper, LayoutElement button) {
        if (Config.isFeedbackButtons()) {
            return helper.addChild(button);
        }
        if (button instanceof AbstractWidget widget) {
            widget.setWidth(204);
        }
        return helper.addChild(button, 2);
    }
}
