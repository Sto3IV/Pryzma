package net.pryzma;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

class PryzmaConfigTest {
    @Test
    void removeRealmsDefaultsOnAndRoundTripsThroughTheOptionsFile() {
        boolean saved = PryzmaConfig.prRemoveRealms;
        try {
            assertTrue(saved, "Remove Realms is on by default");
            PryzmaConfig.apply(Map.of("prRemoveRealms", "false"));
            assertFalse(PryzmaConfig.prRemoveRealms);
            assertEquals("false", PryzmaConfig.snapshot().get("prRemoveRealms"));
            PryzmaConfig.apply(Map.of());
            assertFalse(PryzmaConfig.prRemoveRealms, "a file without the key keeps the current value");
            PryzmaConfig.apply(Map.of("prRemoveRealms", "true"));
            assertEquals("true", PryzmaConfig.snapshot().get("prRemoveRealms"));
        } finally {
            PryzmaConfig.prRemoveRealms = saved;
        }
    }

    @Test
    void telemetryAttributionDefaultsOffAndRoundTripsThroughTheOptionsFile() {
        boolean saved = PryzmaConfig.prTelemetryAttribution;
        try {
            assertFalse(saved, "Telemetry & Attribution buttons are hidden by default");
            PryzmaConfig.apply(Map.of("prTelemetryAttribution", "true"));
            assertTrue(PryzmaConfig.prTelemetryAttribution);
            assertEquals("true", PryzmaConfig.snapshot().get("prTelemetryAttribution"));
            PryzmaConfig.apply(Map.of());
            assertTrue(PryzmaConfig.prTelemetryAttribution, "a file without the key keeps the current value");
            PryzmaConfig.apply(Map.of("prTelemetryAttribution", "false"));
            assertEquals("false", PryzmaConfig.snapshot().get("prTelemetryAttribution"));
        } finally {
            PryzmaConfig.prTelemetryAttribution = saved;
        }
    }

    @Test
    void farlandsDistanceIsOffByDefaultAndAcceptsOnlyItsSteps() {
        int saved = PryzmaConfig.prFarlandsDistance;
        try {
            assertEquals(0, saved, "Farlands LOD ships off");
            for (int step : PryzmaConfig.FARLANDS_DISTANCE_VALUES) {
                PryzmaConfig.apply(Map.of("prFarlandsDistance", Integer.toString(step)));
                assertEquals(step, PryzmaConfig.prFarlandsDistance);
                assertEquals(Integer.toString(step), PryzmaConfig.snapshot().get("prFarlandsDistance"));
            }
            PryzmaConfig.apply(Map.of());
            assertEquals(1024, PryzmaConfig.prFarlandsDistance, "a file without the key keeps the current value");
            PryzmaConfig.apply(Map.of("prFarlandsDistance", "100"));
            assertEquals(0, PryzmaConfig.prFarlandsDistance, "an unknown value falls back to off");
            PryzmaConfig.apply(Map.of("prFarlandsDistance", "far"));
            assertEquals(0, PryzmaConfig.prFarlandsDistance, "so does garbage");
        } finally {
            PryzmaConfig.prFarlandsDistance = saved;
        }
    }

    @Test
    void detailDistanceIsOffByDefaultAndAcceptsOnlyItsSteps() {
        int saved = PryzmaConfig.prDetailDistance;
        try {
            assertEquals(0, saved, "B4 ships off");
            PryzmaConfig.apply(Map.of("prDetailDistance", "64"));
            assertEquals(64, PryzmaConfig.prDetailDistance);
            assertEquals("64", PryzmaConfig.snapshot().get("prDetailDistance"));
            PryzmaConfig.apply(Map.of("prDetailDistance", "50"));
            assertEquals(0, PryzmaConfig.prDetailDistance, "an unknown value falls back to off");
        } finally {
            PryzmaConfig.prDetailDistance = saved;
        }
    }

