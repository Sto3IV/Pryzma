package net.pryzma.render;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4f;

/**
 * The F3 text cache frame state tracker.
 */
public final class PrF3RenderCache {
    /** OptiFine's interval: the text refreshes ten times a second. */
    public static final long TTL_NANOS = 100_000_000L;

    /** Off: vanilla draws the text every frame. The self-test compares the two. */
    public static boolean enabled = true;

    private static final Matrix4f POSE = new Matrix4f();
    private static final List<String> NO_LINES = new ArrayList<>();

    private static boolean replaying;
    private static boolean valid;
    private static boolean eventSkippable;
    /** Columns of the current build: bit 0 left, bit 1 right. */
    private static int built;
    private static long builtAt;
    private static int width = -1;
    private static int height = -1;
    private static int builds;

    private PrF3RenderCache() {
    }

    /**
     * Start of {@code DebugScreenOverlay} frame: decides once per frame whether the text replays.
     */
    public static boolean beginFrame(long now, int guiWidth, int guiHeight, Matrix4f pose) {
        replaying = enabled && valid && eventSkippable && now - builtAt < TTL_NANOS && guiWidth == width
                && guiHeight == height && POSE.equals(pose);
        if (!replaying) {
            valid = false;
            built = 0;
            builtAt = now;
            width = guiWidth;
            height = guiHeight;
            POSE.set(pose);
        }
        return replaying;
    }

    /** This frame replays: the raycasts, the lists and the DebugText event are skipped. */
    public static boolean replaying() {
        return replaying;
    }

    /** Stands in for a skipped list; mutable, like the lists vanilla hands on. */
    public static List<String> noLines() {
        NO_LINES.clear();
        return NO_LINES;
    }

    /**
     * The DebugText hook ran on a build frame. Until it has, no frame replays: skipping the lists
     * without the event would hand its listeners empty lists to edit by index.
     */
    public static void eventHookRan() {
        eventSkippable = true;
    }

    /** The next frame builds: F3 or a chart toggled, the world left, resources reloaded. */
    public static void invalidate() {
        valid = false;
    }

    /** Completed builds since start; the self-test reads the refresh rate from it. */
    public static int builds() {
        return builds;
    }

    /** Both columns of the build are in: the following frames may replay. */
    public static void columnBuilt(boolean left) {
        built |= left ? 1 : 2;
        if (built == 3 && !valid) {
            valid = true;
            builds++;
        }
    }
}
