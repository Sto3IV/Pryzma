package net.pryzma.util;

/*
 * Ranni: I'm not sure why this works, but it does. Don't touch it.
 */

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dedicated worker pool for chunk meshing, isolated from {@code Util.backgroundExecutor()}.
 * The shared background pool also carries chunk IO, structure loading, texture stitching and
 * mod work; meshing queued behind those stalls the section dispatcher. Call sites are injected
 * by {@code net.pryzma.neoforge.LevelRendererTransformer}.
 *
 * <p>Workers are daemon threads below normal priority, so they never outrank the render or
 * main thread and never hold the JVM open.
 */
public final class PryzmaChunkExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger("Pryzma");

    public static final String WORKER_PREFIX = "Pryzma-Chunk-Worker-";
    public static final int WORKER_PRIORITY = Thread.NORM_PRIORITY - 2;

    private static final int MIN_THREADS = 1;
    private static final int MAX_THREADS = 10;
    private static final long KEEP_ALIVE_SECONDS = 60L;

    private static volatile ThreadPoolExecutor executor;

    private PryzmaChunkExecutor() {
    }

    /**
     * A third of the cores, or all but six, whichever is larger, clamped to [1, 10]. Small
     * machines keep a worker, mid-range machines keep headroom for the render and main threads,
     * and large machines stop before the meshing frontend becomes the bottleneck.
     */
    public static int getOptimalThreadCount() {
        int cores = Runtime.getRuntime().availableProcessors();
        return Math.clamp(Math.max(cores / 3, cores - 6), MIN_THREADS, MAX_THREADS);
    }

    /** Returns configured target pool size, or optimal count if not started yet. */
    public static int getWorkerCount() {
        ThreadPoolExecutor current = executor;
        return current != null ? current.getCorePoolSize() : getOptimalThreadCount();
    }

    /**
     * Calculates the target worker thread count for a given Chunk Updates preset (1..5),
     * ensuring strict monotonic scaling across all CPU topologies without exceeding safe limits.
     */
    public static int getThreadsForOption(int option) {
        int cores = Runtime.getRuntime().availableProcessors();
        int t1 = cores > 4 ? Math.max(1, Math.min(2, cores / 4)) : 1;
        int t2 = Math.max(t1, Math.min(cores / 2, 4));
        int t3 = Math.max(t2, getOptimalThreadCount());
        int t4 = Math.max(t3, Math.min(cores - 1, Math.min(12, Math.max(cores / 2, t3 + (cores >= 8 ? 2 : 1)))));
        int t5 = Math.max(t4, Math.min(cores - 1, 16));
        return switch (option) {
            case 1 -> t1;
            case 2 -> t2;
            case 3 -> t3;
            case 4 -> t4;
            case 5 -> t5;
            default -> t3;
        };
    }

    /**
     * Dynamically adjusts thread pool size according to the Chunk Updates setting (1..5),
     * matching Sodium's chunkBuilderThreads paradigm without requiring restart.
     */
    public static synchronized void applyChunkUpdatesOption(int option) {
        int targetThreads = getThreadsForOption(option);
        ThreadPoolExecutor current = executor;
        if (current != null && !current.isShutdown()) {
            if (targetThreads > current.getMaximumPoolSize()) {
                current.setMaximumPoolSize(targetThreads);
                current.setCorePoolSize(targetThreads);
            } else {
                current.setCorePoolSize(targetThreads);
                current.setMaximumPoolSize(targetThreads);
            }
            LOGGER.info("Pryzma chunk worker pool adjusted: {} threads (option {})", targetThreads, option);
        }
    }

    /** The meshing pool, started on first use and restarted after {@link #shutdown()}. */
    public static ExecutorService getExecutor() {
        ThreadPoolExecutor current = executor;
        return current != null && !current.isShutdown() ? current : start();
    }

    private static synchronized ThreadPoolExecutor start() {
        ThreadPoolExecutor current = executor;
        if (current != null && !current.isShutdown()) {
            return current;
        }
        int threads = getOptimalThreadCount();
        ThreadPoolExecutor pool = new ThreadPoolExecutor(threads, threads,
                KEEP_ALIVE_SECONDS, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), new WorkerFactory());
        pool.allowCoreThreadTimeOut(true);
        executor = pool;
        LOGGER.info("Pryzma chunk worker pool: {} threads at priority {} ({} cores)",
                threads, WORKER_PRIORITY, Runtime.getRuntime().availableProcessors());
        return pool;
    }

    /**
     * Drops the pool; queued meshing is discarded and the next {@link #getExecutor()} builds a
     * fresh one. Workers are daemons, so this is optional at JVM exit.
     */
    public static synchronized void shutdown() {
        ThreadPoolExecutor current = executor;
        executor = null;
        if (current != null) {
            current.shutdownNow();
        }
    }

    private static final class WorkerFactory implements ThreadFactory {
        private final AtomicInteger id = new AtomicInteger();

        @Override
        public Thread newThread(Runnable task) {
            Thread worker = new Thread(task, WORKER_PREFIX + id.incrementAndGet());
            worker.setDaemon(true);
            worker.setPriority(WORKER_PRIORITY);
            worker.setUncaughtExceptionHandler(
                    (t, e) -> LOGGER.error("Uncaught error on {}", t.getName(), e));
            return worker;
        }
    }
}
