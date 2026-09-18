package net.pryzma.lod.data;

/**
 * High-density 64-bit representation of a single block column in a LOD chunk.
 * <p>
 * Bit layout in {@code raw}:
 * <ul>
 *   <li>bits  0..11 (12 bits): {@code terrainY} (signed offset from -64..1023, stored as value + 64)</li>
 *   <li>bits 12..23 (12 bits): {@code waterY} (signed offset from -64..1023, 0 = no water)</li>
 *   <li>bits 24..31 ( 8 bits): {@code blockMaterial} (coarse material category: grass, stone, sand, wood, leaves, etc.)</li>
 *   <li>bits 32..47 (16 bits): {@code biomeId} (0..65535, used for biome tinting)</li>
 *   <li>bits 48..51 ( 4 bits): {@code skyLight} (0..15)</li>
 *   <li>bits 52..55 ( 4 bits): {@code blockLight} (0..15)</li>
 *   <li>bits 56..63 ( 8 bits): {@code flags} (bit 0: hasWater, bit 1: isLava, bit 2: hasSnow)</li>
 * </ul>
 */
public final class LodColumn {
    public static final int Y_OFFSET = 64;
    public static final int FLAG_HAS_WATER = 1;
    public static final int FLAG_IS_LAVA = 2;
    public static final int FLAG_HAS_SNOW = 4;

    public static long pack(int terrainY, int waterY, int material, int biomeId, int skyLight, int blockLight, int flags) {
        long ty = (Math.max(-64, Math.min(1023, terrainY)) + Y_OFFSET) & 0xFFFL;
        long wy = (waterY > -64 ? (Math.min(1023, waterY) + Y_OFFSET) : 0L) & 0xFFFL;
        long mat = (material & 0xFFL);
        long biome = (biomeId & 0xFFFFL);
        long sky = (Math.max(0, Math.min(15, skyLight)) & 0xFL);
        long blk = (Math.max(0, Math.min(15, blockLight)) & 0xFL);
        long flg = (flags & 0xFFL);

        return ty
                | (wy << 12)
                | (mat << 24)
                | (biome << 32)
                | (sky << 48)
                | (blk << 52)
                | (flg << 56);
    }

    public static int getTerrainY(long raw) {
        return (int) (raw & 0xFFFL) - Y_OFFSET;
    }

    public static int getWaterY(long raw) {
        int val = (int) ((raw >>> 12) & 0xFFFL);
        return val == 0 ? -128 : val - Y_OFFSET;
    }

    public static int getMaterial(long raw) {
        return (int) ((raw >>> 24) & 0xFFL);
    }

    public static int getBiomeId(long raw) {
        return (int) ((raw >>> 32) & 0xFFFFL);
    }

    public static int getSkyLight(long raw) {
        return (int) ((raw >>> 48) & 0xFL);
    }

    public static int getBlockLight(long raw) {
        return (int) ((raw >>> 52) & 0xFL);
    }

    public static int getFlags(long raw) {
        return (int) ((raw >>> 56) & 0xFFL);
    }

    public static boolean hasWater(long raw) {
        return (getFlags(raw) & FLAG_HAS_WATER) != 0;
    }

    public static boolean isLava(long raw) {
        return (getFlags(raw) & FLAG_IS_LAVA) != 0;
    }

    public static boolean hasSnow(long raw) {
        return (getFlags(raw) & FLAG_HAS_SNOW) != 0;
    }
}
