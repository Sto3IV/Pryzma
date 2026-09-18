package net.pryzma.lod.data;

/**
 * Encapsulates a 16x16 block area of LOD terrain columns.
 * Memory footprint: 256 * 8 bytes = 2048 bytes (2 KB).
 */
public final class LodChunk {
    public final int chunkX;
    public final int chunkZ;
    private final long[] columns = new long[256];
    private int minY = Integer.MAX_VALUE;
    private int maxY = Integer.MIN_VALUE;
    private volatile boolean dirty = true;

    public LodChunk(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    public void setColumn(int localX, int localZ, long column) {
        int idx = (localZ << 4) | localX;
        columns[idx] = column;
        int ty = LodColumn.getTerrainY(column);
        int wy = LodColumn.getWaterY(column);
        int y = Math.max(ty, wy);
        if (y < minY) minY = y;
        if (y > maxY) maxY = y;
        dirty = true;
    }

    public long getColumn(int localX, int localZ) {
        return columns[(localZ << 4) | localX];
    }

    public int getMinY() {
        return minY == Integer.MAX_VALUE ? 64 : minY;
    }

    public int getMaxY() {
        return maxY == Integer.MIN_VALUE ? 64 : maxY;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void clearDirty() {
        dirty = false;
    }

    public long[] getRawColumns() {
        return columns;
    }
}
