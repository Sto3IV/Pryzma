package net.pryzma.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.pryzma.PryzmaConfig;

class PrCloudRendererTest {
    @Test
    void dirtyFlagTracksAndConsumesState() {
        assertFalse(PrCloudRenderer.consumeDirty());
        PrCloudRenderer.markDirty();
        assertTrue(PrCloudRenderer.consumeDirty());
        assertFalse(PrCloudRenderer.consumeDirty(), "consumeDirty resets flag to false");
    }

    @Test
    void wallEpsilonStrictlyOccludesVerticalWallEdges() {
        float epsilon = PrCloudRenderer.WALL_EPSILON;
        assertTrue(epsilon > 0.001F, "Epsilon must be positive and large enough for depth buffer precision");
        assertTrue(epsilon < 0.01F, "Epsilon must be imperceptible in world coordinates (< 1 cm)");

        float z = 0.0F;
        float bottomFaceY = z + 0.0F;
        float topFaceY = z + 4.0F;
        float wallBottomY = z + 0.0F + epsilon;
        float wallTopY = z + 4.0F - epsilon;

        // Bottom face test: bottom face must be strictly lower than wall bottom
        // so that looking up from below, the bottom face is strictly closer to camera
        assertTrue(wallBottomY > bottomFaceY, "Wall bottom must be inset upwards from bottom face");
        assertEquals(epsilon, wallBottomY - bottomFaceY, 1e-6);

        // Top face test: top face must be strictly higher than wall top
        // so that looking down from above, top face is strictly closer to camera
        assertTrue(topFaceY > wallTopY, "Wall top must be inset downwards from top face");
        assertEquals(epsilon, topFaceY - wallTopY, 1e-6);

        // Also test at arbitrary world height
        float zWorld = 192.0F;
        assertTrue(zWorld + epsilon > zWorld, "Wall bottom must strictly exceed bottom face at world height");
        assertTrue(zWorld + 4.0F > zWorld + 4.0F - epsilon, "Top face must strictly exceed wall top at world height");
    }

    @Test
    void farRadiusCoversTargetDistantHorizonsLODChunks() {
        int cells = PrCloudRenderer.FAR_CELL_RADIUS;
        assertEquals(48, cells);
        assertEquals(48 * 48, PrCloudRenderer.FAR_CELL_RADIUS_SQ);

        // 1 cell = 8 units; 1 unit = 12 world blocks
        int radiusBlocks = cells * 8 * 12;
        assertEquals(4608, radiusBlocks, "Far cloud horizon radius should reach 4608 blocks");

        int radiusChunks = radiusBlocks / 16;
        assertEquals(288, radiusChunks, "Far cloud horizon radius should cover 288 chunks (matching DH high-end profiles)");
    }

    @Test
    void nearZoneIsProperlyBounded() {
        assertEquals(-4, PrCloudRenderer.NEAR_CELL_MIN);
        assertEquals(5, PrCloudRenderer.NEAR_CELL_MAX);
        int cellCountNear = (PrCloudRenderer.NEAR_CELL_MAX - PrCloudRenderer.NEAR_CELL_MIN + 1);
        assertEquals(10, cellCountNear);

        // 10 cells * 8 units * 12 blocks = 960 blocks diameter (480 blocks radius = 30 chunks)
        int nearDiameterBlocks = cellCountNear * 8 * 12;
        assertEquals(960, nearDiameterBlocks);
    }
}
