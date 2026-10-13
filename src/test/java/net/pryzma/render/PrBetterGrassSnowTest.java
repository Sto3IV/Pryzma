package net.pryzma.render;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.pryzma.PryzmaConfig;

class PrBetterGrassSnowTest {
    private int savedBetterGrass;
    private boolean savedBetterSnow;

    @BeforeEach
    void setUp() {
        savedBetterGrass = PryzmaConfig.prBetterGrass;
        savedBetterSnow = PryzmaConfig.prBetterSnow;
        PrBetterGrass.clearContext();
        PrBetterGrass.clearCaches();
        PrBetterSnow.clearCaches();
    }

    @AfterEach
    void tearDown() {
        PryzmaConfig.prBetterGrass = savedBetterGrass;
        PryzmaConfig.prBetterSnow = savedBetterSnow;
        PrBetterGrass.clearContext();
        PrBetterGrass.clearCaches();
        PrBetterSnow.clearCaches();
    }

    @Test
    void testConfigModes() {
        PryzmaConfig.prBetterGrass = 1;
        assertTrue(PryzmaConfig.isBetterGrass());
        assertTrue(PryzmaConfig.isBetterGrassFast());
        assertFalse(PryzmaConfig.isBetterGrassFancy());

        PryzmaConfig.prBetterGrass = 2;
        assertTrue(PryzmaConfig.isBetterGrass());
        assertFalse(PryzmaConfig.isBetterGrassFast());
        assertTrue(PryzmaConfig.isBetterGrassFancy());

        PryzmaConfig.prBetterGrass = 3;
        assertFalse(PryzmaConfig.isBetterGrass());
        assertFalse(PryzmaConfig.isBetterGrassFast());
        assertFalse(PryzmaConfig.isBetterGrassFancy());
    }

    @Test
    void testContextLifecycle() {
        assertNull(PrBetterGrass.getCurrentLevel());
        assertNull(PrBetterGrass.getCurrentPos());
        assertNull(PrBetterGrass.getCurrentState());
        assertNull(PrBetterGrass.getFaceQuads(null, null, Direction.NORTH, RandomSource.create(), ModelData.EMPTY, null));
        assertNull(PrBetterGrass.getFaceQuads(null, null, Direction.UP, RandomSource.create(), ModelData.EMPTY, null));
        assertNull(PrBetterGrass.getFaceQuads(null, null, Direction.DOWN, RandomSource.create(), ModelData.EMPTY, null));
    }

    @Test
    void testBetterSnowReentrancyGuard() {
        assertFalse(PrBetterSnow.isRenderingSnow());
    }
}
