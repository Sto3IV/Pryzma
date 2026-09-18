package net.pryzma.lod.data;

import java.util.concurrent.ConcurrentHashMap;

/**
 * A regional grouping of 16x16 chunks (256x256 blocks).
 * Manages spatial bounding boxes, dirty flags for regional meshing, and lifecycle.
 */
public final class LodRegion {
    public static final int CHUNKS_PER_AXIS = 16;
    public final int regionX;
    public final int regionZ;
    private final ConcurrentHashMap<Integer, LodChunk> chunks = new ConcurrentHashMap<>();
    private volatile boolean meshDirty = true;
    private volatile int chunkCount = 0;

    private final java.util.concurrent.atomic.AtomicBoolean meshingInProgress = new java.util.concurrent.atomic.AtomicBoolean(false);

    public LodRegion(int regionX, int regionZ) {
        this.regionX = regionX;
        this.regionZ = regionZ;
    }

    public static long getRegionKey(int regionX, int regionZ) {
        return (((long) regionX) << 32) | (regionZ & 0xFFFFFFFFL);
    }

    public static int chunkToRegionCoord(int chunkCoord) {
        return chunkCoord >> 4;
    }

    private static int localChunkKey(int chunkX, int chunkZ) {
        int lx = chunkX & 15;
        int lz = chunkZ & 15;
        return (lz << 4) | lx;
    }

    public void putChunk(LodChunk chunk) {
        int key = localChunkKey(chunk.chunkX, chunk.chunkZ);
        LodChunk prev = chunks.put(key, chunk);
        if (prev == null) {
            chunkCount++;
        }
        meshDirty = true;
    }

    public LodChunk getChunk(int chunkX, int chunkZ) {
        return chunks.get(localChunkKey(chunkX, chunkZ));
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public boolean isMeshDirty() {
        return meshDirty;
    }

    public void setMeshDirty(boolean dirty) {
        this.meshDirty = dirty;
    }

    public boolean tryStartMeshing() {
        return meshingInProgress.compareAndSet(false, true);
    }

    public void finishMeshing() {
        meshingInProgress.set(false);
    }

    public ConcurrentHashMap<Integer, LodChunk> getChunks() {
        return chunks;
    }

    public int getMinBlockX() {
        return (regionX << 4) * 16;
    }

    public int getMinBlockZ() {
        return (regionZ << 4) * 16;
    }

    public int getMaxBlockX() {
        return getMinBlockX() + (CHUNKS_PER_AXIS * 16);
    }

    public int getMaxBlockZ() {
        return getMinBlockZ() + (CHUNKS_PER_AXIS * 16);
    }
}
