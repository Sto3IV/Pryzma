package net.pryzma.gui;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** A screen button carrying its Pryzma 1.x id (200 Done, 201 Details, ... 231 Shaders). */
final class PrButton extends Button implements PrOptionControl {
    final int id;
    private String customTooltipKey;

    PrButton(int id, int x, int y, int width, Component text, OnPress onPress) {
        super(x, y, width, 20, text, onPress, DEFAULT_NARRATION);
        this.id = id;
    }

    void setTooltipKey(String key) {
        this.customTooltipKey = key;
    }

    @Override
    public String tooltipKey() {
        return customTooltipKey;
    }

    @Override
    public void refresh() {
    }
}
