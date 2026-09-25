package net.pryzma.gui;

import net.minecraft.client.gui.components.AbstractSliderButton;

/**
 * Option slider. While the mouse drags an apply-on-release option the label follows the handle
 * and nothing is applied; the value lands once, on release, as the 1.x sliders did for options
 * that rebuild chunks or textures. Keyboard steps apply at once.
 */
final class PrOptionSlider extends AbstractSliderButton implements PrOptionControl {
    private final PrOption.Slider option;
    private final Runnable changed;
    private boolean dragging;

    PrOptionSlider(int x, int y, int width, PrOption.Slider option, Runnable changed) {
        super(x, y, width, 20, option.label(), option.position());
        this.option = option;
        this.changed = changed;
    }

    @Override
    protected void updateMessage() {
        setMessage(option.labelFor(option.valueAt(value)));
    }

    @Override
    protected void applyValue() {
        if (!(dragging && option.applyOnRelease)) {
            commit();
        }
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        dragging = true;
        super.onClick(mouseX, mouseY);
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
        super.onRelease(mouseX, mouseY);
        if (dragging) {
            dragging = false;
            commit();
        }
    }

    private void commit() {
        option.set(option.valueAt(value));
        value = option.position();
        updateMessage();
        changed.run();
    }

    @Override
    public String tooltipKey() {
        return option.key();
    }

    @Override
    public void refresh() {
        if (!dragging) {
            value = option.position();
            updateMessage();
        }
    }
}
