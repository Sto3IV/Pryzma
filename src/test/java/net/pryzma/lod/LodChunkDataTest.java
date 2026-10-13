package net.pryzma.lod;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** One chunk's surface columns: defaults, the visible top over fluids, light packing. */
class LodChunkDataTest {
    @Test
    void freshDataHasNoSurfaceAndWhiteTints() {
        LodChunkData data = new LodChunkData(1, -2, 7L);
        for (int i = 0; i < LodChunkData.AREA; i++) {
            assertFalse(data.hasSurface(i));
            assertFalse(data.hasFluid(i));
            assertEquals(LodChunkData.NO_TINT, data.topTint(i));
        }
        assertEquals(1, data.chunkX());
        assertEquals(-2, data.chunkZ());
        assertEquals(7L, data.sequence());
        assertEquals(LodDataStore.key(1, -2), data.key());
    }

    @Test
    void columnsAreRowMajor() {
        assertEquals(0, LodChunkData.index(0, 0));
        assertEquals(4 * 16 + 3, LodChunkData.index(3, 4));
        assertEquals(LodChunkData.AREA - 1, LodChunkData.index(15, 15));
    }

    @Test
    void theVisibleTopIsTheFluidWhereThereIsOne() {
        LodChunkData data = new LodChunkData(0, 0, 0L);
        int dry = LodChunkData.index(3, 4);
        int wet = LodChunkData.index(15, 15);
        int bottomless = LodChunkData.index(0, 1);
        data.setColumn(dry, 70, 9, LodChunkData.NO_FLUID, 0, 15, 2);
        data.setColumn(wet, 40, 11, 63, 86, 14, 0);
        data.setColumn(bottomless, LodChunkData.NO_SURFACE, 0, 63, 86, 15, 0);
        data.setTints(dry, 0x91BD59, 0x3F76E4);
        data.setTints(wet, 0xFFFFFF, 0x3F76E4);

        assertTrue(data.hasSurface(dry));
        assertFalse(data.hasFluid(dry));
        assertEquals(70, data.topHeight(dry));
        assertEquals(9, data.topStateId(dry));
        assertEquals(0x91BD59, data.topTint(dry));
        assertEquals(0, data.fluidDepth(dry));

        assertTrue(data.hasFluid(wet));
        assertEquals(63, data.topHeight(wet));
        assertEquals(86, data.topStateId(wet));
        assertEquals(0x3F76E4, data.topTint(wet));
        assertEquals(23, data.fluidDepth(wet));

        assertTrue(data.hasSurface(bottomless), "a fluid without a floor in reach is still a surface");
        assertEquals(63, data.topHeight(bottomless));
        assertEquals(Integer.MAX_VALUE, data.fluidDepth(bottomless));
    }

    @Test
    void lightKeepsFourBitsPerLayer() {
        LodChunkData data = new LodChunkData(0, 0, 0L);
        data.setColumn(0, 64, 1, LodChunkData.NO_FLUID, 0, 31, 18);
        assertEquals(15, data.skyLight[0]);
        assertEquals(2, data.blockLight[0]);
        assertEquals(2 << 4 | 15, data.packedLight(0));
        data.setColumn(1, 64, 1, LodChunkData.NO_FLUID, 0, 14, 0);
        assertEquals(14, data.packedLight(1));
    }

    @Test
    void tintsDropTheirAlpha() {
        LodChunkData data = new LodChunkData(0, 0, 0L);
        data.setTints(5, 0xFF91BD59, 0x803F76E4);
        assertEquals(0x91BD59, data.solidTint[5]);
        assertEquals(0x3F76E4, data.fluidTint[5]);
    }

    @Test
    void canopiesAreALayerAboveTheGround() {
        LodChunkData data = new LodChunkData(0, 0, 0L);
        int tree = LodChunkData.index(4, 4);
        for (int i = 0; i < LodChunkData.AREA; i++) {
            assertFalse(data.hasCanopy(i));
            assertEquals(LodChunkData.NO_TINT, data.canopyTint[i]);
            assertEquals(LodChunkData.NO_BIOME, data.biomeId[i]);
        }
        data.setColumn(tree, 64, 9, LodChunkData.NO_FLUID, 0, 11, 0);
        data.setCanopy(tree, 72, 68, 77, 0xFF59AE30, 3 << 4 | 15);
        assertTrue(data.hasCanopy(tree));
        assertEquals(72, data.canopyTop[tree]);
        assertEquals(68, data.canopyBottom[tree]);
        assertEquals(77, data.canopyStateId[tree]);
        assertEquals(0x59AE30, data.canopyTint[tree], "tints drop their alpha");
        assertEquals(3 << 4 | 15, data.packedCanopyLight(tree));
        assertEquals(11, data.packedLight(tree), "the forest floor keeps its own, shaded light");
        assertEquals(64, data.topHeight(tree), "the visible top below the canopy is the ground");

        int inverted = LodChunkData.index(5, 4);
        data.setCanopy(inverted, 70, 75, 77, LodChunkData.NO_TINT, 15);
        assertEquals(70, data.canopyBottom[inverted], "a bottom above the top is clamped to it");
    }

    @Test
    void copiesAreDeep() {
        LodChunkData data = new LodChunkData(-3, 8, 42L);
        int i = LodChunkData.index(7, 9);
        data.setColumn(i, 40, 11, 63, 86, 14, 2);
        data.setTints(i, 0x91BD59, 0x3F76E4);
        data.setCanopy(i, 75, 70, 77, 0x59AE30, 0xF5);
        data.biomeId[i] = 12;
        LodChunkData copy = data.copy();
        assertEquals(data.chunkX(), copy.chunkX());
        assertEquals(data.chunkZ(), copy.chunkZ());
        assertEquals(data.sequence(), copy.sequence());
        assertArrayEquals(data.heightSolid, copy.heightSolid);
        assertArrayEquals(data.heightFluid, copy.heightFluid);
        assertArrayEquals(data.blockStateId, copy.blockStateId);
        assertArrayEquals(data.fluidStateId, copy.fluidStateId);
        assertArrayEquals(data.solidTint, copy.solidTint);
        assertArrayEquals(data.fluidTint, copy.fluidTint);
        assertArrayEquals(data.skyLight, copy.skyLight);
        assertArrayEquals(data.blockLight, copy.blockLight);
        assertArrayEquals(data.canopyTop, copy.canopyTop);
        assertArrayEquals(data.canopyBottom, copy.canopyBottom);
        assertArrayEquals(data.canopyStateId, copy.canopyStateId);
        assertArrayEquals(data.canopyTint, copy.canopyTint);
        assertArrayEquals(data.canopyLight, copy.canopyLight);
        assertArrayEquals(data.biomeId, copy.biomeId);
        copy.canopyTint[i] = 0;
        copy.heightSolid[i] = 1;
        assertEquals(0x59AE30, data.canopyTint[i], "the copy's arrays are its own");
        assertEquals(40, data.heightSolid[i]);
    }
}
