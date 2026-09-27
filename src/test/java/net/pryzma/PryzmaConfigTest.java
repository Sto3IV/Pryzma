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
}
