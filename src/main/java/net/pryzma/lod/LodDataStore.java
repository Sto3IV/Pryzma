package net.pryzma.lod;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;

/**
 * Farlands LOD: the surface data of every chunk known this session, keyed by chunk coordinates.
 *
 * <p>Scanner threads publish {@link LodChunkData} with {@link #put}; meshing threads read whole macro-sectors
 * ({@value #SECTOR_CHUNKS} x {@value #SECTOR_CHUNKS} chunks) with {@link #snapshotSector}. A read-write lock
 * guards the maps; the published chunk objects are immutable, so a snapshot holds plain references.
 *
 * <p>Every change bumps the version of the chunk's sector, and of the neighbouring sector when the chunk lies on
 * the shared edge: the skirts along a sector border are built from the neighbour's border columns.
 */
public final class LodDataStore {
    /** Chunks per sector side, as a shift. */
    public static final int SECTOR_SHIFT = 4;
    public static final int SECTOR_CHUNKS = 1 << SECTOR_SHIFT;
    /** Blocks per sector side, as a shift. */
    public static final int SECTOR_BLOCK_SHIFT = SECTOR_SHIFT + 4;
    public static final int SECTOR_BLOCKS = 1 << SECTOR_BLOCK_SHIFT;
    /** Chunks per side of a {@link SectorSnapshot}: the sector and a one-chunk border. */
    public static final int SNAPSHOT_SPAN = SECTOR_CHUNKS + 2;

    /**
     * The chunks of one sector and its one-chunk border at the time of the snapshot.
     *
     * @param version {@link #sectorVersion} the chunks belong to
     * @param chunks  {@link #SNAPSHOT_SPAN}² entries, see {@link #snapshotIndex}; null where no data is stored
     */
    public record SectorSnapshot(int sectorX, int sectorZ, int version, LodChunkData[] chunks) {
        /** The chunk at sector-local chunk coordinates {@code (x, z)}, each -1..{@value #SECTOR_CHUNKS}. */
        public LodChunkData chunk(int x, int z) {
            return chunks[snapshotIndex(x, z)];
        }
    }

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Long2ObjectOpenHashMap<LodChunkData> chunks = new Long2ObjectOpenHashMap<>();
    private final Long2IntOpenHashMap sectorVersions = new Long2IntOpenHashMap();
    private final Long2IntOpenHashMap sectorCounts = new Long2IntOpenHashMap();
    private int versionCounter;

    /** Packs chunk (or sector) coordinates the way {@code ChunkPos.asLong} does. */
    public static long key(int x, int z) {
        return (long) x & 0xFFFFFFFFL | ((long) z & 0xFFFFFFFFL) << 32;
    }

    public static int keyX(long key) {
        return (int) key;
    }

    public static int keyZ(long key) {
        return (int) (key >>> 32);
    }

    /** Sector coordinate of a chunk coordinate. */
    public static int sectorCoord(int chunkCoord) {
        return chunkCoord >> SECTOR_SHIFT;
    }

    /** {@link #key} of the sector holding chunk {@code (chunkX, chunkZ)}. */
    public static long sectorKeyOf(int chunkX, int chunkZ) {
        return key(chunkX >> SECTOR_SHIFT, chunkZ >> SECTOR_SHIFT);
    }

    /** Index into {@link SectorSnapshot#chunks} of sector-local chunk {@code (x, z)}, each -1..{@value #SECTOR_CHUNKS}. */
    public static int snapshotIndex(int x, int z) {
        return (z + 1) * SNAPSHOT_SPAN + x + 1;
    }

    /** Whether chunk {@code (x, z)} lies in the circle of {@code radius} chunks around {@code (centerX, centerZ)}. */
    public static boolean inRadius(int x, int z, int centerX, int centerZ, int radius) {
        long dx = (long) x - centerX;
        long dz = (long) z - centerZ;
        return dx * dx + dz * dz <= (long) radius * radius;
    }

