package net.pryzma.lod;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.pryzma.Pryzma;

/**
 * Farlands LOD intake from the saved world (singleplayer). Chunks inside the LOD radius that the client has never
 * loaded are read through the integrated server's chunk storage, which hands back the NBT the server would load:
 * from its region files, or from its write queue when the chunk is not saved yet. Reads run on the storage's IO
 * thread and are decoded on the LOD workers; at most {@value #MAX_IN_FLIGHT} are outstanding so the server's own
 * chunk loading never waits long behind them.
 *
 * <p>Requests go out nearest first along a precomputed spiral ({@link LodMath#spiral}). A chunk that was stored is
 * skipped while the store holds it, and read again once eviction dropped it. A chunk found missing (never
 * generated, not fully generated, or older than the 1.18 layout) is not asked for again this session: once it
 * exists, the client loads it and the live capture takes over. Client thread, except the read callbacks.
 */
final class LodStorageIntake {
    private static final int MAX_IN_FLIGHT = 32;
    /** Spiral positions examined per tick; skipped positions cost a few hash lookups. */
    private static final int POSITIONS_PER_TICK = 32768;

    /** A finished read of {@code session}: {@code stored} is false when the chunk had nothing the LOD could use. */
    private record ReadResult(int session, long key, boolean stored) {
    }

    private final LongOpenHashSet requested = new LongOpenHashSet();
    private final LongOpenHashSet missing = new LongOpenHashSet();
    private final LongOpenHashSet existingRegions = new LongOpenHashSet();
    private final LongOpenHashSet missingRegions = new LongOpenHashSet();
    private final ConcurrentLinkedQueue<ReadResult> done = new ConcurrentLinkedQueue<>();
    /** Chunks generated since they were found missing, from any thread; folded into {@link #missing} on the client thread. */
    private final ConcurrentLinkedQueue<Long> generated = new ConcurrentLinkedQueue<>();
    /** Spiral offsets as {@code dx, dz} pairs sorted by distance, for {@link #spiralRadius}. */
    private int[] spiral = new int[0];
    private int spiralRadius = -1;
    private int cursor;
    private int centerX;
    private int centerZ;
    /** Bumped by {@link #reset}: reads of an earlier session report into nothing of this one. */
    private int session;
    private volatile boolean warned;

