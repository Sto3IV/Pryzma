package net.pryzma.lod;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

/** Farlands LOD geometry decisions: detail levels, the vanilla radius, the depth range, fog and the read spiral. */
class LodMathTest {
    @Test
    void detailCoarsensWithDistance() {
        assertEquals(0, LodMath.levelFor(0.0, -1));
        assertEquals(0, LodMath.levelFor(255.0, -1));
        assertEquals(1, LodMath.levelFor(256.0, -1));
        assertEquals(2, LodMath.levelFor(600.0, -1));
        assertEquals(NativeLodMesher.MAX_LEVEL, LodMath.levelFor(1024.0, -1));
        assertEquals(NativeLodMesher.MAX_LEVEL, LodMath.levelFor(1.0E6, -1));
    }

    @Test
    void detailRefinesUnderZoom() {
        // At 1000 blocks with 4x zoom, effective distance is 250 blocks -> Level 0 instead of Level 2
        assertEquals(2, LodMath.levelFor(1000.0, -1, 1.0F));
        assertEquals(0, LodMath.levelFor(1000.0, -1, 4.0F));
        // At 2000 blocks with 4x zoom, effective distance is 500 blocks -> Level 1 instead of Level 3
        assertEquals(3, LodMath.levelFor(2000.0, -1, 1.0F));
        assertEquals(1, LodMath.levelFor(2000.0, -1, 4.0F));
    }

    @Test
    void levelsHoldAcrossABoundaryUntilTheHysteresisIsCleared() {
        assertEquals(0, LodMath.levelFor(270.0, 0), "just past 256: stays at 0");
        assertEquals(1, LodMath.levelFor(256.0 + LodMath.LEVEL_HYSTERESIS, 0), "cleared");
        assertEquals(1, LodMath.levelFor(240.0, 1), "just inside 256: stays at 1");
        assertEquals(0, LodMath.levelFor(256.0 - LodMath.LEVEL_HYSTERESIS - 1.0, 1));
        assertEquals(3, LodMath.levelFor(1000.0, 3));
        assertEquals(2, LodMath.levelFor(900.0, 3));
        assertEquals(0, LodMath.levelFor(100.0, 3), "a jump over several levels lands on the right one");
        assertEquals(3, LodMath.levelFor(2000.0, 0));
        assertEquals(1, LodMath.levelFor(300.0, 7), "an unknown current level is ignored");
    }

    @Test
    void clipRadiusIsOneChunkInsideTheRenderDistance() {
        assertEquals(176.0F, LodMath.clipRadius(12));
        assertEquals(0.0F, LodMath.clipRadius(1));
        assertEquals(0.0F, LodMath.clipRadius(0));
        assertEquals(0.05F, LodMath.nearPlane(0.0F));
        assertEquals(22.0F, LodMath.nearPlane(176.0F));
        assertEquals(64.0F, LodMath.nearPlane(10_000.0F));
        assertEquals((1024.0F + 512.0F) * 1.5F + 512.0F, LodMath.farPlane(1024));
    }

    @Test
    void farPlaneCoversTheFarthestRetainedSector() {
        for (int chunks : new int[] {32, 64, 96, 128}) {
            int radius = chunks * 16;
            // Meshes are kept up to one sector beyond the radius; their far corner and the build limits must fit.
            double horizontal = radius + LodDataStore.SECTOR_BLOCKS + Math.sqrt(2.0) * LodDataStore.SECTOR_BLOCKS;
            double farthest = Math.sqrt(horizontal * horizontal + 384.0 * 384.0);
            assertTrue(LodMath.farPlane(radius) > farthest, chunks + " chunks");
        }
    }

    @Test
    void fogEndsAtTheLodRadius() {
        assertArrayEquals(new float[] {819.2F, 1024.0F}, LodMath.fogRange(1024, 0.8F, false), 1.0E-3F);
        assertArrayEquals(new float[] {8.0F, 16.0F}, LodMath.fogRange(0, 0.5F, false), 1.0E-6F);
        assertArrayEquals(new float[] {1023.0F, 1024.0F}, LodMath.fogRange(1024, 2.0F, false), 1.0E-6F);
        // Fast fog starts at 25% of radius
        assertArrayEquals(new float[] {256.0F, 1024.0F}, LodMath.fogRange(1024, 0.8F, false, 1), 1.0E-3F);
        // Fancy fog uses fraction
        assertArrayEquals(new float[] {819.2F, 1024.0F}, LodMath.fogRange(1024, 0.8F, false, 2), 1.0E-3F);
        float[] off = LodMath.fogRange(1024, 0.8F, true);
        assertTrue(off[0] > LodMath.farPlane(1024), "fog off starts beyond everything drawn");
        assertEquals(off[0] + 1.0F, off[1]);
        float[] offType = LodMath.fogRange(1024, 0.8F, false, 3);
        assertEquals(off[0], offType[0]);
    }

