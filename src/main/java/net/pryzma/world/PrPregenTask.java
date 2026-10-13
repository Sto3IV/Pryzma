package net.pryzma.world;

import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.visitors.CollectFields;
import net.minecraft.nbt.visitors.FieldSelector;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ChunkTaskPriorityQueueSorter;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.pryzma.lod.LodMath;
import net.pryzma.lod.PryzmaLod;

/**
 * Native, tick-budgeted world pregeneration task for Pryzma.
 *
 * <p>Generates chunks in an outward circular spiral around the center point, feeding finished surface
 * data directly into {@link PryzmaLod#injectChunk} in real time without disk I/O bottlenecks.
 */
public final class PrPregenTask {
    private static final Logger LOGGER = LoggerFactory.getLogger("Pryzma-Pregen");
    private static final int BATCH_SIZE = 32;
    private static final int QUEUE_THRESHOLD = 8;
    private static final TicketType<ChunkPos> PREGEN_TICKET =
            TicketType.create("pryzma_pregen", Comparator.comparingLong(ChunkPos::toLong));

    private static volatile PrPregenTask activeTask;

    public enum Mode {
        FULL,
        LOD
    }

    private final CommandSourceStack source;
    private final MinecraftServer server;
    private final ServerChunkCache chunkSource;
    private final ServerLevel serverLevel;
    private final int originX;
    private final int originZ;
    private final int radius;
    private final Mode mode;

    private final int[] spiral;
    private final int totalCount;
    private int cursor;

    private final Object queueLock = new Object();
    private final AtomicInteger queuedCount = new AtomicInteger();
    private final AtomicInteger okCount = new AtomicInteger();
    private final AtomicInteger errorCount = new AtomicInteger();
    private final AtomicInteger skippedCount = new AtomicInteger();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile boolean stopped;

    private long startTime;
    private long lastReportTime;
    private int lastReportChunks;
    private int lastReportMilestone = -1;

    private PrPregenTask(CommandSourceStack source, ServerLevel serverLevel, int originX, int originZ, int radius, Mode mode) {
        this.source = source;
        this.serverLevel = serverLevel;
        this.server = serverLevel.getServer();
        this.chunkSource = serverLevel.getChunkSource();
        this.originX = originX;
        this.originZ = originZ;
        this.radius = radius;
        this.mode = mode;

        this.spiral = LodMath.spiral(radius);
        this.totalCount = this.spiral.length / 2;
    }

    public static synchronized void start(CommandSourceStack source, ServerLevel level, int originX, int originZ, int radius, Mode mode) {
        if (activeTask != null && activeTask.isRunning()) {
            source.sendFailure(Component.translatable("pryzma.command.pregen.already_running"));
            return;
        }

        activeTask = new PrPregenTask(source, level, originX, originZ, radius, mode);
        source.sendSuccess(() -> Component.translatable(
                "pryzma.command.pregen.started",
                radius, radius * 16, mode.name().toLowerCase(), originX, originZ, activeTask.totalCount), true);

        activeTask.run();
    }

    public static synchronized void stop(CommandSourceStack source) {
        if (activeTask != null && activeTask.isRunning()) {
            activeTask.stop();
            activeTask = null;
        } else {
            source.sendFailure(Component.translatable("pryzma.command.pregen.not_running"));
        }
    }

    public static synchronized void status(CommandSourceStack source) {
        if (activeTask != null && activeTask.isRunning()) {
            activeTask.reportStatus(source);
        } else {
            source.sendSuccess(() -> Component.translatable("pryzma.command.pregen.not_running"), false);
        }
    }

    public static synchronized void stopAll() {
        if (activeTask != null) {
            activeTask.stop();
            activeTask = null;
        }
    }

    public boolean isRunning() {
        return running.get() && !stopped;
    }

    private void run() {
        this.running.set(true);
        this.startTime = System.currentTimeMillis();
        this.lastReportTime = this.startTime;
        this.lastReportChunks = 0;

        CompletableFuture.runAsync(this::tryEnqueueTasks, Util.backgroundExecutor());
    }

    public void stop() {
        synchronized (queueLock) {
            stopped = true;
            running.set(false);
        }
        int done = okCount.get() + errorCount.get() + skippedCount.get();
        double percent = totalCount > 0 ? (double) done / totalCount * 100.0 : 0.0;
        broadcastMessage(Component.translatable("pryzma.command.pregen.stopped",
                done, totalCount, String.format(Locale.ROOT, "%.1f", percent)));
    }