    @Test
    void fogTypeAcceptsFastFancyOffAndHasHelpers() {
        int saved = PryzmaConfig.prFogType;
        try {
            PryzmaConfig.apply(Map.of("prFogType", "1"));
            assertEquals(1, PryzmaConfig.prFogType);
            assertTrue(PryzmaConfig.isFogFast());
            assertFalse(PryzmaConfig.isFogFancy());
            assertFalse(PryzmaConfig.isFogOff());

            PryzmaConfig.apply(Map.of("prFogType", "2"));
            assertEquals(2, PryzmaConfig.prFogType);
            assertFalse(PryzmaConfig.isFogFast());
            assertTrue(PryzmaConfig.isFogFancy());
            assertFalse(PryzmaConfig.isFogOff());

            PryzmaConfig.apply(Map.of("prFogType", "3"));
            assertEquals(3, PryzmaConfig.prFogType);
            assertFalse(PryzmaConfig.isFogFast());
            assertFalse(PryzmaConfig.isFogFancy());
            assertTrue(PryzmaConfig.isFogOff());

            // Clamps outside 1..3
            PryzmaConfig.apply(Map.of("prFogType", "0"));
            assertEquals(1, PryzmaConfig.prFogType);
            PryzmaConfig.apply(Map.of("prFogType", "4"));
            assertEquals(3, PryzmaConfig.prFogType);
        } finally {
            PryzmaConfig.prFogType = saved;
        }
    }

    @Test
    void farlandsForcesFogOff() {
        int savedFog = PryzmaConfig.prFogType;
        int savedFarlands = PryzmaConfig.prFarlandsDistance;
        try {
            PryzmaConfig.prFogType = 2; // Fancy
            PryzmaConfig.prFarlandsDistance = 0;
            assertFalse(PryzmaConfig.isFogOff());
            assertTrue(PryzmaConfig.isFogFancy());

            PryzmaConfig.prFarlandsDistance = 128;
            assertTrue(PryzmaConfig.isFogOff());
            assertFalse(PryzmaConfig.isFogFancy());
            assertFalse(PryzmaConfig.isFogFast());
        } finally {
            PryzmaConfig.prFogType = savedFog;
            PryzmaConfig.prFarlandsDistance = savedFarlands;
        }
    }

    @Test
    void farlandsFogAcceptsFastFancyOffAndLegacyBooleans() {
        int saved = PryzmaConfig.prFarlandsFog;
        try {
            assertEquals(2, saved, "Farlands Fog defaults to 2 (Fancy)");
            PryzmaConfig.apply(Map.of("prFarlandsFog", "1"));
            assertEquals(1, PryzmaConfig.prFarlandsFog);
            assertTrue(PryzmaConfig.isFarlandsFogFast());
            assertFalse(PryzmaConfig.isFarlandsFogFancy());
            assertFalse(PryzmaConfig.isFarlandsFogOff());

            PryzmaConfig.apply(Map.of("prFarlandsFog", "2"));
            assertEquals(2, PryzmaConfig.prFarlandsFog);
            assertFalse(PryzmaConfig.isFarlandsFogFast());
            assertTrue(PryzmaConfig.isFarlandsFogFancy());
            assertFalse(PryzmaConfig.isFarlandsFogOff());

            PryzmaConfig.apply(Map.of("prFarlandsFog", "3"));
            assertEquals(3, PryzmaConfig.prFarlandsFog);
            assertFalse(PryzmaConfig.isFarlandsFogFast());
            assertFalse(PryzmaConfig.isFarlandsFogFancy());
            assertTrue(PryzmaConfig.isFarlandsFogOff());

            // Legacy boolean string backward compatibility
            PryzmaConfig.apply(Map.of("prFarlandsFog", "true"));
            assertEquals(2, PryzmaConfig.prFarlandsFog);
            PryzmaConfig.apply(Map.of("prFarlandsFog", "false"));
            assertEquals(3, PryzmaConfig.prFarlandsFog);

            // Clamping
            PryzmaConfig.apply(Map.of("prFarlandsFog", "0"));
            assertEquals(1, PryzmaConfig.prFarlandsFog);
            PryzmaConfig.apply(Map.of("prFarlandsFog", "9"));
            assertEquals(3, PryzmaConfig.prFarlandsFog);
        } finally {
            PryzmaConfig.prFarlandsFog = saved;
        }
    }
}
