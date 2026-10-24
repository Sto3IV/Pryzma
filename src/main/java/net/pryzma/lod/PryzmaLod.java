package net.pryzma.lod;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import it.unimi.dsi.fastutil.longs.Long2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.pryzma.Pryzma;
import net.pryzma.PryzmaConfig;
import net.pryzma.render.PrRenderHooks;
import net.pryzma.shader.PryzmaShaders;
import net.pryzma.shader.pipeline.ShadedWorldRenderingPipeline;

/**
 * Farlands LOD, the native distant terrain: wiring and scheduling.
 *
 * <ul>
 * <li>Data: a chunk is captured ({@link LodWorldScanner}) {@value #LOAD_CAPTURE_DELAY_TICKS} ticks after it loads,
 * once its light has arrived, and again when it unloads, so the LOD keeps its last state. In singleplayer,
 * {@link LodStorageIntake} reads chunks the client never loaded from the saved world, and pregeneration
 * ({@link #injectChunk}) hands over chunks as they are generated.</li>
 * <li>Meshes: every client tick, sectors within the LOD distance whose data, detail level or palette changed are
 * meshed ({@link NativeLodMesher}) on the LOD workers, nearest first. A sector rebuilt for new data waits
 * {@value #MIN_REMESH_TICKS} ticks between builds, so streaming data does not mesh it over and over. A resource
 * reload rebuilds the palette, re-tints every stored chunk and so rebuilds every mesh.</li>
 * <li>Drawing: finished meshes are uploaded within a per-frame budget. Without a shader pack
 * {@link NativeLodRenderer} draws them after the sky, and the vanilla terrain fog moves out to the LOD's fog. Under a
 * shader pack {@link LodShaderSupport} draws them into the pack's G-buffer: the opaque LOD after the sky, its water
 * after the translucent terrain.</li>
 * </ul>
 * Off means off: with the option at Off, or in a dimension with a ceiling, nothing is captured, kept or drawn, and
 * no listener but the client tick (which notices the option) is registered. Client thread, except the worker tasks
 * and {@link #injectChunk}.
 */
