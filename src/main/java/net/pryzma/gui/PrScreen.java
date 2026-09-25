package net.pryzma.gui;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.pryzma.PryzmaConfig;

/**
 * Shared frame of the Pryzma settings screens: the 1.x two-column grid, right-click routing,
 * option tooltips and saving on close.
 */
abstract class PrScreen extends Screen {
    /** A vanilla option placed in the grid, with the key its 1.x tooltip lines hang off. */
    record Vanilla(String key, OptionInstance<?> option) {
    }

    protected final Screen parent;
    private final PrTooltips tooltips = new PrTooltips(this);
    private final Map<AbstractWidget, String> vanillaKeys = new IdentityHashMap<>();

    PrScreen(Component title, Screen parent) {
        super(title);
        this.parent = parent;
    }

    static Vanilla vanilla(String key, OptionInstance<?> option) {
        return new Vanilla(key, option);
    }

    /** Grid cell {@code i}: two columns 160 apart, rows 21 apart, as every 1.x settings page. */
    int cellX(int i) {
        return width / 2 - 155 + i % 2 * 160;
    }

    int cellY(int i) {
        return height / 6 + 21 * (i / 2) - 12;
    }

    /** The y of the Done row. */
    int bottomY() {
        return height / 6 + 168 + 11;
    }

    /** Places {@code entries} in the grid; {@code null} leaves its cell empty. */
    List<AbstractWidget> grid(Object... entries) {
        List<AbstractWidget> added = new ArrayList<>(entries.length);
        for (int i = 0; i < entries.length; i++) {
            added.add(entries[i] == null ? null : addOption(entries[i], cellX(i), cellY(i), 150));
        }
        return added;
    }

    AbstractWidget addOption(Object entry, int x, int y, int w) {
        AbstractWidget widget = switch (entry) {
            case PrOption.Cycle c -> new PrOptionButton(x, y, w, c, this::refreshOptions);
            case PrOption.Slider s -> new PrOptionSlider(x, y, w, s, this::refreshOptions);
            case Vanilla v -> {
                AbstractWidget made = v.option().createButton(minecraft.options, x, y, w);
                made.setTooltip(null);
                vanillaKeys.put(made, v.key());
                yield made;
            }
            default -> throw new IllegalArgumentException("Not an option: " + entry);
        };
        return addRenderableWidget(widget);
    }

    /** Redraws every option label; one option may change what another shows. */
    void refreshOptions() {
        for (GuiEventListener child : children()) {
            if (child instanceof PrOptionControl control) {
                control.refresh();
            }
        }
        onOptionsChanged();
    }

    void onOptionsChanged() {
    }

    /** Right-click on a widget; true when handled. Only the main screen's GUI Scale uses it. */
    boolean onRightClick(AbstractWidget widget) {
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1) {
            AbstractWidget widget = PrTooltips.widgetAt(widgets(), mouseX, mouseY);
            if (widget != null && widget.active && onRightClick(widget)) {
                widget.playDownSound(minecraft.getSoundManager());
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    List<AbstractWidget> widgets() {
        List<AbstractWidget> out = new ArrayList<>();
        for (GuiEventListener child : children()) {
            if (child instanceof AbstractWidget w) {
                out.add(w);
            }
        }
        return out;
    }

    @Override
    protected void clearWidgets() {
        vanillaKeys.clear();
        super.clearWidgets();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 15, 0xFFFFFF);
        tooltips.render(g, mouseX, mouseY, widgets(),
                w -> w instanceof PrOptionControl c ? c.tooltipKey() : vanillaKeys.get(w));
    }

    @Override
    public void removed() {
        minecraft.options.save();
        PryzmaConfig.save();
        super.removed();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
