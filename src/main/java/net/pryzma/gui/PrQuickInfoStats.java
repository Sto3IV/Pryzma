package net.pryzma.gui;

import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.util.concurrent.atomic.AtomicLong;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;

/**
 * Counters behind Quick Info that vanilla does not keep: block entities drawn, heap allocation
 * rate, native image memory and GPU load from timer queries. Minimum FPS and chunk updates come
 * from {@link net.pryzma.perf.PrDebugTracker}, shared with the F3 screen.
 */
public final class PrQuickInfoStats {
    public static final AtomicLong IMAGE_BYTES = new AtomicLong();
    public static int blockEntitiesRendered;

    private static final int QUERIES = 4;
    private static final int[] queries = new int[QUERIES];
    private static final boolean[] pending = new boolean[QUERIES];
    private static int query;
    private static boolean timerQueries = true;
    private static boolean started;

    private static long windowStart;
    private static long lastHeapUsed;
    private static long allocatedInWindow;
    private static double allocationMbPerSecond;
    private static double gpuLoad;
    private static long gpuNsInWindow;

    private PrQuickInfoStats() {
    }

    /** Once per rendered frame, on the render thread, after the frame is drawn. */
    public static void onFrameEnd() {
        long now = System.nanoTime();
        if (windowStart == 0L) {
            windowStart = now;
        }
        Runtime rt = Runtime.getRuntime();
        long used = rt.totalMemory() - rt.freeMemory();
        if (used > lastHeapUsed) {
            allocatedInWindow += used - lastHeapUsed;
        }
        lastHeapUsed = used;
        collectGpu();
        long elapsed = now - windowStart;
        if (elapsed >= 1_000_000_000L) {
            double seconds = elapsed / 1e9;
            double rate = allocatedInWindow / seconds / (1024.0 * 1024.0);
            allocationMbPerSecond = allocationMbPerSecond == 0.0 ? rate : (allocationMbPerSecond * 4.0 + rate) / 5.0;
            gpuLoad = Math.min(1.0, gpuNsInWindow / (double) elapsed);
            windowStart = now;
            allocatedInWindow = 0L;
            gpuNsInWindow = 0L;
        }
    }

    /** Times the GPU work of a frame; results are read a few frames later so nothing stalls. */
    public static void onFrameStart() {
        if (!timerQueries) {
            return;
        }
        try {
            if (queries[0] == 0) {
                GL15.glGenQueries(queries);
            }
            if (!pending[query]) {
                GL15.glBeginQuery(GL33.GL_TIME_ELAPSED, queries[query]);
                pending[query] = true;
                started = true;
            }
        } catch (RuntimeException | LinkageError e) {
            timerQueries = false;
        }
    }

    public static void onFrameRendered() {
        if (timerQueries && started) {
            GL15.glEndQuery(GL33.GL_TIME_ELAPSED);
            started = false;
            query = (query + 1) % QUERIES;
        }
    }

    private static void collectGpu() {
        if (!timerQueries) {
            return;
        }
        for (int i = 0; i < QUERIES; i++) {
            if (pending[i] && i != query && GL15.glGetQueryObjecti(queries[i], GL15.GL_QUERY_RESULT_AVAILABLE) != 0) {
                gpuNsInWindow += GL33.glGetQueryObjecti64(queries[i], GL15.GL_QUERY_RESULT);
                pending[i] = false;
            }
        }
    }

    public static double allocationMbPerSecond() {
        return allocationMbPerSecond;
    }

    /** GPU busy time over wall time, 0..1; 0 when timer queries are unavailable. */
    public static double gpuLoad() {
        return gpuLoad;
    }

    public static long directBytes() {
        for (BufferPoolMXBean pool : ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) {
            if ("direct".equals(pool.getName())) {
                return pool.getMemoryUsed();
            }
        }
        return 0L;
    }
}
