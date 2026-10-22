package net.pryzma.lod;

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
        PryzmaConfig.prFogType = 2; // Fancy fog
    }

    @Test
    void whenDhNotInstalled_farlandsFunctionsNormally() {
        DistantHorizonsBridge.setMockInstalled(false);
        assertFalse(DistantHorizonsBridge.isInstalled());
        assertFalse(DistantHorizonsBridge.isDhActive());

        // Farlands is OFF
        PryzmaConfig.prFarlandsDistance = 0;
        assertFalse(PryzmaConfig.isFarlandsActive());
        assertFalse(PryzmaLod.enabled());
        assertFalse(LodShaderSupport.isEnabled());
        assertFalse(PryzmaConfig.isFogOff());
        assertTrue(PryzmaConfig.isFogFancy());

        // Turn Farlands ON
        PryzmaConfig.prFarlandsDistance = 64;
        assertTrue(PryzmaConfig.isFarlandsActive());
        assertTrue(PryzmaLod.enabled());
        assertTrue(LodShaderSupport.isEnabled());
        assertTrue(PryzmaConfig.isFogOff());
        assertFalse(PryzmaConfig.isFogFancy());
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
        assertFalse(PryzmaLod.enabled(), "PryzmaLod.enabled must be false when DH is active");
        assertFalse(LodShaderSupport.isEnabled(), "LodShaderSupport must not build DH contract when DH is active");

        // Fog should NOT be forced OFF by Farlands
        assertFalse(PryzmaConfig.isFogOff());
        assertTrue(PryzmaConfig.isFogFancy());
    }

    @Test
    void whenDhInstalledButInactive_farlandsCanBeUsed() {
        DistantHorizonsBridge.setMockInstalled(true);
        DistantHorizonsBridge.setMockDhActive(false);

        assertTrue(DistantHorizonsBridge.isInstalled());
        assertFalse(DistantHorizonsBridge.isDhActive());

        PryzmaConfig.prFarlandsDistance = 128;
        assertTrue(PryzmaConfig.isFarlandsActive());
        assertTrue(PryzmaLod.enabled());
        assertTrue(LodShaderSupport.isEnabled());
        assertTrue(PryzmaConfig.isFogOff());
    }

    @Test
    void farlandsSuppressionStateTracksBidirectionalChanges() {
        DistantHorizonsBridge.setMockInstalled(true);

        assertFalse(DistantHorizonsBridge.isFarlandsSuppressionActive());

        DistantHorizonsBridge.onFarlandsChanged(true);
        assertTrue(DistantHorizonsBridge.isFarlandsSuppressionActive());

        DistantHorizonsBridge.onFarlandsChanged(false);
        assertFalse(DistantHorizonsBridge.isFarlandsSuppressionActive());

        // syncWithFarlandsState idempotent test
        DistantHorizonsBridge.syncWithFarlandsState(true);
        assertTrue(DistantHorizonsBridge.isFarlandsSuppressionActive());
        DistantHorizonsBridge.syncWithFarlandsState(true);
        assertTrue(DistantHorizonsBridge.isFarlandsSuppressionActive());

        DistantHorizonsBridge.syncWithFarlandsState(false);
        assertFalse(DistantHorizonsBridge.isFarlandsSuppressionActive());
    }
}