    /**
     * Issues reads around chunk {@code (cameraChunkX, cameraChunkZ)} and collects finished ones.
     *
     * @return reads currently outstanding
     */
    int tick(ClientLevel level, LodDataStore store, int cameraChunkX, int cameraChunkZ, int radius, Executor workers) {
        ReadResult result;
        while ((result = done.poll()) != null) {
            if (result.session() != session) {
                continue;
            }
            requested.remove(result.key());
            if (!result.stored()) {
                missing.add(result.key());
            }
        }
        Long chunk;
        while ((chunk = generated.poll()) != null) {
            long k = chunk.longValue();
            missing.remove(k);
            int cx = LodDataStore.keyX(k);
            int cz = LodDataStore.keyZ(k);
            missingRegions.remove(LodDataStore.key(cx >> 5, cz >> 5));
        }
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null || radius <= 0) {
            return requested.size();
        }
        ServerLevel serverLevel = server.getLevel(level.dimension());
        if (serverLevel == null) {
            return requested.size();
        }
        ChunkMap storage = serverLevel.getChunkSource().chunkMap;
        java.nio.file.Path regionDir = resolveRegionDir(server, serverLevel);
        if (radius != spiralRadius) {
            spiral = LodMath.spiral(radius);
            spiralRadius = radius;
            cursor = 0;
        }
        if (cameraChunkX != centerX || cameraChunkZ != centerZ) {
            centerX = cameraChunkX;
            centerZ = cameraChunkZ;
            cursor = 0;
        }
        LodWorldScanner.DecodeContext context = null;
        int examined = 0;
        while (cursor < spiral.length && requested.size() < MAX_IN_FLIGHT && examined < POSITIONS_PER_TICK) {
            int x = centerX + spiral[cursor];
            int z = centerZ + spiral[cursor + 1];
            cursor += 2;
            examined++;
            int rx = x >> 5;
            int rz = z >> 5;
            long regionKey = LodDataStore.key(rx, rz);
            if (missingRegions.contains(regionKey)) {
                continue;
            }
            if (!existingRegions.contains(regionKey)) {
                if (regionDir != null) {
                    java.nio.file.Path file = regionDir.resolve("r." + rx + "." + rz + ".mca");
                    if (java.nio.file.Files.exists(file)) {
                        existingRegions.add(regionKey);
                    } else {
                        missingRegions.add(regionKey);
                        continue;
                    }
                }
            }
            long chunkKey = LodDataStore.key(x, z);
            if (missing.contains(chunkKey) || requested.contains(chunkKey) || store.contains(x, z)
                    || level.getChunkSource().getChunk(x, z, false) != null) {
                continue;
            }
            if (context == null) {
                context = LodWorldScanner.decodeContext(level);
            }
            request(storage, store, x, z, chunkKey, context, LodWorldScanner.finisher(level), workers);
        }
        return requested.size();
    }

    private static java.nio.file.Path resolveRegionDir(IntegratedServer server, ServerLevel serverLevel) {
        try {
            java.nio.file.Path root = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim = serverLevel.dimension();
            if (dim == net.minecraft.world.level.Level.OVERWORLD) {
                return root.resolve("region");
            } else if (dim == net.minecraft.world.level.Level.NETHER) {
                java.nio.file.Path p = root.resolve("dimensions/minecraft/the_nether/region");
                return java.nio.file.Files.isDirectory(p) ? p : root.resolve("DIM-1/region");
            } else if (dim == net.minecraft.world.level.Level.END) {
                java.nio.file.Path p = root.resolve("dimensions/minecraft/the_end/region");
                return java.nio.file.Files.isDirectory(p) ? p : root.resolve("DIM1/region");
            } else {
                return root.resolve("dimensions")
                        .resolve(dim.location().getNamespace())
                        .resolve(dim.location().getPath())
                        .resolve("region");
            }
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void request(ChunkMap storage, LodDataStore store, int x, int z, long chunkKey, LodWorldScanner.DecodeContext context,
            LodWorldScanner.Finisher finisher, Executor workers) {
        requested.add(chunkKey);
        int readSession = session;
        storage.read(new ChunkPos(x, z)).whenCompleteAsync((tag, error) -> {
            boolean stored = false;
            try {
                if (error == null && tag.isPresent()) {
                    LodWorldScanner.RawChunk raw = LodWorldScanner.decode(tag.get(), x, z, context);
                    if (raw != null) {
                        store.put(finisher.finish(raw));
                        stored = true;
                    }
                }
            } catch (RuntimeException e) {
                if (!warned) {
                    warned = true;
                    Pryzma.LOGGER.warn("Farlands LOD: could not read saved chunk [{}, {}]", x, z, e);
                }
            } finally {
                done.add(new ReadResult(readSession, chunkKey, stored));
            }
        }, workers);
    }

    /** Forgets every request; reads still running report into the store they were issued for. */
    void reset() {
        session++;
        requested.clear();
        missing.clear();
        existingRegions.clear();
        missingRegions.clear();
        done.clear();
        generated.clear();
        cursor = 0;
    }

    /** The chunk exists now (pregeneration, on the server thread): it may be read again. Any thread. */
    void unmarkMissing(long chunkKey) {
        generated.add(chunkKey);
        int cx = LodDataStore.keyX(chunkKey);
        int cz = LodDataStore.keyZ(chunkKey);
        missingRegions.remove(LodDataStore.key(cx >> 5, cz >> 5));
    }

    int outstanding() {
        return requested.size();
    }
}
