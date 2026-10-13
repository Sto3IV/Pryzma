package net.pryzma.gui;

import net.minecraft.client.gui.components.AbstractSliderButton;
import org.lwjgl.glfw.GLFW;

/**
 * Option slider. While the mouse drags an apply-on-release option the label follows the handle
 * and nothing is applied; the value lands once, on release, as the 1.x sliders did for options
 * that rebuild chunks or textures. Keyboard steps and mouse wheel apply at once.
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

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!active || !visible) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            step(-1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            step(1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!active || !visible || !isMouseOver(mouseX, mouseY) || scrollY == 0.0) {
            return false;
        }
        step(scrollY > 0.0 ? 1 : -1);
        return true;
    }

    private void step(int direction) {
        if (dragging) {
            dragging = false;
        }
        if (option.steps > 0) {
            int currentStep = (int) Math.round(value * option.steps);
            int nextStep = Math.max(0, Math.min(option.steps, currentStep + direction));
            if (nextStep != currentStep) {
                value = (double) nextStep / option.steps;
                commit();
            }
        } else {
            double nextPos = Math.max(0.0, Math.min(1.0, value + direction * 0.05));
            if (nextPos != value) {
                value = nextPos;
                commit();
            }
        }
    }

    private void commit() {
        option.set(option.valueAt(value));
        value = option.position();
        updateMessage();
        changed.run();
    }

    PrOption.Slider option() {
        return option;
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
