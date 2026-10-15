package net.pryzma.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.pryzma.PryzmaConfig;

/** Cycling option button: click steps forward (Shift: backward), the wheel steps like vanilla's. */
final class PrOptionButton extends Button implements PrOptionControl {
    private final PrOption.Cycle option;
    private final Runnable changed;

    PrOptionButton(int x, int y, int width, PrOption.Cycle option, Runnable changed) {
        super(x, y, width, 20, option.label(), button -> {
        }, DEFAULT_NARRATION);
        this.option = option;
        this.changed = changed;
    }

    @Override
    public void onPress() {
        step(Screen.hasShiftDown() ? -1 : 1);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!active || !visible || scrollY == 0.0) {
            return false;
        }
        step(scrollY > 0.0 ? -1 : 1);
        return true;
    }

    private void step(int direction) {
        option.cycle(direction);
        changed.run();
    }

    PrOption.Cycle option() {
        return option;
    }

    @Override
    public String tooltipKey() {
        if (PryzmaConfig.prFarlandsDistance > 0) {
            if (option == PrOptions.FOG_FANCY) {
                return "pr.options.fog.locked";
            }
            if (option == PrOptions.FOG_START) {
                return "pr.options.fogStart.locked";
            }
        }
        if (option == PrOptions.MIPMAP_TYPE && Minecraft.getInstance().options.mipmapLevels().get() == 0) {
            return "pr.options.mipmapType.locked";
        }
        return option.key();
    }

    @Override
    public void refresh() {
        setMessage(option.label());
    }
}
