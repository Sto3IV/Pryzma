package net.pryzma.lod.data;

/**
 * Coarse material classifications and standard RGB palette for Pryzma LOD terrain.
 */
public final class LodMaterial {
    public static final int UNKNOWN = 0;
    public static final int GRASS = 1;
    public static final int DIRT = 2;
    public static final int STONE = 3;
    public static final int DEEPSLATE = 4;
    public static final int SAND = 5;
    public static final int GRAVEL = 6;
    public static final int WATER = 7;
    public static final int LAVA = 8;
    public static final int SNOW = 9;
    public static final int ICE = 10;
    public static final int LEAVES = 11;
    public static final int WOOD = 12;
    public static final int NETHERRACK = 13;
    public static final int END_STONE = 14;
    public static final int TERRACOTTA = 15;

    // Default base RGB colors (when biome tint is not applicable)
    private static final int[] BASE_COLORS = new int[256];

    static {
        BASE_COLORS[UNKNOWN] = 0x7F7F7F;
        BASE_COLORS[GRASS] = 0x5B8C32;
        BASE_COLORS[DIRT] = 0x866043;
        BASE_COLORS[STONE] = 0x7D7D7D;
        BASE_COLORS[DEEPSLATE] = 0x36363C;
        BASE_COLORS[SAND] = 0xD8CA9C;
        BASE_COLORS[GRAVEL] = 0x837F7E;
        BASE_COLORS[WATER] = 0x2A59A8;
        BASE_COLORS[LAVA] = 0xD9581E;
        BASE_COLORS[SNOW] = 0xF0F5F5;
        BASE_COLORS[ICE] = 0x91B5E8;
        BASE_COLORS[LEAVES] = 0x3A6A24;
        BASE_COLORS[WOOD] = 0x6E5335;
        BASE_COLORS[NETHERRACK] = 0x651515;
        BASE_COLORS[END_STONE] = 0xDCE09E;
        BASE_COLORS[TERRACOTTA] = 0x985E44;
    }

    public static int getBaseColor(int material) {
        if (material >= 0 && material < BASE_COLORS.length) {
            int c = BASE_COLORS[material];
            return c != 0 ? c : 0x7F7F7F;
        }
        return 0x7F7F7F;
    }

    public static void setColor(int material, int rgb) {
        if (material >= 0 && material < BASE_COLORS.length) {
            BASE_COLORS[material] = rgb & 0xFFFFFF;
        }
    }

    public static boolean isTranslucent(int material) {
        return material == WATER || material == ICE;
    }
}
