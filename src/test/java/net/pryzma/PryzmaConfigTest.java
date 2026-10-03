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
}
