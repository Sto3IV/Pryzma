package net.pryzma.farlands;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.pryzma.PryzmaConfig;

class DistantHorizonsBridgeTest {

    @BeforeEach
    @AfterEach
    void resetState() {
        DistantHorizonsBridge.resetMocks();
        PryzmaConfig.prFarlandsDistance = 0;
    }

    @Test
    void whenDhNotInstalled_farlandsFunctionsNormally() {
        DistantHorizonsBridge.setMockInstalled(false);
        assertFalse(DistantHorizonsBridge.isInstalled());
        assertFalse(DistantHorizonsBridge.isDhActive());

        // Farlands is OFF
        PryzmaConfig.prFarlandsDistance = 0;
        assertFalse(PryzmaConfig.isFarlandsActive());

        // Turn Farlands ON
        PryzmaConfig.prFarlandsDistance = 64;
        assertTrue(PryzmaConfig.isFarlandsActive());
    }

    @Test
    void whenDhActive_farlandsIsLockedAndDisabled() {
        DistantHorizonsBridge.setMockInstalled(true);
        DistantHorizonsBridge.setMockDhActive(true);

        assertTrue(DistantHorizonsBridge.isInstalled());
        assertTrue(DistantHorizonsBridge.isDhActive());

        // Even if config holds a distance value, Farlands must be inactive
        PryzmaConfig.prFarlandsDistance = 64;
        assertFalse(PryzmaConfig.isFarlandsActive(), "Farlands must not be active when DH is active");
    }

    @Test
    void whenDhInstalledButInactive_farlandsCanBeUsed() {
        DistantHorizonsBridge.setMockInstalled(true);
        DistantHorizonsBridge.setMockDhActive(false);

        assertTrue(DistantHorizonsBridge.isInstalled());
        assertFalse(DistantHorizonsBridge.isDhActive());

        PryzmaConfig.prFarlandsDistance = 128;
        assertTrue(PryzmaConfig.isFarlandsActive());
    }

    @Test
    void farlandsNeverSuppressesDistantHorizons() {
        DistantHorizonsBridge.setMockInstalled(true);

        assertFalse(DistantHorizonsBridge.isFarlandsSuppressionActive());

        DistantHorizonsBridge.onFarlandsChanged(true);
        assertFalse(DistantHorizonsBridge.isFarlandsSuppressionActive());

        DistantHorizonsBridge.onFarlandsChanged(false);
        assertFalse(DistantHorizonsBridge.isFarlandsSuppressionActive());

        // syncWithFarlandsState idempotent test
        DistantHorizonsBridge.syncWithFarlandsState(true);
        assertFalse(DistantHorizonsBridge.isFarlandsSuppressionActive());
        DistantHorizonsBridge.syncWithFarlandsState(false);
        assertFalse(DistantHorizonsBridge.isFarlandsSuppressionActive());
    }
}
