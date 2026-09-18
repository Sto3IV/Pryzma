package net.pryzma.lod.exec;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Dedicated background worker pool for LOD processing, disk I/O, and mesh generation.
 * <p>
 * Strictly isolated from {@code PryzmaChunkExecutor}. All threads execute at
 * {@code Thread.MIN_PRIORITY} as daemon threads to never steal CPU time from
 * vanilla chunk meshing or the main render thread.
 */
public final class PryzmaLodExecutor {
    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger(1);
    private static volatile ExecutorService executor;

    public static synchronized ExecutorService getExecutor() {
        if (executor == null || executor.isShutdown()) {
            ThreadFactory factory = r -> {
                Thread t = new Thread(r, "Pryzma-LOD-Worker-" + THREAD_COUNTER.getAndIncrement());
                t.setDaemon(true);
                t.setPriority(Thread.MIN_PRIORITY);
                return t;
            };
            // Default: 2 background threads (1 for chunk intake/downsampling, 1 for greedy meshing)
            executor = Executors.newFixedThreadPool(2, factory);
        }
        return executor;
    }

    public static void execute(Runnable task) {
        if (task != null) {
            getExecutor().execute(task);
        }
    }

    public static synchronized void shutdown() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
            executor = null;
        }
    }
}