    public void reportStatus(CommandSourceStack requester) {
        int done = okCount.get() + errorCount.get() + skippedCount.get();
        double percent = totalCount > 0 ? (double) done / totalCount * 100.0 : 0.0;
        long elapsedSec = Math.max(1, (System.currentTimeMillis() - startTime) / 1000L);
        int cps = (int) (done / elapsedSec);
        long remaining = Math.max(0, totalCount - done);
        long remainingSecs = cps > 0 ? remaining / cps : 0;

        requester.sendSuccess(() -> Component.translatable(
                "pryzma.command.pregen.status",
                done, totalCount, String.format(Locale.ROOT, "%.1f", percent), mode.name().toLowerCase(), cps,
                formatTime(elapsedSec), formatTime(remainingSecs), errorCount.get()), false);
    }

    private void tryEnqueueTasks() {
        synchronized (queueLock) {
            if (stopped) {
                return;
            }

            int enqueueCount = BATCH_SIZE - queuedCount.get();
            if (enqueueCount <= 0) {
                return;
            }

            LongList chunks = collectChunks(enqueueCount);
            if (chunks.isEmpty()) {
                if (cursor >= spiral.length && queuedCount.get() == 0) {
                    onComplete();
                    stopped = true;
                    running.set(false);
                }
                return;
            }

            queuedCount.getAndAdd(chunks.size());
            server.submit(() -> enqueueChunks(chunks));
        }
    }

    private LongList collectChunks(int count) {
        LongList chunks = new LongArrayList(count);
        while (cursor < spiral.length && chunks.size() < count) {
            int dx = spiral[cursor++];
            int dz = spiral[cursor++];
            int cx = originX + dx;
            int cz = originZ + dz;
            ChunkPos pos = new ChunkPos(cx, cz);
            if (isChunkFullyGenerated(pos)) {
                skippedCount.incrementAndGet();
                PryzmaLod.unmarkMissing(cx, cz);
                onProgressUpdate(false);
                continue;
            }
            chunks.add(pos.toLong());
        }
        return chunks;
    }

    private void enqueueChunks(LongList chunks) {
        if (stopped) {
            return;
        }

        for (int i = 0; i < chunks.size(); i++) {
            long chunkPos = chunks.getLong(i);
            acquireChunk(chunkPos);
        }

        chunkSource.tick(() -> false, true);

        ChunkMap chunkMap = chunkSource.chunkMap;
        ChunkStatus targetStatus = mode == Mode.LOD ? ChunkStatus.SURFACE : ChunkStatus.FULL;

        for (int i = 0; i < chunks.size(); i++) {
            long chunkLongPos = chunks.getLong(i);
            ChunkHolder holder = chunkMap.getVisibleChunkIfPresent(chunkLongPos);
            if (holder == null) {
                acceptChunkResult(chunkLongPos, ChunkHolder.UNLOADED_CHUNK);
                continue;
            }

            holder.scheduleChunkGenerationTask(targetStatus, chunkMap).whenCompleteAsync((result, throwable) -> {
                if (throwable == null) {
                    acceptChunkResult(chunkLongPos, result);
                } else {
                    LOGGER.warn("Pryzma pregen: chunk [{}, {}] error",
                            ChunkPos.getX(chunkLongPos), ChunkPos.getZ(chunkLongPos), throwable);
                    acceptChunkResult(chunkLongPos, ChunkHolder.UNLOADED_CHUNK);
                }
            }, runnable -> chunkMap.scheduleOnMainThreadMailbox(ChunkTaskPriorityQueueSorter.message(holder, runnable)));
        }
    }

    private void acceptChunkResult(long chunk, ChunkResult<ChunkAccess> result) {
        server.submit(() -> releaseChunk(chunk));

        if (result != null && result.isSuccess()) {
            okCount.incrementAndGet();
            ChunkAccess chunkAccess = result.orElse(null);
            if (chunkAccess != null) {
                PryzmaLod.injectChunk(chunkAccess, serverLevel, serverLevel.dimensionType().hasSkyLight(), serverLevel.getLightEngine());
                PryzmaLod.unmarkMissing(chunkAccess.getPos().x, chunkAccess.getPos().z);
            }
        } else {
            errorCount.incrementAndGet();
        }

        onProgressUpdate(false);

        int remaining = queuedCount.decrementAndGet();
        if (remaining <= QUEUE_THRESHOLD && !stopped) {
            CompletableFuture.runAsync(this::tryEnqueueTasks, Util.backgroundExecutor());
        }

        if (cursor >= spiral.length && remaining == 0 && !stopped) {
            onComplete();
            stopped = true;
            running.set(false);
        }

        if (((okCount.get() + errorCount.get()) % 1000) == 999) {
            server.submit(() -> serverLevel.save(null, false, true));
        }
    }

