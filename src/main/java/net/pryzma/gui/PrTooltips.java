package net.pryzma.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;

/**
 * Pryzma 1.x option tooltips: after the mouse rests on an option for 700 ms, its
 * {@code <key>.tooltip.1..10} lines appear in a fixed 300 x 94 panel above or below the grid.
 */
final class PrTooltips {
    private static final int DELAY_MS = System.getProperty("pryzma.selftest") != null ? 0 : 700;
    private static final int MAX_LINES = 10;
    private static final int PANEL = 0xE0000000;
    private static final int TEXT = 0xFFDDDDDD;
    private static final int WARNING = 0xFFFF2020;

    private final Screen screen;
    private int lastX;
    private int lastY;
    private long stillSince;

    PrTooltips(Screen screen) {
        this.screen = screen;
    }

    /** @param keyOf tooltip key of a widget, or {@code null} when it has none */
    void render(GuiGraphicsExtractor g, int x, int y, List<AbstractWidget> widgets, Function<AbstractWidget, String> keyOf) {
        if (DELAY_MS > 0 && (Math.abs(x - lastX) > 5 || Math.abs(y - lastY) > 5)) {
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
        List<String> rawLines = key == null ? List.of() : lines(key);
        if (rawLines.isEmpty()) {
            return;
        }

        net.minecraft.client.gui.Font font = screen.getMinecraft().font;
        int maxAllowedLineWidth = Math.max(200, Math.min(320, screen.width - 32));
        List<String> lines = wrapLines(font, rawLines, maxAllowedLineWidth);
        if (lines.size() > MAX_LINES) {
            lines = new ArrayList<>(lines.subList(0, MAX_LINES));
            lines.set(MAX_LINES - 1, lines.get(MAX_LINES - 1) + " ...");
        }

        int maxTextWidth = 0;
        for (String line : lines) {
            maxTextWidth = Math.max(maxTextWidth, font.width(line));
        }
        int panelWidth = Math.min(Math.max(200, maxTextWidth + 14), screen.width - 16);
        int panelHeight = 8 + lines.size() * 11;
        int x1 = Math.max(8, Math.min(screen.width / 2 - panelWidth / 2, screen.width - panelWidth - 8));
        
        // Position panel at top or bottom half opposite to the hovered cursor so it never covers the button
        int y1;
        if (y < screen.height / 2) {
            y1 = screen.height - panelHeight - 6;
        } else {
            y1 = 6;
        }
        y1 = Math.max(4, Math.min(y1, screen.height - panelHeight - 4));

        g.nextStratum();
        g.fill(x1, y1, x1 + panelWidth, y1 + panelHeight, PANEL);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (!line.isEmpty()) {
                g.text(font, line, x1 + 7, y1 + 5 + i * 11, line.endsWith("!") ? WARNING : TEXT, true);
            }
        }
    }

    private static List<String> wrapLines(net.minecraft.client.gui.Font font, List<String> rawLines, int maxLineWidth) {
        List<String> wrapped = new ArrayList<>();
        for (String raw : rawLines) {
            if (raw.isEmpty()) {
                wrapped.add("");
                continue;
            }
            if (font.width(raw) <= maxLineWidth) {
                wrapped.add(raw);
                continue;
            }
            String[] words = raw.split(" ");
            StringBuilder sb = new StringBuilder();
            for (String word : words) {
                if (sb.length() == 0) {
                    sb.append(word);
                } else {
                    String test = sb + " " + word;
                    if (font.width(test) <= maxLineWidth) {
                        sb.append(" ").append(word);
                    } else {
                        wrapped.add(sb.toString());
                        sb = new StringBuilder(word);
                    }
                }
            }
            if (sb.length() > 0) {
                wrapped.add(sb.toString());
            }
        }
        return wrapped;
    }

    static List<String> lines(String key) {
        List<String> lines = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            String lineKey = key + ".tooltip." + i;
            if (!net.minecraft.locale.Language.getInstance().has(lineKey)) {
                break;
            }
            lines.add(I18n.get(lineKey));
        }
        if (lines.isEmpty()) {
            String singleKey = key + ".tooltip";
            if (net.minecraft.locale.Language.getInstance().has(singleKey)) {
                for (String part : I18n.get(singleKey).split("\n")) {
                    String trimmed = part.stripTrailing();
                    if (!trimmed.isEmpty()) {
                        lines.add(trimmed);
                    }
                }
            }
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
