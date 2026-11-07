package net.pryzma.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.pryzma.PryzmaConfig;

/**
 * CloudRenderer optimization and Infinite Clouds expansion coordinator.
 */
public final class PrCloudRenderer {
    public static final float WALL_EPSILON = 0.02F;
    public static final int NEAR_CELL_MIN = -16;
    public static final int NEAR_CELL_MAX = 16;
    public static final int FAR_CELL_RADIUS = 48;
    public static final int FAR_CELL_RADIUS_SQ = FAR_CELL_RADIUS * FAR_CELL_RADIUS;

    private static volatile boolean dirty = false;
    private static float savedFogStart;
    private static float savedFogEnd;
    private static boolean fogExtended = false;

    private PrCloudRenderer() {
    }

    public static void markDirty() {
        dirty = true;
    }

    public static boolean consumeDirty() {
        boolean was = dirty;
        dirty = false;
        return was;
    }

    public static void beforeRenderClouds() {
    }

    public static void afterRenderClouds() {
    }
}
