package net.pryzma.lod.data;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

/**
 * World-level storage for LOD regions with spatial index and distance-based retention.
 */
public final class LodWorldStorage {
    private static final LodWorldStorage INSTANCE = new LodWorldStorage();
    private final ConcurrentHashMap<Long, LodRegion> regions = new ConcurrentHashMap<>();

    public static LodWorldStorage get() {
        return INSTANCE;
    }

    public LodRegion getOrCreateRegion(int regionX, int regionZ) {
        long key = LodRegion.getRegionKey(regionX, regionZ);
        return regions.computeIfAbsent(key, k -> new LodRegion(regionX, regionZ));
    }

    public LodRegion getRegion(int regionX, int regionZ) {
        return regions.get(LodRegion.getRegionKey(regionX, regionZ));
    }

    public void putChunk(LodChunk chunk) {
        int rx = LodRegion.chunkToRegionCoord(chunk.chunkX);
        int rz = LodRegion.chunkToRegionCoord(chunk.chunkZ);
        LodRegion region = getOrCreateRegion(rx, rz);
        region.putChunk(chunk);
    }

    public boolean hasChunk(int chunkX, int chunkZ) {
        int rx = LodRegion.chunkToRegionCoord(chunkX);
        int rz = LodRegion.chunkToRegionCoord(chunkZ);
        LodRegion region = regions.get(LodRegion.getRegionKey(rx, rz));
        return region != null && region.getChunk(chunkX, chunkZ) != null;
    }

    public Collection<LodRegion> getAllRegions() {
        return regions.values();
    }

    public void pruneRegions(int centerChunkX, int centerChunkZ, int maxDistanceChunks) {
        int maxDistSq = maxDistanceChunks * maxDistanceChunks;
        regions.entrySet().removeIf(entry -> {
            LodRegion reg = entry.getValue();
            int regCenterChunkX = (reg.regionX << 4) + 8;
            int regCenterChunkZ = (reg.regionZ << 4) + 8;
            int dx = regCenterChunkX - centerChunkX;
            int dz = regCenterChunkZ - centerChunkZ;
            return (dx * dx + dz * dz) > maxDistSq;
        });
    }

    public void clear() {
        regions.clear();
    }

    public int getRegionCount() {
        return regions.size();
    }
}