    @Test
    void farlandsFogRangeModes() {
        // Fast (1): dense fog at outer horizon (85% of lod radius)
        assertArrayEquals(new float[] {870.4F, 1024.0F}, LodMath.farlandsFogRange(256, 1024, 1), 1.0E-3F);
        // Fast clamping if vanilla border is beyond 85%
        assertArrayEquals(new float[] {900.0F, 1000.0F}, LodMath.farlandsFogRange(900, 1000, 1), 1.0E-3F);

        // Fancy (2): smooth gradient starting at vanilla border all the way to lod radius
        assertArrayEquals(new float[] {256.0F, 1024.0F}, LodMath.farlandsFogRange(256, 1024, 2), 1.0E-3F);
        // Fancy fallback when Farlands span is very narrow (< 16 blocks)
        assertArrayEquals(new float[] {88.0F, 110.0F}, LodMath.farlandsFogRange(100, 110, 2), 1.0E-3F);

        // OFF (3): fog pushed far beyond render distance
        float[] off = LodMath.farlandsFogRange(256, 1024, 3);
        assertTrue(off[0] > LodMath.farPlane(1024), "farlands fog off starts beyond farPlane");
        assertEquals(off[0] + 1.0F, off[1]);
    }

    @Test
    void fogClosesAtTheDrawnHorizon() {
        float[] configured = LodMath.farlandsFogRange(192, 1024, 2);
        // Saved-world intake can stop far inside the configured radius; the fade must end where the terrain does.
        assertArrayEquals(new float[] {192.0F, 400.0F}, LodMath.clampFogToHorizon(configured, 400.0F), 1.0E-3F);
        // A horizon inside the configured start still keeps a fade band in front of it.
        assertArrayEquals(new float[] {144.0F, 192.0F}, LodMath.clampFogToHorizon(configured, 192.0F), 1.0E-3F);
        // Dense (Fast) fog whose start is beyond the horizon is pulled in too.
        assertArrayEquals(new float[] {300.0F, 400.0F},
                LodMath.clampFogToHorizon(LodMath.farlandsFogRange(256, 1024, 1), 400.0F), 1.0E-3F);
        // Nothing to clamp: terrain reaches the configured end, or nothing was drawn.
        assertArrayEquals(configured, LodMath.clampFogToHorizon(configured, 2048.0F), 1.0E-6F);
        assertArrayEquals(configured, LodMath.clampFogToHorizon(configured, 0.0F), 1.0E-6F);
    }

    @Test
    void sectorDistances() {
        assertEquals(0.0, LodMath.sectorDistance(128.0, 128.0, 0, 0));
        assertEquals(10.0, LodMath.sectorDistance(-10.0, 0.0, 0, 0), 1.0E-9);
        assertEquals(512.0, LodMath.sectorDistance(0.0, 0.0, 2, 0), 1.0E-9);
        assertEquals(Math.sqrt(2.0) * 44.0, LodMath.sectorDistance(300.0, 300.0, 0, 0), 1.0E-9);
        assertEquals(Math.sqrt(2.0) * 256.0, LodMath.sectorFarthest(0.0, 0.0, 0, 0), 1.0E-9);
        assertEquals(Math.sqrt(384.0 * 384.0 + 128.0 * 128.0), LodMath.sectorFarthest(128.0, 128.0, 1, 0), 1.0E-9);
        assertTrue(LodMath.insideClip(128.0, 128.0, 0, 0, 400.0F));
        assertFalse(LodMath.insideClip(128.0, 128.0, 1, 0, 400.0F));
        assertFalse(LodMath.insideClip(128.0, 128.0, 0, 0, 100.0F));
    }

