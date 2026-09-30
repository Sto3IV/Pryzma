package net.pryzma.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.joml.Matrix4f;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/** The frame decisions of the F3 text cache; the GL side needs a client and is covered by the self-test. */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PrF3RenderCacheTest {
    private static final long MS = 1_000_000L;
    private static final int W = 960;
    private static final int H = 526;
    /** The debug layer's pose: a z offset per GUI layer below it. */
    private static final Matrix4f LAYER = new Matrix4f().translation(0, 0, 1200);

    /** A frame that has to build, with both columns built. */
    private static void build(long now) {
        assertFalse(PrF3RenderCache.beginFrame(now, W, H, LAYER), "expected a build frame");
        PrF3RenderCache.eventHookRan();
        PrF3RenderCache.columnBuilt(true);
        PrF3RenderCache.columnBuilt(false);
    }

    /** First, while no DebugText hook has run in this JVM. */
    @Test
    @Order(1)
    void neverReplaysBeforeTheDebugTextHookRan() {
        PrF3RenderCache.invalidate();
        assertFalse(PrF3RenderCache.beginFrame(0, W, H, LAYER));
        PrF3RenderCache.columnBuilt(true);
        PrF3RenderCache.columnBuilt(false);
        assertFalse(PrF3RenderCache.beginFrame(MS, W, H, LAYER), "listeners would get empty lists");
        PrF3RenderCache.eventHookRan();
        PrF3RenderCache.columnBuilt(true);
        PrF3RenderCache.columnBuilt(false);
        assertTrue(PrF3RenderCache.beginFrame(2 * MS, W, H, LAYER));
    }

    @Test
    void replaysUntilTheTextIsOneHundredMillisecondsOld() {
        long t = 1_000 * MS;
        build(t);
        int builds = PrF3RenderCache.builds();
        assertTrue(PrF3RenderCache.beginFrame(t + 1, W, H, LAYER));
        assertTrue(PrF3RenderCache.replaying());
        assertTrue(PrF3RenderCache.beginFrame(t + PrF3RenderCache.TTL_NANOS - 1, W, H, LAYER));
        assertFalse(PrF3RenderCache.beginFrame(t + PrF3RenderCache.TTL_NANOS, W, H, LAYER));
        assertFalse(PrF3RenderCache.replaying());
        PrF3RenderCache.columnBuilt(true);
        PrF3RenderCache.columnBuilt(false);
        assertEquals(builds + 1, PrF3RenderCache.builds());
        // The age counts from the start of the new build.
        assertTrue(PrF3RenderCache.beginFrame(t + PrF3RenderCache.TTL_NANOS + 50 * MS, W, H, LAYER));
    }

    @Test
    void aColumnMissingFromTheBuildKeepsEveryFrameBuilding() {
        long t = 2_000 * MS;
        assertFalse(PrF3RenderCache.beginFrame(t, W + 1, H, LAYER));
        PrF3RenderCache.columnBuilt(true);
        assertFalse(PrF3RenderCache.beginFrame(t + MS, W + 1, H, LAYER), "the right column was never built");
        PrF3RenderCache.columnBuilt(false);
        assertFalse(PrF3RenderCache.beginFrame(t + 2 * MS, W + 1, H, LAYER), "the bits are per build, not cumulative");
    }

    @Test
    void guiSizeAndLayerPoseRebuild() {
        long t = 3_000 * MS;
        build(t);
        assertFalse(PrF3RenderCache.beginFrame(t + MS, W - 1, H, LAYER), "the right column's x follows the width");
        build(t + 2 * MS);
        assertFalse(PrF3RenderCache.beginFrame(t + 3 * MS, W, H + 1, LAYER));
        build(t + 4 * MS);
        assertFalse(PrF3RenderCache.beginFrame(t + 5 * MS, W, H, new Matrix4f(LAYER).translate(0, 0, 200)),
                "a layer added below the debug screen moves its z");
        build(t + 6 * MS);
        assertTrue(PrF3RenderCache.beginFrame(t + 7 * MS, W, H, new Matrix4f(LAYER)), "an equal pose replays");
    }

    @Test
    void invalidateBuildsTheNextFrame() {
        long t = 4_000 * MS;
        build(t);
        PrF3RenderCache.invalidate();
        assertFalse(PrF3RenderCache.beginFrame(t + MS, W, H, LAYER));
    }

    @Test
    void disabledNeverReplays() {
        long t = 5_000 * MS;
        build(t);
        PrF3RenderCache.enabled = false;
        try {
            assertFalse(PrF3RenderCache.beginFrame(t + MS, W, H, LAYER));
        } finally {
            PrF3RenderCache.enabled = true;
        }
        assertFalse(PrF3RenderCache.beginFrame(t + 2 * MS, W, H, LAYER), "re-enabled: builds before it replays");
    }

    @Test
    void skippedListsAreEmptyAndStayEditable() {
        List<String> lines = PrF3RenderCache.noLines();
        lines.add("added by a wrapper");
        assertTrue(PrF3RenderCache.noLines().isEmpty());
    }
}
