package net.pryzma.lod;

/**
 * Material ids of LOD faces. The values are the Distant Horizons block materials that shader packs know as the
 * {@code DH_BLOCK_*} macros (see {@code StandardMacros}); they travel in every vertex, so a pack's {@code dh_terrain}
 * and {@code dh_water} programs can shade foliage, water and emitters as they would Distant Horizons terrain, and
 * the native LOD shader can recognise foliage.
 */
public final class LodMaterial {
    public static final int UNKNOWN = 0;
    public static final int LEAVES = 1;
    public static final int STONE = 2;
    public static final int WOOD = 3;
    public static final int METAL = 4;
    public static final int DIRT = 5;
    public static final int LAVA = 6;
    public static final int DEEPSLATE = 7;
    public static final int SNOW = 8;
    public static final int SAND = 9;
    public static final int TERRACOTTA = 10;
    public static final int NETHER_STONE = 11;
    public static final int WATER = 12;
    public static final int GRASS = 13;
    public static final int AIR = 14;
    public static final int ILLUMINATED = 15;
    /** One more than the largest id. */
    public static final int COUNT = 16;

    private LodMaterial() {
    }

    /** Whether {@code material} is a valid id. */
    public static boolean isValid(int material) {
        return material >= 0 && material < COUNT;
    }
}