    @Test
    void depthRangeSwapKeepsFieldOfViewZoomAndBobbing() {
        float fov = (float) Math.toRadians(70.0);
        float aspect = 2560.0F / 1440.0F;
        // GameRenderer: zoom (translate, scale) * perspective * view bobbing * nausea.
        Matrix4f bob = new Matrix4f().translate(0.03F, -0.05F, 0.0F).rotateZ(0.02F).rotateX(0.015F);
        Matrix4f nausea = new Matrix4f().rotate(0.4F, 0.0F, 0.7071F, 0.7071F).scale(1.2F, 1.0F, 1.0F).rotate(-0.4F, 0.0F, 0.7071F, 0.7071F);
        Matrix4f game = new Matrix4f().translate(0.1F, -0.2F, 0.0F).scale(1.5F, 1.5F, 1.0F)
                .perspective(fov, aspect, 0.05F, 768.0F).mul(bob).mul(nausea);
        Matrix4f expected = new Matrix4f().translate(0.1F, -0.2F, 0.0F).scale(1.5F, 1.5F, 1.0F)
                .perspective(fov, aspect, 60.0F, 4000.0F).mul(bob).mul(nausea);
        Matrix4f actual = new Matrix4f();
        assertTrue(LodMath.withDepthRange(game, 60.0F, 4000.0F, actual));
        float[] e = expected.get(new float[16]);
        float[] a = actual.get(new float[16]);
        for (int i = 0; i < 16; i++) {
            assertEquals(e[i], a[i], 1.0E-4F * Math.max(1.0F, Math.abs(e[i])), "element " + i);
        }
        // Same screen position, new depth: a point 1000 blocks ahead lands at the same x and y.
        Vector4f point = new Vector4f(5.0F, -3.0F, -1000.0F, 1.0F);
        Vector4f before = game.transform(new Vector4f(point));
        Vector4f after = actual.transform(new Vector4f(point));
        assertEquals(before.x / before.w, after.x / after.w, 1.0E-5F);
        assertEquals(before.y / before.w, after.y / after.w, 1.0E-5F);
        float depth = after.z / after.w;
        assertTrue(depth > -1.0F && depth < 1.0F, "inside the new depth range: " + depth);
    }

    @Test
    void nonPerspectiveProjectionsAreLeftAlone() {
        Matrix4f ortho = new Matrix4f().ortho(-10.0F, 10.0F, -10.0F, 10.0F, 0.1F, 100.0F);
        Matrix4f dest = new Matrix4f();
        assertFalse(LodMath.withDepthRange(ortho, 1.0F, 50.0F, dest));
        assertEquals(ortho, dest);
        Matrix4f perspective = new Matrix4f().perspective(1.2F, 1.5F, 0.05F, 512.0F);
        assertFalse(LodMath.withDepthRange(perspective, 50.0F, 10.0F, dest), "far before near");
        assertEquals(perspective, dest);
        assertFalse(LodMath.withDepthRange(perspective, 0.0F, 10.0F, dest), "near at the eye");
    }

    @Test
    void spiralCoversTheDiscNearestFirstWithoutDuplicates() {
        int radius = 9;
        int[] spiral = LodMath.spiral(radius);
        int expected = 0;
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                if (dx * dx + dz * dz <= radius * radius) {
                    expected++;
                }
            }
        }
        assertEquals(expected * 2, spiral.length);
        assertEquals(0, spiral[0]);
        assertEquals(0, spiral[1]);
        Set<Long> seen = new HashSet<>();
        long last = -1;
        for (int i = 0; i < spiral.length; i += 2) {
            long distance = (long) spiral[i] * spiral[i] + (long) spiral[i + 1] * spiral[i + 1];
            assertTrue(distance <= (long) radius * radius);
            assertTrue(distance >= last, "nearest first");
            last = distance;
            assertTrue(seen.add(LodDataStore.key(spiral[i], spiral[i + 1])), "no duplicates");
        }
        assertArrayEquals(new int[] {0, 0}, LodMath.spiral(0));
        assertEquals(2 * 5, LodMath.spiral(1).length, "the centre and its four neighbours");
        assertThrows(IllegalArgumentException.class, () -> LodMath.spiral(-1));
        assertThrows(IllegalArgumentException.class, () -> LodMath.spiral(LodMath.MAX_SPIRAL_RADIUS + 1));
    }
}