    private void onProgressUpdate(boolean force) {
        int done = okCount.get() + errorCount.get() + skippedCount.get();
        long now = System.currentTimeMillis();
        boolean timeInterval = (now - lastReportTime) >= 5000L;
        int currentPercent = totalCount > 0 ? (int) ((double) done / totalCount * 10.0) : 0;
        boolean percentMilestone = currentPercent > lastReportMilestone && currentPercent <= 10;

        if (force || timeInterval || percentMilestone) {
            lastReportMilestone = currentPercent;
            long dt = now - lastReportTime;
            int dChunks = done - lastReportChunks;
            lastReportTime = now;
            lastReportChunks = done;

            int cps = dt > 0 ? (int) (dChunks * 1000.0 / dt) : 0;
            double percent = totalCount > 0 ? (double) done / totalCount * 100.0 : 100.0;
            long remaining = Math.max(0, totalCount - done);
            long remainingSecs = cps > 0 ? remaining / cps : 0;
            Component eta = formatTime(remainingSecs);

            Component msg = Component.translatable("pryzma.command.pregen.progress",
                    done, totalCount, String.format(Locale.ROOT, "%.1f", percent), cps, eta);
            broadcastMessage(msg);
        }
    }

    private void onComplete() {
        long elapsedSec = Math.max(1, (System.currentTimeMillis() - startTime) / 1000L);
        int ok = okCount.get();
        int skipped = skippedCount.get();
        int err = errorCount.get();
        int avgRate = (int) ((ok + skipped + err) / elapsedSec);

        Component msg = Component.translatable("pryzma.command.pregen.completed",
                ok, skipped, err, formatTime(elapsedSec), avgRate);
        broadcastMessage(msg);

        server.submit(() -> serverLevel.save(null, false, true));
    }

    private void acquireChunk(long chunk) {
        ChunkPos pos = new ChunkPos(chunk);
        chunkSource.addRegionTicket(PREGEN_TICKET, pos, 0, pos);
    }

    private void releaseChunk(long chunk) {
        ChunkPos pos = new ChunkPos(chunk);
        chunkSource.removeRegionTicket(PREGEN_TICKET, pos, 0, pos);
    }

    private boolean isChunkFullyGenerated(ChunkPos pos) {
        CollectFields collectFields = new CollectFields(new FieldSelector(StringTag.TYPE, "Status"));
        chunkSource.chunkScanner().scanChunk(pos, collectFields).join();

        if (collectFields.getResult() instanceof CompoundTag compoundTag) {
            String status = compoundTag.getString("Status");
            if (mode == Mode.LOD) {
                return "minecraft:full".equals(status) || "minecraft:surface".equals(status)
                        || "minecraft:carvers".equals(status) || "minecraft:features".equals(status)
                        || "minecraft:initialize_light".equals(status) || "minecraft:light".equals(status)
                        || "minecraft:spawn".equals(status);
            }
            return "minecraft:full".equals(status);
        }

        return false;
    }

    private void broadcastMessage(Component comp) {
        if (server.isSingleplayer()) {
            if (source != null) {
                source.sendSuccess(() -> comp, false);
            }
        } else {
            server.getPlayerList().broadcastSystemMessage(comp, false);
        }
    }

    private static Component formatTime(long seconds) {
        if (seconds < 60) {
            return Component.translatable("pryzma.command.time.seconds", seconds);
        }
        long minutes = seconds / 60;
        long secs = seconds % 60;
        if (minutes < 60) {
            return Component.translatable("pryzma.command.time.minutes_seconds", minutes, String.format(Locale.ROOT, "%02d", secs));
        }
        long hours = minutes / 60;
        long mins = minutes % 60;
        return Component.translatable("pryzma.command.time.hours_minutes_seconds", hours, String.format(Locale.ROOT, "%02d", mins), String.format(Locale.ROOT, "%02d", secs));
    }
}