    /**
     * Publishes {@code data}, replacing the stored capture of the same chunk unless that one is newer.
     *
     * @return whether {@code data} was stored
     */
    public boolean put(LodChunkData data) {
        long key = data.key();
        lock.writeLock().lock();
        try {
            LodChunkData old = chunks.get(key);
            if (old != null && old.sequence() > data.sequence()) {
                return false;
            }
            chunks.put(key, data);
            if (old == null) {
                sectorCounts.addTo(sectorKeyOf(data.chunkX(), data.chunkZ()), 1);
            }
            touch(data.chunkX(), data.chunkZ());
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Drops the data of chunk {@code (x, z)}; false when there was none. */
    public boolean remove(int x, int z) {
        lock.writeLock().lock();
        try {
            return removeLocked(x, z);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public LodChunkData get(int x, int z) {
        lock.readLock().lock();
        try {
            return chunks.get(key(x, z));
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean contains(int x, int z) {
        lock.readLock().lock();
        try {
            return chunks.containsKey(key(x, z));
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Number of chunks stored. */
    public int size() {
        lock.readLock().lock();
        try {
            return chunks.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Version of sector {@code (sectorX, sectorZ)}: 0 until anything touches it, then strictly increasing. */
    public int sectorVersion(int sectorX, int sectorZ) {
        lock.readLock().lock();
        try {
            return sectorVersions.get(key(sectorX, sectorZ));
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Whether any chunk of sector {@code (sectorX, sectorZ)} is stored. */
    public boolean hasSectorData(int sectorX, int sectorZ) {
        lock.readLock().lock();
        try {
            return sectorCounts.get(key(sectorX, sectorZ)) > 0;
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Returns keys of all sectors that currently have stored chunks. */
    public LongArrayList activeSectorKeys() {
        lock.readLock().lock();
        try {
            LongArrayList list = new LongArrayList(sectorCounts.size());
            for (var entry : sectorCounts.long2IntEntrySet()) {
                if (entry.getIntValue() > 0) {
                    list.add(entry.getLongKey());
                }
            }
            return list;
        } finally {
            lock.readLock().unlock();
        }
    }

    /** The chunks of a sector and its border with the sector's version, read atomically. */
    public SectorSnapshot snapshotSector(int sectorX, int sectorZ) {
        LodChunkData[] out = new LodChunkData[SNAPSHOT_SPAN * SNAPSHOT_SPAN];
        int baseX = sectorX << SECTOR_SHIFT;
        int baseZ = sectorZ << SECTOR_SHIFT;
        lock.readLock().lock();
        try {
            for (int z = -1; z <= SECTOR_CHUNKS; z++) {
                for (int x = -1; x <= SECTOR_CHUNKS; x++) {
                    out[snapshotIndex(x, z)] = chunks.get(key(baseX + x, baseZ + z));
                }
            }
            return new SectorSnapshot(sectorX, sectorZ, sectorVersions.get(key(sectorX, sectorZ)), out);
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Every stored chunk, in no particular order: a copy of the list, the chunks themselves are immutable. */
    public List<LodChunkData> all() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(chunks.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Every stored chunk with {@code minX <= x <= maxX} and {@code minZ <= z <= maxZ}, in no particular order. */
    public List<LodChunkData> queryRect(int minX, int minZ, int maxX, int maxZ) {
        List<LodChunkData> out = new ArrayList<>();
        if (maxX < minX || maxZ < minZ) {
            return out;
        }
        lock.readLock().lock();
        try {
            long area = ((long) maxX - minX + 1) * ((long) maxZ - minZ + 1);
            if (area <= chunks.size()) {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int x = minX; x <= maxX; x++) {
                        LodChunkData data = chunks.get(key(x, z));
                        if (data != null) {
                            out.add(data);
                        }
                    }
                }
            } else {
                for (LodChunkData data : chunks.values()) {
                    if (data.chunkX() >= minX && data.chunkX() <= maxX && data.chunkZ() >= minZ && data.chunkZ() <= maxZ) {
                        out.add(data);
                    }
                }
            }
        } finally {
            lock.readLock().unlock();
        }
        return out;
    }

    /** Every stored chunk within {@code radius} chunks of {@code (centerX, centerZ)} (Euclidean), in no particular order. */
    public List<LodChunkData> queryRadius(int centerX, int centerZ, int radius) {
        List<LodChunkData> out = queryRect(centerX - radius, centerZ - radius, centerX + radius, centerZ + radius);
        out.removeIf(data -> !inRadius(data.chunkX(), data.chunkZ(), centerX, centerZ, radius));
        return out;
    }

    /** Drops every chunk farther than {@code radius} chunks from {@code (centerX, centerZ)}; returns how many. */
    public int evictOutside(int centerX, int centerZ, int radius) {
        lock.writeLock().lock();
        try {
            LongArrayList doomed = new LongArrayList();
            for (LodChunkData data : chunks.values()) {
                if (!inRadius(data.chunkX(), data.chunkZ(), centerX, centerZ, radius)) {
                    doomed.add(data.key());
                }
            }
            for (int i = 0; i < doomed.size(); i++) {
                long key = doomed.getLong(i);
                removeLocked(keyX(key), keyZ(key));
            }
            return doomed.size();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void clear() {
        lock.writeLock().lock();
        try {
            chunks.clear();
            sectorCounts.clear();
            sectorVersions.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    private boolean removeLocked(int x, int z) {
        if (chunks.remove(key(x, z)) == null) {
            return false;
        }
        long sector = sectorKeyOf(x, z);
        if (sectorCounts.addTo(sector, -1) <= 1) {
            sectorCounts.remove(sector);
        }
        touch(x, z);
        return true;
    }

    /** Bumps the version of the chunk's sector and of the sectors whose border skirts read the chunk. */
    private void touch(int x, int z) {
        int sectorX = x >> SECTOR_SHIFT;
        int sectorZ = z >> SECTOR_SHIFT;
        int localX = x & SECTOR_CHUNKS - 1;
        int localZ = z & SECTOR_CHUNKS - 1;
        bump(sectorX, sectorZ);
        if (localX == 0) {
            bump(sectorX - 1, sectorZ);
        } else if (localX == SECTOR_CHUNKS - 1) {
            bump(sectorX + 1, sectorZ);
        }
        if (localZ == 0) {
            bump(sectorX, sectorZ - 1);
        } else if (localZ == SECTOR_CHUNKS - 1) {
            bump(sectorX, sectorZ + 1);
        }
    }

    private void bump(int sectorX, int sectorZ) {
        sectorVersions.put(key(sectorX, sectorZ), ++versionCounter);
    }
}
