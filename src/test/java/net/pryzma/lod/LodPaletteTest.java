package net.pryzma.lod;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Saved-chunk bit layout, as PalettedContainer, SimpleBitStorage, Heightmap and DataLayer write it. */
class LodPaletteTest {
    /** Packs {@code values} the way SimpleBitStorage does: whole values per long, low bits first. */
    private static long[] pack(int[] values, int bits) {
        int perLong = 64 / bits;
        long mask = (1L << bits) - 1L;
        long[] data = new long[LodPalette.expectedLongs(values.length, bits)];
        for (int i = 0; i < values.length; i++) {
            data[i / perLong] |= (values[i] & mask) << (i % perLong * bits);
        }
        return data;
    }

    @Test
    void bitWidthsFollowTheSavedPalettes() {
        assertEquals(0, LodPalette.ceilLog2(1));
        assertEquals(1, LodPalette.ceilLog2(2));
        assertEquals(2, LodPalette.ceilLog2(3));
        assertEquals(2, LodPalette.ceilLog2(4));
        assertEquals(3, LodPalette.ceilLog2(5));
        assertEquals(9, LodPalette.ceilLog2(385));
        assertEquals(0, LodPalette.blockStateBits(1), "a single state needs no data");
        assertEquals(4, LodPalette.blockStateBits(2), "linear palettes use at least 4 bits");
        assertEquals(4, LodPalette.blockStateBits(16));
        assertEquals(5, LodPalette.blockStateBits(17));
        assertEquals(0, LodPalette.biomeBits(1));
        assertEquals(1, LodPalette.biomeBits(2));
        assertEquals(2, LodPalette.biomeBits(3));
        assertEquals(9, LodPalette.heightmapBits(384), "overworld: 385 heights");
        assertEquals(9, LodPalette.heightmapBits(256), "257 heights still need 9 bits");
        assertEquals(12, LodPalette.heightmapBits(4064), "the tallest world a datapack may define");
    }

    @Test
    void valuesNeverStraddleTwoLongs() {
        assertEquals(37, LodPalette.expectedLongs(LodPalette.HEIGHTMAP_ENTRIES, 9));
        assertEquals(256, LodPalette.expectedLongs(LodPalette.SECTION_BLOCKS, 4));
        assertEquals(342, LodPalette.expectedLongs(LodPalette.SECTION_BLOCKS, 5));
        assertEquals(1, LodPalette.expectedLongs(LodPalette.SECTION_BIOMES, 1));
        assertEquals(4, LodPalette.expectedLongs(LodPalette.SECTION_BIOMES, 3));
    }

    @Test
    void packedValuesReadBackEntryByEntry() {
        for (int bits : new int[] {1, 3, 4, 5, 9, 15, 31}) {
            int[] values = new int[LodPalette.SECTION_BLOCKS];
            long mask = (1L << bits) - 1L;
            for (int i = 0; i < values.length; i++) {
                values[i] = (int) ((i * 2_654_435_761L >>> 7) & mask);
            }
            long[] data = pack(values, bits);
            for (int i = 0; i < values.length; i++) {
                assertEquals(values[i], LodPalette.get(data, bits, i), bits + " bits, entry " + i);
            }
        }
    }

    @Test
    void nibblesAreLowFirst() {
        byte[] layer = new byte[2048];
        layer[0] = (byte) 0xA5;
        layer[2047] = (byte) 0xF0;
        assertEquals(5, LodPalette.nibble(layer, 0));
        assertEquals(10, LodPalette.nibble(layer, 1));
        assertEquals(0, LodPalette.nibble(layer, 4094));
        assertEquals(15, LodPalette.nibble(layer, 4095));
    }

    @Test
    void indicesMatchTheVanillaOrder() {
        assertEquals(2 * 256 + 3 * 16 + 1, LodPalette.blockIndex(1, 18, 3), "y, then z, then x; y within the section");
        assertEquals(15 * 256 + 15, LodPalette.blockIndex(15, -1, 0));
        assertEquals(2 * 16 + 3 * 4 + 1, LodPalette.biomeIndex(1, 2, 3));
        assertEquals(3 + 2 * 16, LodPalette.heightmapIndex(3, 2), "x + z * 16");
        assertEquals(255, LodPalette.heightmapIndex(-1, -1));
    }
}
