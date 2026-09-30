package net.pryzma.render;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * High-performance 10 Hz render cache for the F3 DebugScreenOverlay.
 *
 * <p>In vanilla Minecraft 1.21.1, the debug screen evaluates 3D terrain noise generators,
 * performs two world raycasts, formats ~80 strings, and recalculates font widths and glyphs
 * on every single frame. At high frame rates (1600+ FPS), this single overlay costs ~1.7 ms
 * of CPU time per frame, capping FPS to ~430.
 *
 * <p>Following OptiFine's proven 100 ms RenderCache architecture, this cache captures the
 * formatted lines and pre-computes their pixel coordinates, skipping raycasts, noise router
 * evaluations, string allocations, and layout scans on intermediate frames, while preserving
 * full native FPS for dynamic frame-time and profiler charts.
 */
public final class PrF3RenderCache {
    /** OptiFine standard update interval: 100 ms (10 updates per second). */
    public static final long CACHE_TTL_MS = 100L;

    /** Background box color: 0x90000000 (-1879048192). */
    private static final int BG_COLOR = 0x90000000;
    /** Default text color: 0xE0E0E0 (14737632). */
    private static final int TEXT_COLOR = 14737632;
    private static final int LINE_HEIGHT = 9;

    public record Entry(String text, int x, int y, int width, int height) {}

    private static long nextUpdateTimeMs = 0L;
    private static long lastRenderTimeMs = 0L;
    private static int lastGuiWidth = -1;
    private static int lastGuiHeight = -1;

    private static final List<Entry> leftEntries = new ArrayList<>();
    private static final List<Entry> rightEntries = new ArrayList<>();

    private PrF3RenderCache() {}

    /**
     * Checks whether the cache is currently valid and fresh.
     */
    public static boolean isCached() {
        long now = System.currentTimeMillis();
        // If F3 has not been drawn for over 200 ms, consider it newly reopened and force fresh data
        if (now - lastRenderTimeMs > 200L) {
            invalidate();
            return false;
        }
        return now < nextUpdateTimeMs;
    }

    /**
     * Records a render tick to detect F3 open/close transitions.
     */
    public static void recordRenderTick() {
        lastRenderTimeMs = System.currentTimeMillis();
    }

    /**
     * Checks and handles window resize / GUI scale invalidation.
     */
    public static void checkResolution(int guiWidth, int guiHeight) {
        if (guiWidth != lastGuiWidth || guiHeight != lastGuiHeight) {
            lastGuiWidth = guiWidth;
            lastGuiHeight = guiHeight;
            invalidate();
        }
    }

    /**
     * Forces cache invalidation so the next frame recomputes all lines.
     */
    public static void invalidate() {
        nextUpdateTimeMs = 0L;
        leftEntries.clear();
        rightEntries.clear();
    }

    /**
     * Attempts to render the left column from cache.
     * @return true if rendered from cache, false if a rebuild is required.
     */
    public static boolean drawLeftCached(GuiGraphics guiGraphics, Font font) {
        checkResolution(guiGraphics.guiWidth(), guiGraphics.guiHeight());
        if (!isCached() || leftEntries.isEmpty()) {
            return false;
        }
        renderEntries(guiGraphics, font, leftEntries);
        return true;
    }

    /**
     * Attempts to render the right column from cache.
     * @return true if rendered from cache, false if a rebuild is required.
     */
    public static boolean drawRightCached(GuiGraphics guiGraphics, Font font) {
        checkResolution(guiGraphics.guiWidth(), guiGraphics.guiHeight());
        if (!isCached() || rightEntries.isEmpty()) {
            return false;
        }
        renderEntries(guiGraphics, font, rightEntries);
        return true;
    }

    /**
     * Caches and renders the left column lines.
     */
    public static void cacheAndRenderLeft(GuiGraphics guiGraphics, Font font, List<String> lines) {
        leftEntries.clear();
        for (int i = 0; i < lines.size(); i++) {
            String s = lines.get(i);
            if (s != null && !s.isEmpty()) {
                int width = font.width(s);
                int x = 2;
                int y = 2 + LINE_HEIGHT * i;
                leftEntries.add(new Entry(s, x, y, width, LINE_HEIGHT));
            }
        }
        renderEntries(guiGraphics, font, leftEntries);
        scheduleNextUpdate();
    }

    /**
     * Caches and renders the right column lines.
     */
    public static void cacheAndRenderRight(GuiGraphics guiGraphics, Font font, List<String> lines) {
        rightEntries.clear();
        int guiWidth = guiGraphics.guiWidth();
        for (int i = 0; i < lines.size(); i++) {
            String s = lines.get(i);
            if (s != null && !s.isEmpty()) {
                int width = font.width(s);
                int x = guiWidth - 2 - width;
                int y = 2 + LINE_HEIGHT * i;
                rightEntries.add(new Entry(s, x, y, width, LINE_HEIGHT));
            }
        }
        renderEntries(guiGraphics, font, rightEntries);
        scheduleNextUpdate();
    }

    private static void scheduleNextUpdate() {
        if (nextUpdateTimeMs <= System.currentTimeMillis()) {
            nextUpdateTimeMs = System.currentTimeMillis() + CACHE_TTL_MS;
        }
    }

    private static void renderEntries(GuiGraphics guiGraphics, Font font, List<Entry> entries) {
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            guiGraphics.fill(e.x - 1, e.y - 1, e.x + e.width + 1, e.y + e.height - 1, BG_COLOR);
            guiGraphics.drawString(font, e.text, e.x, e.y, TEXT_COLOR, false);
        }
    }
}
