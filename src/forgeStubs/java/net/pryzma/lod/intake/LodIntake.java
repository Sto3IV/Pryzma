package net.pryzma.lod.intake;

import net.pryzma.lod.config.LodConfig;
import net.pryzma.lod.data.LodChunk;
import net.pryzma.lod.data.LodColumn;
import net.pryzma.lod.data.LodWorldStorage;
import net.pryzma.lod.exec.PryzmaLodExecutor;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Non-blocking intake pipeline capturing chunk height and block data asynchronously.
 * Guarantees zero stalls on client networking or rendering threads.
 */
public final class LodIntake {
    private static final int MAX_PENDING = 512;
    private static final ConcurrentLinkedQueue<RawChunkPayload> QUEUE = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger PENDING_COUNT = new AtomicInteger(0);

    public static void onChunkLoaded(ClientLevel level, ChunkPos pos) {
        if (!LodConfig.isEnabled() || level == null || pos == null) {
            return;
        }
        PryzmaLodExecutor.execute(() -> {
            try {
                LevelChunk chunk = level.getChunk(pos.x, pos.z);
                if (chunk == null || chunk.isEmpty()) {
                    return;
                }
                LodChunk lodChunk = new LodChunk(pos.x, pos.z);
                int minBuild = chunk.getMinBuildHeight();
                BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        int ty = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                        int wy = -128;
                        int mat = 1;
                        if (ty > minBuild) {
                            mpos.set(x, ty - 1, z);
                            BlockState bs = chunk.getBlockState(mpos);
                            if (bs != null && bs.is(Blocks.WATER)) {
                                wy = ty;
                                mat = 6;
                            }
                        }
                        long col = LodColumn.pack(ty, wy, mat, 0, 15, 0, wy > -128 ? LodColumn.FLAG_HAS_WATER : 0);
                        lodChunk.setColumn(x, z, col);
                    }
                }
                LodWorldStorage.get().putChunk(lodChunk);
            } catch (Throwable ignored) {
            }
        });
    }

    public record RawChunkPayload(int chunkX, int chunkZ, short[] terrainHeights, short[] waterHeights,
                                  short[] materials, int biomeId, byte[] lights) {
    }

    public static void submit(int chunkX, int chunkZ, short[] terrainHeights, short[] waterHeights,
                              short[] materials, int biomeId, byte[] lights) {
        if (!LodConfig.isEnabled()) {
            return;
        }

        // Bounded queue: drop oldest if overloaded to never block producer threads
        if (PENDING_COUNT.get() >= MAX_PENDING) {
            RawChunkPayload dropped = QUEUE.poll();
            if (dropped != null) {
                PENDING_COUNT.decrementAndGet();
            }
        }

        QUEUE.offer(new RawChunkPayload(chunkX, chunkZ, terrainHeights, waterHeights, materials, biomeId, lights));
        PENDING_COUNT.incrementAndGet();

        PryzmaLodExecutor.execute(LodIntake::drainQueue);
    }

    private static void drainQueue() {
        RawChunkPayload payload;
        while ((payload = QUEUE.poll()) != null) {
            PENDING_COUNT.decrementAndGet();
            processPayload(payload);
        }
    }

    private static void processPayload(RawChunkPayload p) {
        LodChunk chunk = new LodChunk(p.chunkX, p.chunkZ);
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int idx = (z << 4) | x;
                int ty = (p.terrainHeights != null && idx < p.terrainHeights.length) ? p.terrainHeights[idx] : 64;
                int wy = (p.waterHeights != null && idx < p.waterHeights.length) ? p.waterHeights[idx] : -128;
                int mat = (p.materials != null && idx < p.materials.length) ? p.materials[idx] : 1;
                int light = (p.lights != null && idx < p.lights.length) ? p.lights[idx] : 15;
                int sky = (light >>> 4) & 0xF;
                int blk = light & 0xF;
                int flags = wy > -64 ? LodColumn.FLAG_HAS_WATER : 0;

                long col = LodColumn.pack(ty, wy, mat, p.biomeId, sky, blk, flags);
                chunk.setColumn(x, z, col);
            }
        }
        LodWorldStorage.get().putChunk(chunk);
    }
}
