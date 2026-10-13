package net.pryzma.lod;

/**
 * Bit-level layout of saved chunks (Anvil, 1.18+), as {@code PalettedContainer}, {@code SimpleBitStorage},
 * {@code Heightmap} and {@code DataLayer} write it. The Farlands storage intake reads single entries straight from
 * the packed arrays instead of unpacking whole sections.
 */
public final class LodPalette {
    /** Entries of a block-state section (16 x 16 x 16). */
    public static final int SECTION_BLOCKS = 4096;
    /** Entries of a biome section (4 x 4 x 4). */
    public static final int SECTION_BIOMES = 64;
    /** Entries of a heightmap (16 x 16). */
    public static final int HEIGHTMAP_ENTRIES = 256;

    private LodPalette() {
    }

    /** {@code Mth.ceillog2}: the smallest {@code n} with {@code 2^n >= value}; 0 for 1. */
    public static int ceilLog2(int value) {
        return value <= 1 ? 0 : 32 - Integer.numberOfLeadingZeros(value - 1);
    }

    /**
     * Bits per entry of a saved block-state section with {@code paletteSize} entries: none for a single value,
     * at least 4 for linear and hash-map palettes, the palette's own width beyond.
     */
    public static int blockStateBits(int paletteSize) {
        return paletteSize <= 1 ? 0 : Math.max(4, ceilLog2(paletteSize));
    }

    /** Bits per entry of a saved biome section with {@code paletteSize} entries. */
    public static int biomeBits(int paletteSize) {
        return paletteSize <= 1 ? 0 : ceilLog2(paletteSize);
    }

    /** Bits per entry of a heightmap of a world {@code worldHeight} blocks tall. */
    public static int heightmapBits(int worldHeight) {
        return ceilLog2(worldHeight + 1);
    }

    /** Longs a packed array of {@code entries} values of {@code bits} bits occupies; values never straddle two longs. */
    public static int expectedLongs(int entries, int bits) {
        int perLong = 64 / bits;
        return (entries + perLong - 1) / perLong;
    }

    /** Value {@code index} of a packed array of {@code bits}-bit values. */
    public static int get(long[] data, int bits, int index) {
        int perLong = 64 / bits;
        long word = data[index / perLong];
        int shift = index % perLong * bits;
        return (int) (word >>> shift & (1L << bits) - 1L);
    }

    /** Value {@code index} of a nibble array ({@code DataLayer}: light levels, two per byte, low nibble first). */
    public static int nibble(byte[] data, int index) {
        return data[index >> 1] >> ((index & 1) << 2) & 15;
    }

    /** Index of section-local block {@code (x, y, z)} in a block-state section or light layer. */
    public static int blockIndex(int x, int y, int z) {
        return (y & 15) << 8 | (z & 15) << 4 | x & 15;
    }

    /** Index of section-local quart {@code (qx, qy, qz)} (4-block cells) in a biome section. */
    public static int biomeIndex(int qx, int qy, int qz) {
        return (qy & 3) << 4 | (qz & 3) << 2 | qx & 3;
    }

    /** Index of column {@code (x, z)} in a heightmap. */
    public static int heightmapIndex(int x, int z) {
        return (x & 15) + (z & 15) * 16;
    }
}
