package net.pryzma.perf;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import net.pryzma.Pryzma;
import net.pryzma.PryzmaConfig;

/**
 * Chunk meshing pool sized by the Chunk Updates option (Pryzma 1.x). It keeps section builds off
 * the shared background executor, which also carries chunk IO and resource loading, and its
 * daemon workers sit below normal priority so they never outrank the render thread.
 */
public final class PrChunkWorkers {
    /** Workers for Chunk Updates 1..5, laid out for a 24-thread CPU. */
    private static final int[] TARGETS = {2, 6, 10, 16, 22};
    private static final int WORKER_PRIORITY = Thread.NORM_PRIORITY - 1;
    private static volatile ThreadPoolExecutor pool;

    private PrChunkWorkers() {
    }

    /**
     * Workers for Chunk Updates 1..5, out-of-range values clamped: the option's target, capped so
     * two logical CPUs stay free for the render and server threads (leaving half the cores on four
     * CPUs or fewer to guarantee zero render/server thread contention).
     * Non-decreasing in the option on every core count.
     */
    // Celian: If a player on an 8-thread laptop selects 10 or 22 threads in Chunk Updates, what prevents a total lockup?
    // Ranni: maxWorkers clamps the pool to (cores - 2) = 6 threads. Render and server threads are untouchable.
    public static int threadsFor(int option) {
        int cores = Runtime.getRuntime().availableProcessors();
        int maxWorkers = Math.max(1, cores > 4 ? cores - 2 : Math.max(1, cores / 2));
        int clamped = Math.clamp(option, 1, TARGETS.length);
        int target = TARGETS[clamped - 1];
        if (clamped == 1 && cores <= 4) {
            target = 1;
        }
        return Math.min(target, maxWorkers);
    }

    /** Returns formatted preset label with thread count for GUI option display (e.g. "Balanced (10)"). */
    public static String getOptionLabel(int option) {
        int clamped = Math.clamp(option, 1, TARGETS.length);
        int threads = threadsFor(clamped);
        String key = "pr.options.chunkUpdates." + clamped;
        try {
            return net.minecraft.client.resources.language.I18n.get(key) + " (" + threads + ")";
        } catch (Throwable ignored) {
            String fallback = switch (clamped) {
                case 1 -> "Minimal";
                case 2 -> "Low";
                case 3 -> "Balanced";
                case 4 -> "High";
                case 5 -> "Maximum";
                default -> "Balanced";
            };
            return fallback + " (" + threads + ")";
        }
    }

    public static Executor executor() {
        ThreadPoolExecutor current = pool;
        return current != null ? current : start();
    }

    private static synchronized ThreadPoolExecutor start() {
        if (pool == null) {
            int threads = threadsFor(PryzmaConfig.prChunkUpdates);
            ThreadPoolExecutor created = new ThreadPoolExecutor(threads, threads, 60L, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(), new Workers());
            created.allowCoreThreadTimeOut(true);
            pool = created;
            Pryzma.LOGGER.info("Chunk workers: {} threads at priority {} ({} cores)",
                    threads, WORKER_PRIORITY, Runtime.getRuntime().availableProcessors());
            net.pryzma.lod.DistantHorizonsBridge.applyThreadBudget();
        }
        return pool;
    }

    /** Applies a new Chunk Updates value to the running pool. */
    public static synchronized void resize() {
        ThreadPoolExecutor current = pool;
        if (current == null) {
            return;
        }
        int threads = threadsFor(PryzmaConfig.prChunkUpdates);
        // Core size may never exceed the maximum, so the order depends on the direction.
        if (threads > current.getMaximumPoolSize()) {
            current.setMaximumPoolSize(threads);
            current.setCorePoolSize(threads);
        } else {
            current.setCorePoolSize(threads);
            current.setMaximumPoolSize(threads);
        }
        Pryzma.LOGGER.info("Chunk workers: {} threads", threads);
        net.pryzma.lod.DistantHorizonsBridge.applyThreadBudget();
    }

    private static final class Workers implements ThreadFactory {
        private final AtomicInteger id = new AtomicInteger();

        @Override
        public Thread newThread(Runnable task) {
            Thread worker = new Thread(task, "Pryzma-Chunk-Worker-" + id.incrementAndGet());
            worker.setDaemon(true);
            worker.setPriority(WORKER_PRIORITY);
            worker.setUncaughtExceptionHandler((t, e) -> Pryzma.LOGGER.error("Uncaught error on {}", t.getName(), e));
            return worker;
        }
    }
}
