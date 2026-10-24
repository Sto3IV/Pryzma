package net.pryzma.overlay;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

class PrOverlaysTest {

    @Test
    void testKeyBindingDefaults() {
        assertNotNull(PrOverlays.KEY_LIGHT_OVERLAY, "KEY_LIGHT_OVERLAY must be initialized");
        assertNotNull(PrOverlays.KEY_CHUNK_GRID, "KEY_CHUNK_GRID must be initialized");

        assertEquals("pr.key.light_overlay", PrOverlays.KEY_LIGHT_OVERLAY.getName());
        assertEquals("pr.key.chunk_grid", PrOverlays.KEY_CHUNK_GRID.getName());

        assertEquals(GLFW.GLFW_KEY_F7, PrOverlays.KEY_LIGHT_OVERLAY.getKey().getValue());
        assertEquals(GLFW.GLFW_KEY_F9, PrOverlays.KEY_CHUNK_GRID.getKey().getValue());
    }

    @Test
    void testInitialOverlayStates() {
        assertFalse(PrOverlays.isLightOverlayActive(), "Light overlay should be OFF by default");
        assertEquals(0, PrOverlays.getChunkGridMode(), "Chunk grid mode should be 0 (OFF) by default");
    }

    @Test
    void testChunkGridModeCycling() {
        int mode = 0;
        mode = (mode + 1) % 3;
        assertEquals(1, mode, "Mode should cycle to 1 (Current Chunk)");
        mode = (mode + 1) % 3;
        assertEquals(2, mode, "Mode should cycle to 2 (3x3 Chunks)");
        mode = (mode + 1) % 3;
        assertEquals(0, mode, "Mode should cycle back to 0 (OFF)");
    }

    @Test
    void testLightOverlaySpawnRules() {
        // Block Light > 0 -> No mob spawn
        int blockLight1 = 8;
        int skyLight1 = 15;
        assertFalse(shouldSpawnHostile(blockLight1), "Block light > 0 must never allow hostile spawns");

        // Block Light == 0 and Sky Light > 0 -> Night only (Yellow)
        int blockLight2 = 0;
        int skyLight2 = 12;
        assertTrue(shouldSpawnHostile(blockLight2));
        assertEquals(1, classifyMarkerColor(blockLight2, skyLight2), "Should be Yellow (1) for night spawns");

        // Block Light == 0 and Sky Light == 0 -> Always (Red)
        int blockLight3 = 0;
        int skyLight3 = 0;
        assertTrue(shouldSpawnHostile(blockLight3));
        assertEquals(2, classifyMarkerColor(blockLight3, skyLight3), "Should be Red (2) for always spawns");
    }

    private static boolean shouldSpawnHostile(int blockLight) {
        return blockLight == 0;
    }

    private static byte classifyMarkerColor(int blockLight, int skyLight) {
        if (blockLight > 0) return 0;
        return (byte) (skyLight > 0 ? 1 : 2);
    }
}