public final class PryzmaLod {
    private static final int LOAD_CAPTURE_DELAY_TICKS = 40;
    private static final long LOAD_CAPTURE_BUDGET_NANOS = 2_000_000L;
    private static final int MIN_REMESH_TICKS = 40;
    private static final int EVICT_INTERVAL_TICKS = 200;
    private static final int UPLOADS_PER_FRAME = 4;
    private static final long UPLOAD_BYTES_PER_FRAME = 8L << 20;
    private static final int UPLOADS_PER_TICK = 16;
    /** Stored chunks re-tinted per worker task after a resource reload. */
    private static final int RETINT_BATCH = 64;
    private static final int MAX_LOGGED_FAILURES = 5;
    private static final int WORKER_THREADS = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 4));
    private static final int MAX_MESH_JOBS = WORKER_THREADS * 2;
    private static final ThreadPoolExecutor WORKERS = createWorkers();

    private static final LodStorageIntake INTAKE = new LodStorageIntake();
    private static final ConcurrentLinkedQueue<MeshResult> COMPLETED = new ConcurrentLinkedQueue<>();
    private static final LongOpenHashSet IN_FLIGHT = new LongOpenHashSet();
    /** Chunk key to the tick its delayed capture is due, in due order. */
    private static final Long2LongLinkedOpenHashMap PENDING_LOADS = new Long2LongLinkedOpenHashMap();
    private static final List<Candidate> CANDIDATES = new ArrayList<>();
    private static final Comparator<Candidate> NEAR_FIRST = Comparator.comparingDouble(Candidate::distance);
    private static final AtomicInteger FAILURES = new AtomicInteger();
    private static final AtomicLong CAPTURE_SEQUENCE = new AtomicLong();

    // Listeners that only exist while the LOD runs: with the option off, chunk events and frames never reach it.
    private static final Consumer<ChunkEvent.Load> CHUNK_LOAD = PryzmaLod::onChunkLoad;
    private static final Consumer<ChunkEvent.Unload> CHUNK_UNLOAD = PryzmaLod::onChunkUnload;
    private static final Consumer<LevelEvent.Unload> LEVEL_UNLOAD = PryzmaLod::onLevelUnload;
    private static final Consumer<RenderFrameEvent.Pre> FRAME_START = event -> frameActive = false;
    private static final Consumer<RenderLevelStageEvent> RENDER_STAGE = PryzmaLod::onRenderStage;

    private static volatile LodDataStore store = new LodDataStore();
    private static IEventBus gameBus;
    private static boolean listening;
    private static ClientLevel trackedLevel;
    private static volatile boolean running;
    private static long tick;
    private static int tintedGeneration;
    private static boolean frameActive;
    private static float frameFogStart;
    private static float frameFogEnd;

    private PryzmaLod() {
    }

    /** A finished mesh job, handed from a worker to the render thread. {@code mesh} is null when meshing failed. */
    private record MeshResult(LodDataStore store, long key, int level, int version, int colorGeneration, int colorEpoch,
            boolean fallbackColors, LodMesh mesh) {
    }

    private record Candidate(int sectorX, int sectorZ, int level, double distance) {
    }

    /**
     * Registers the client tick listener, the only one that exists while the option is off. The mod bus is not used:
     * the LOD's core shader is built on first use ({@link NativeLodRenderer}), not with the game's shaders.
     */
    public static void register(IEventBus modBus, IEventBus gameBus) {
        PryzmaLod.gameBus = gameBus;
        gameBus.addListener(ClientTickEvent.Post.class, PryzmaLod::onClientTick);
    }

    /** Whether the Farlands LOD option is on and not blocked by Distant Horizons. */
    public static boolean enabled() {
        return PryzmaConfig.isFarlandsActive();
    }

    /** Whether the LOD system is actively running with listeners registered. */
    public static boolean isRunning() {
        return running;
    }

    /** Whether the LOD drew this frame; the terrain fog of this frame then follows {@link #fogStart}/{@link #fogEnd}. */
    public static boolean isActiveThisFrame() {
        return frameActive;
    }

    public static float fogStart() {
        if (!running) {
            return 0.0F;
        }
        int radiusBlocks = PryzmaConfig.prFarlandsDistance << 4;
        return LodMath.fogRange(radiusBlocks, PryzmaConfig.prFogStart, PryzmaConfig.isFogOff(), PryzmaConfig.prFogType)[0];
    }

    public static float fogEnd() {
        if (!running) {
            return 0.0F;
        }
        int radiusBlocks = PryzmaConfig.prFarlandsDistance << 4;
        return LodMath.fogRange(radiusBlocks, PryzmaConfig.prFogStart, PryzmaConfig.isFogOff(), PryzmaConfig.prFogType)[1];
    }

    /**
     * The Farlands LOD option changed. Client thread. Turning it off frees everything at once; switching it on or off
     * under a shader pack reloads the pack, whose programs, macros and uniforms depend on it.
     */
    public static void onSettingsChanged() {
        boolean enabled = enabled();
        if (!enabled) {
            stop();
        }
        // A shader pipeline carries Farlands support exactly when it was built with the option on.
        if (PryzmaShaders.isPackInUseQuick() && (shaderSupport() != null) != enabled) {
            try {
                PryzmaShaders.reload();
            } catch (IOException e) {
                Pryzma.LOGGER.error("Farlands LOD: could not reload the shader pack after the option changed", e);
            }
        }
    }

    /** A resource reload finished applying: models and textures, and with them the palette, are new. */
    public static void onResourcesReloaded() {
        NativeLodRenderer.onResourcesReloaded();
        LodBlockColors.onResourcesReloaded();
    }

    /** The F3 line, or null while the option is off. */
    @Nullable
    public static String debugLine() {
        if (!enabled()) {
            return null;
        }
        if (!running) {
            return "Farlands: " + PryzmaConfig.prFarlandsDistance + " chunks, idle";
        }
        return String.format(Locale.ROOT, "Farlands: %d chunks, data %d, sectors %d/%d, %.1fK quads, %d MB, jobs %d, reads %d%s",
                PryzmaConfig.prFarlandsDistance, store.size(), NativeLodRenderer.drawnLastFrame(), NativeLodRenderer.meshCount(),
                NativeLodRenderer.quadCount() / 1000.0, NativeLodRenderer.gpuBytes() >> 20, IN_FLIGHT.size(), INTAKE.outstanding(),
                shaderMode());
    }

    /** How the LOD is drawn under the active shader pack, for the F3 line. */
    private static String shaderMode() {
        LodShaderSupport support = shaderSupport();
        if (support == null) {
            return PryzmaShaders.isPackInUseQuick() ? ", shader pack: not drawn" : "";
        }
        if (!support.canDraw()) {
            return ", shader pack: programs failed";
        }
        return support.usesDistantHorizonsPrograms() ? ", shader pack: dh programs" : ", shader pack: gbuffers fallback";
    }

    /**
     * Injects a chunk into the LOD store from server pregeneration. Any thread: the surface is read where the chunk
     * lives, block colours are only queued for the render thread, ids and tints are resolved on a worker.
     */
    public static void injectChunk(ChunkAccess chunk, LevelHeightAccessor level, boolean skyLit, @Nullable LevelLightEngine light) {
        LodDataStore target = store;
        ClientLevel clientLevel = trackedLevel;
        if (!running || clientLevel == null) {
            return;
        }
        LodWorldScanner.RawChunk raw = LodWorldScanner.captureChunk(level, skyLit, light, chunk, CAPTURE_SEQUENCE.incrementAndGet());
        LodWorldScanner.Finisher finisher = LodWorldScanner.finisher(clientLevel);
        WORKERS.execute(() -> {
            try {
                target.put(finisher.finish(raw));
            } catch (RuntimeException e) {
                logFailure("finish injected chunk " + raw.chunkX + ", " + raw.chunkZ, e);
            }
        });
    }

    /**
     * A chunk was generated (pregeneration): the saved-world intake may read it again. Any thread; nothing is kept
     * while the LOD is not running.
     */
    public static void unmarkMissing(int chunkX, int chunkZ) {
        if (running) {
            INTAKE.unmarkMissing(LodDataStore.key(chunkX, chunkZ));
        }
    }

    /** Whether the LOD store already holds data for chunk (x, z). */
    public static boolean isStored(int chunkX, int chunkZ) {
        return store.contains(chunkX, chunkZ);
    }

    // ------------------------------------------------------------------ listeners

    /** Registers the LOD's chunk, level and frame listeners while it runs, and removes them when it stops. */
    private static void listen(boolean on) {
        if (on == listening || gameBus == null) {
            return;
        }
        listening = on;
        if (on) {
            gameBus.addListener(ChunkEvent.Load.class, CHUNK_LOAD);
            gameBus.addListener(ChunkEvent.Unload.class, CHUNK_UNLOAD);
            gameBus.addListener(LevelEvent.Unload.class, LEVEL_UNLOAD);
            gameBus.addListener(RenderFrameEvent.Pre.class, FRAME_START);
            gameBus.addListener(RenderLevelStageEvent.class, RENDER_STAGE);
        } else {
            gameBus.unregister(CHUNK_LOAD);
            gameBus.unregister(CHUNK_UNLOAD);
            gameBus.unregister(LEVEL_UNLOAD);
            gameBus.unregister(FRAME_START);
            gameBus.unregister(RENDER_STAGE);
        }
    }

    // ------------------------------------------------------------------ chunk events

    private static void onChunkLoad(ChunkEvent.Load event) {
        if (!event.getLevel().isClientSide() || !running || !(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }
        ChunkPos pos = chunk.getPos();
        PENDING_LOADS.putAndMoveToLast(LodDataStore.key(pos.x, pos.z), tick + LOAD_CAPTURE_DELAY_TICKS);
    }

    /** The chunk is still complete, light included: its final state becomes the LOD's. */
    private static void onChunkUnload(ChunkEvent.Unload event) {
        if (!event.getLevel().isClientSide() || !running || !(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || level != trackedLevel || chunk.getLevel() != level) {
            return;
        }
        ChunkPos pos = chunk.getPos();
        PENDING_LOADS.remove(LodDataStore.key(pos.x, pos.z));
        capture(level, chunk);
    }

    private static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            stop();
        }
    }

    // ------------------------------------------------------------------ tick

    private static void onClientTick(ClientTickEvent.Post event) {
        tick++;
        DistantHorizonsBridge.syncWithFarlandsState(PryzmaConfig.isFarlandsActive());
        if (!enabled()) {
            if (running || trackedLevel != null) {
                stop();
            }
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || level.dimensionType().hasCeiling()) {
            if (running || trackedLevel != null) {
                stop();
            }
            return;
        }
        if (level != trackedLevel) {
            stop();
            start(mc, level);
        }
        LodBlockColors.tick();
        LodDataStore current = store;
        if (LodBlockColors.generation() != tintedGeneration) {
            tintedGeneration = LodBlockColors.generation();
            retintAll(level, current);
        }
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        int cameraChunkX = Mth.floor(camera.x) >> 4;
        int cameraChunkZ = Mth.floor(camera.z) >> 4;
        int radius = PryzmaConfig.prFarlandsDistance;
        captureDueLoads(level);
        INTAKE.tick(level, current, cameraChunkX, cameraChunkZ, radius, WORKERS);
        drainCompleted(UPLOADS_PER_TICK, Long.MAX_VALUE);
        schedule(current, camera.x, camera.z, radius);
        if (tick % EVICT_INTERVAL_TICKS == 0) {
            current.evictOutside(cameraChunkX, cameraChunkZ, Math.max(radius * 2, radius + 64));
        }
    }

    /** Starts on {@code level}: chunks already loaded are captured as if they had just loaded. */
    private static void start(Minecraft mc, ClientLevel level) {
        trackedLevel = level;
        tintedGeneration = LodBlockColors.generation();
        running = true;
        listen(true);
        ChunkPos center = mc.player.chunkPosition();
        int reach = mc.options.getEffectiveRenderDistance() + 2;
        for (int z = center.z - reach; z <= center.z + reach; z++) {
            for (int x = center.x - reach; x <= center.x + reach; x++) {
                if (level.getChunkSource().getChunk(x, z, false) != null) {
                    PENDING_LOADS.putAndMoveToLast(LodDataStore.key(x, z), tick);
                }
            }
        }
    }

    /** Drops all data, jobs and meshes, and the per-frame listeners. Render thread. */
    private static void stop() {
        running = false;
        trackedLevel = null;
        store = new LodDataStore();
        PENDING_LOADS.clear();
        IN_FLIGHT.clear();
        COMPLETED.clear();
        CANDIDATES.clear();
        INTAKE.reset();
        NativeLodRenderer.clear();
        frameActive = false;
        listen(false);
    }

    private static void captureDueLoads(ClientLevel level) {
        long deadline = System.nanoTime() + LOAD_CAPTURE_BUDGET_NANOS;
        while (!PENDING_LOADS.isEmpty() && System.nanoTime() < deadline) {
            long key = PENDING_LOADS.firstLongKey();
            if (PENDING_LOADS.get(key) > tick) {
                break;
            }
            PENDING_LOADS.removeFirstLong();
            LevelChunk chunk = level.getChunkSource().getChunk(LodDataStore.keyX(key), LodDataStore.keyZ(key), false);
            if (chunk != null) {
                capture(level, chunk);
            }
        }
    }

    /** Captures on this thread, resolves ids and tints on a worker. */
    private static void capture(ClientLevel level, LevelChunk chunk) {
        LodWorldScanner.RawChunk raw = LodWorldScanner.capture(level, chunk, CAPTURE_SEQUENCE.incrementAndGet());
        LodWorldScanner.Finisher finisher = LodWorldScanner.finisher(level);
        LodDataStore target = store;
        WORKERS.execute(() -> {
            try {
                target.put(finisher.finish(raw));
            } catch (RuntimeException e) {
                logFailure("finish chunk " + raw.chunkX + ", " + raw.chunkZ, e);
            }
        });
    }

    /**
     * The palette was rebuilt after a resource reload: the colour maps and colour providers may have changed, so
     * every stored chunk is tinted again from its block states and biomes. A newer capture of a chunk always wins
     * over its re-tint ({@link LodDataStore#put}).
     */
    private static void retintAll(ClientLevel level, LodDataStore current) {
        List<LodChunkData> all = current.all();
        for (int from = 0; from < all.size(); from += RETINT_BATCH) {
            List<LodChunkData> batch = all.subList(from, Math.min(all.size(), from + RETINT_BATCH));
            LodWorldScanner.Finisher finisher = LodWorldScanner.finisher(level);
            WORKERS.execute(() -> {
                for (LodChunkData data : batch) {
                    try {
                        current.put(finisher.retint(data));
                    } catch (RuntimeException e) {
                        logFailure("re-tint chunk " + data.chunkX() + ", " + data.chunkZ(), e);
                    }
                }
            });
        }
    }

    // ------------------------------------------------------------------ meshing

    private static void schedule(LodDataStore current, double cameraX, double cameraZ, int radiusChunks) {
        int radiusBlocks = radiusChunks << 4;
        int centerX = Mth.floor(cameraX) >> LodDataStore.SECTOR_BLOCK_SHIFT;
        int centerZ = Mth.floor(cameraZ) >> LodDataStore.SECTOR_BLOCK_SHIFT;
        int reach = (radiusBlocks >> LodDataStore.SECTOR_BLOCK_SHIFT) + 1;
        int colorGeneration = LodBlockColors.generation();
        int colorEpoch = LodBlockColors.epoch();
        CANDIDATES.clear();
        if (reach > 16) {
            LongOpenHashSet toCheck = new LongOpenHashSet();
            toCheck.addAll(current.activeSectorKeys());
            toCheck.addAll(NativeLodRenderer.meshKeys());
            for (long key : toCheck) {
                int sectorX = LodDataStore.keyX(key);
                int sectorZ = LodDataStore.keyZ(key);
                if (Math.abs(sectorX - centerX) > reach || Math.abs(sectorZ - centerZ) > reach) {
                    continue;
                }
                double distance = LodMath.sectorDistance(cameraX, cameraZ, sectorX, sectorZ);
                if (distance > radiusBlocks) {
                    continue;
                }
                NativeLodRenderer.SectorMesh mesh = NativeLodRenderer.get(key);
                if (!current.hasSectorData(sectorX, sectorZ)) {
                    if (mesh != null) {
                        NativeLodRenderer.remove(key);
                    }
                    continue;
                }
                if (IN_FLIGHT.contains(key)) {
                    continue;
                }
                float zoomFactor = net.pryzma.gui.PrZoom.zoomFactor();
                int level = LodMath.levelFor(distance, mesh == null ? -1 : mesh.level, zoomFactor);
                if (mesh != null) {
                    boolean levelChanged = mesh.level != level;
                    boolean dataChanged = mesh.version != current.sectorVersion(sectorX, sectorZ);
                    boolean colorsChanged = mesh.colorGeneration != colorGeneration || mesh.fallbackColors && mesh.colorEpoch != colorEpoch;
                    if (!levelChanged && !dataChanged && !colorsChanged) {
                        continue;
                    }
                    if (!levelChanged && tick - mesh.builtTick < MIN_REMESH_TICKS) {
                        continue;
                    }
                }
                CANDIDATES.add(new Candidate(sectorX, sectorZ, level, distance));
            }
        } else {
            for (int sectorZ = centerZ - reach; sectorZ <= centerZ + reach; sectorZ++) {
                for (int sectorX = centerX - reach; sectorX <= centerX + reach; sectorX++) {
                    double distance = LodMath.sectorDistance(cameraX, cameraZ, sectorX, sectorZ);
                    if (distance > radiusBlocks) {
                        continue;
                    }
                    long key = LodDataStore.key(sectorX, sectorZ);
                    NativeLodRenderer.SectorMesh mesh = NativeLodRenderer.get(key);
                    if (!current.hasSectorData(sectorX, sectorZ)) {
                        if (mesh != null) {
                            NativeLodRenderer.remove(key);
                        }
                        continue;
                    }
                    if (IN_FLIGHT.contains(key)) {
                        continue;
                    }
                    float zoomFactor = net.pryzma.gui.PrZoom.zoomFactor();
                    int level = LodMath.levelFor(distance, mesh == null ? -1 : mesh.level, zoomFactor);
                    if (mesh != null) {
                        boolean levelChanged = mesh.level != level;
                        boolean dataChanged = mesh.version != current.sectorVersion(sectorX, sectorZ);
                        boolean colorsChanged = mesh.colorGeneration != colorGeneration || mesh.fallbackColors && mesh.colorEpoch != colorEpoch;
                        if (!levelChanged && !dataChanged && !colorsChanged) {
                            continue;
                        }
                        if (!levelChanged && tick - mesh.builtTick < MIN_REMESH_TICKS) {
                            continue;
                        }
                    }
                    CANDIDATES.add(new Candidate(sectorX, sectorZ, level, distance));
                }
            }
        }
        CANDIDATES.sort(NEAR_FIRST);
        int slots = MAX_MESH_JOBS - IN_FLIGHT.size();
        for (int i = 0; i < CANDIDATES.size() && i < slots; i++) {
            submit(current, CANDIDATES.get(i));
        }
        NativeLodRenderer.retainWithin(cameraX, cameraZ, radiusBlocks + LodDataStore.SECTOR_BLOCKS);
    }

    private static void submit(LodDataStore current, Candidate candidate) {
        long key = LodDataStore.key(candidate.sectorX(), candidate.sectorZ());
        IN_FLIGHT.add(key);
        WORKERS.execute(() -> {
            int colorGeneration = LodBlockColors.generation();
            int colorEpoch = LodBlockColors.epoch();
            int version = 0;
            boolean fallbackColors = false;
            LodMesh mesh = null;
            try {
                LodDataStore.SectorSnapshot snapshot = current.snapshotSector(candidate.sectorX(), candidate.sectorZ());
                version = snapshot.version();
                LodBlockColors.Source colors = LodBlockColors.source();
                mesh = NativeLodMesher.mesh(candidate.sectorX(), candidate.sectorZ(), snapshot.chunks(), candidate.level(), colors);
                fallbackColors = colors.usedFallback();
            } catch (RuntimeException e) {
                logFailure("mesh sector " + candidate.sectorX() + ", " + candidate.sectorZ(), e);
            }
            COMPLETED.add(new MeshResult(current, key, candidate.level(), version, colorGeneration, colorEpoch, fallbackColors, mesh));
        });
    }

    /** Uploads finished meshes, at most {@code maxUploads} and about {@code maxBytes} of vertex data. Render thread. */
    private static void drainCompleted(int maxUploads, long maxBytes) {
        LodDataStore current = store;
        int uploads = 0;
        long bytes = 0;
        MeshResult result;
        while (uploads < maxUploads && bytes < maxBytes && (result = COMPLETED.poll()) != null) {
            if (result.store() != current) {
                continue;
            }
            IN_FLIGHT.remove(result.key());
            NativeLodRenderer.accept(result.key(), result.level(), result.version(), result.colorGeneration(), result.colorEpoch(),
                    result.fallbackColors(), result.mesh(), tick);
            uploads++;
            if (result.mesh() != null) {
                bytes += result.mesh().byteSize();
            }
        }
    }

    // ------------------------------------------------------------------ drawing

    /**
     * After the sky: the LOD pass; under a shader pack also after the translucent terrain, for the LOD's water.
     * Skipped where the camera's surroundings hide the distance anyway.
     */
    private static void onRenderStage(RenderLevelStageEvent event) {
        RenderLevelStageEvent.Stage stage = event.getStage();
        boolean sky = stage == RenderLevelStageEvent.Stage.AFTER_SKY;
        if (!sky && stage != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!running || mc.level == null || mc.level != trackedLevel) {
            return;
        }
        LodShaderSupport shaders = shaderSupport();
        boolean packInUse = PryzmaShaders.isPackInUseQuick();
        if (sky) {
            frameActive = false;
            drainCompleted(UPLOADS_PER_FRAME, UPLOAD_BYTES_PER_FRAME);
        } else if (shaders == null) {
            return;
        }
        Camera camera = event.getCamera();
        if (camera.getFluidInCamera() != FogType.NONE) {
            return;
        }
        int radiusBlocks = PryzmaConfig.prFarlandsDistance << 4;
        float clipRadius = LodMath.clipRadius(mc.options.getEffectiveRenderDistance());
        if (radiusBlocks <= clipRadius + 16.0F) {
            return;
        }
        if (packInUse) {
            // The pack's own fog and lighting apply; the vanilla fog is left alone.
            if (shaders != null) {
                if (sky) {
                    shaders.renderOpaque(camera.getPosition(), clipRadius);
                } else {
                    shaders.renderWater(camera.getPosition(), clipRadius);
                }
            }
            return;
        }
        if (!sky || !PrRenderHooks.isClearAir(camera)) {
            return;
        }
        int vanillaBlocks = mc.options.getEffectiveRenderDistance() << 4;
        float[] fog = LodMath.farlandsFogRange(vanillaBlocks, radiusBlocks, PryzmaConfig.prFarlandsFog);
        if (NativeLodRenderer.render(event.getModelViewMatrix(), event.getProjectionMatrix(), camera.getPosition(), clipRadius,
                radiusBlocks, fog[0], fog[1])) {
            frameActive = true;
            frameFogStart = fog[0];
            frameFogEnd = fog[1];
        }
    }

    /** The active shader pipeline's Farlands support, null without a shader pack or with the option off at its load. */
    @Nullable
    private static LodShaderSupport shaderSupport() {
        return PryzmaShaders.getPipelineManager().getPipelineNullable() instanceof ShadedWorldRenderingPipeline pipeline
                ? pipeline.getFarlands() : null;
    }

    // ------------------------------------------------------------------ workers

    private static ThreadPoolExecutor createWorkers() {
        AtomicInteger ids = new AtomicInteger();
        ThreadPoolExecutor pool = new ThreadPoolExecutor(WORKER_THREADS, WORKER_THREADS, 30L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(), task -> {
                    Thread worker = new Thread(task, "Pryzma-LOD-Worker-" + ids.incrementAndGet());
                    worker.setDaemon(true);
                    worker.setPriority(Thread.NORM_PRIORITY - 2);
                    worker.setUncaughtExceptionHandler((thread, error) -> Pryzma.LOGGER.error("Uncaught error on {}", thread.getName(), error));
                    return worker;
                });
        pool.allowCoreThreadTimeOut(true);
        return pool;
    }

    private static void logFailure(String what, RuntimeException e) {
        int count = FAILURES.incrementAndGet();
        if (count <= MAX_LOGGED_FAILURES) {
            Pryzma.LOGGER.warn("Farlands LOD: failed to {}{}", what, count == MAX_LOGGED_FAILURES ? " (further failures are not logged)" : "", e);
        }
    }
}
