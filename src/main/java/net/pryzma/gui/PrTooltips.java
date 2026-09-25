package net.pryzma.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;

/**
 * Pryzma 1.x option tooltips: after the mouse rests on an option for 700 ms, its
 * {@code <key>.tooltip.1..10} lines appear in a fixed 300 x 94 panel above or below the grid.
 */
final class PrTooltips {
    private static final int DELAY_MS = 700;
    private static final int MAX_LINES = 8;
    private static final int PANEL = 0xE0000000;
    private static final int TEXT = 0xDDDDDD;
    private static final int WARNING = 0xFF2020;

    private final Screen screen;
    private int lastX;
    private int lastY;
    private long stillSince;

    PrTooltips(Screen screen) {
        this.screen = screen;
    }

    /** @param keyOf tooltip key of a widget, or {@code null} when it has none */
    void render(GuiGraphics g, int x, int y, List<AbstractWidget> widgets, Function<AbstractWidget, String> keyOf) {
        if (Math.abs(x - lastX) > 5 || Math.abs(y - lastY) > 5) {
            lastX = x;
            lastY = y;
            stillSince = System.currentTimeMillis();
            return;
        }
        if (System.currentTimeMillis() < stillSince + DELAY_MS) {
            return;
        }
        AbstractWidget hovered = widgetAt(widgets, x, y);
        String key = hovered == null ? null : keyOf.apply(hovered);
        List<String> lines = key == null ? List.of() : lines(key);
        if (lines.isEmpty()) {
            return;
        }
        int x1 = screen.width / 2 - 150;
        int y1 = screen.height / 6 - 7;
        if (y <= y1 + 98) {
            y1 += 105;
        }
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 400.0F);
        g.fill(x1, y1, x1 + 300, y1 + 94, PANEL);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            g.drawString(screen.getMinecraft().font, line, x1 + 5, y1 + 5 + i * 11, line.endsWith("!") ? WARNING : TEXT, true);
        }
        g.pose().popPose();
    }

    static List<String> lines(String key) {
        List<String> lines = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            String lineKey = key + ".tooltip." + i;
            if (!I18n.exists(lineKey)) {
                break;
            }
            lines.add(I18n.get(lineKey));
        }
        if (lines.size() > MAX_LINES) {
            lines = new ArrayList<>(lines.subList(0, MAX_LINES));
            lines.set(MAX_LINES - 1, lines.get(MAX_LINES - 1) + " ...");
        }
        return lines;
    }

    /** First visible widget under the point, in insertion order, as 1.x picked it. */
    static AbstractWidget widgetAt(List<AbstractWidget> widgets, double x, double y) {
        for (AbstractWidget w : widgets) {
            if (w.visible && x >= w.getX() && y >= w.getY() && x < w.getX() + w.getWidth() && y < w.getY() + w.getHeight()) {
                return w;
            }
        }
        return null;
    }
}
