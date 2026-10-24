package net.pryzma.lod;

import java.util.Arrays;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/**
 * The geometry decisions of the Farlands LOD, kept free of game state: detail levels by distance, the
 * vanilla-covered radius the LOD stays out of, the LOD pass's depth range and its fog.
 */
public final class LodMath {
    /** Distance (blocks, camera to the nearest point of a sector) below which levels 0..2 apply. */
    static final int[] LEVEL_LIMITS = {256, 512, 1024};
    /** Distance a sector must pass beyond a level boundary before its level changes. */
    public static final int LEVEL_HYSTERESIS = 32;
    /** Largest radius {@link #spiral} lays out: 2049² offsets, far beyond any LOD distance. */
    public static final int MAX_SPIRAL_RADIUS = 1024;
    private static final float MIN_NEAR = 0.05F;
    private static final float MAX_NEAR = 64.0F;

    private static final Int2ObjectOpenHashMap<int[]> SPIRAL_CACHE = new Int2ObjectOpenHashMap<>();

    private LodMath() {
    }

    /**
     * Detail level of a sector at {@code distance} blocks: 0 near the camera, {@link NativeLodMesher#MAX_LEVEL} far
     * away. A sector at {@code current} (-1 for none) keeps it until the distance clears the boundary by
     * {@link #LEVEL_HYSTERESIS}, so a camera moving along a boundary does not remesh the sector back and forth.
     */
    public static int levelFor(double distance, int current) {
        return levelFor(distance, current, 1.0F);
    }

    /**
     * Detail level scaled by an optical zoom factor (e.g. 4.0 for OptiFine/Pryzma zoom).
     * Effective distance scales down proportionally, refining distant features on the screen.
     */
    public static int levelFor(double distance, int current, float zoomFactor) {
        double effectiveDist = zoomFactor > 1.0F ? distance / zoomFactor : distance;
        int level = baseLevel(effectiveDist);
        if (current < 0 || current > NativeLodMesher.MAX_LEVEL || current == level) {
            return level;
        }
        if (level > current && effectiveDist < LEVEL_LIMITS[current] + LEVEL_HYSTERESIS) {
            return current;
        }
        if (level < current && effectiveDist > LEVEL_LIMITS[current - 1] - LEVEL_HYSTERESIS) {
            return current;
        }
        return level;
    }

    static int baseLevel(double distance) {
        for (int i = 0; i < LEVEL_LIMITS.length; i++) {
            if (distance < LEVEL_LIMITS[i]) {
                return i;
            }
        }
        return LEVEL_LIMITS.length;
    }

    /**
     * Radius (blocks) around the camera that vanilla terrain covers at render distance {@code renderDistance}
     * chunks: vanilla draws every chunk whose distance minus one is below the render distance, and the camera can sit
     * anywhere in its chunk, so one chunk less is always drawn. The LOD discards fragments closer than this.
     */
    public static float clipRadius(int renderDistance) {
        return Math.max(0, (renderDistance - 1) * 16);
    }

    /**
     * Near plane of the LOD pass. Nothing closer than {@code clipRadius} is drawn, and a fragment that far away
     * cannot have an eye depth below an eighth of it inside any sane field of view, so the near plane moves out
     * and the depth buffer keeps its precision for distant terrain.
     */
    public static float nearPlane(float clipRadius) {
        return Math.clamp(clipRadius / 8.0F, MIN_NEAR, MAX_NEAR);
    }

    /** Far plane of the LOD pass: beyond the farthest corner of the farthest sector and the build height. */
    public static float farPlane(int lodRadiusBlocks) {
        return (lodRadiusBlocks + 2.0F * LodDataStore.SECTOR_BLOCKS) * 1.5F + 512.0F;
    }

    /**
     * Fog of the LOD pass, also applied to vanilla terrain while the LOD draws so both fade alike:
     * {@code {start, end}} in blocks, cylindrical like vanilla terrain fog. With fog off it starts beyond the LOD.
     */
    public static float[] fogRange(int lodRadiusBlocks, float fogStartFraction, boolean fogOff) {
        return fogRange(lodRadiusBlocks, fogStartFraction, fogOff, 2);
    }

    /**
     * Fog of the LOD pass with explicit fogType (1=Fast, 2=Fancy, 3=OFF).
     * Fast fog starts closer (25% of radius) for denser haze; Fancy fog starts at {@code fogStartFraction}.
     */
    public static float[] fogRange(int lodRadiusBlocks, float fogStartFraction, boolean fogOff, int fogType) {
        if (fogOff || fogType == 3) {
            float start = farPlane(lodRadiusBlocks) * 2.0F;
            return new float[] {start, start + 1.0F};
        }
        float end = Math.max(16.0F, lodRadiusBlocks);
        float fraction = fogType == 1 ? 0.25F : Math.clamp(fogStartFraction, 0.0F, 1.0F);
        float start = Math.min(end - 1.0F, end * fraction);
        return new float[] {start, end};
    }

    /**
     * Fog range for the Farlands LOD horizon:
     * - Mode 1 (Fast): Dense fog at the LOD horizon (starts at 85% of LOD radius) to mask ungenerated void.
     * - Mode 2 (Fancy): Smooth gradient from the vanilla render distance boundary to the LOD edge (Celian's scheme).
     * - Mode 3 (OFF): Disabled completely, starts beyond the far plane.
     */
    public static float[] farlandsFogRange(int vanillaBlocks, int lodRadiusBlocks, int farlandsFogMode) {
        if (farlandsFogMode == 3) {
            float start = farPlane(lodRadiusBlocks) * 2.0F;
            return new float[] {start, start + 1.0F};
        }
        float end = Math.max(16.0F, (float) lodRadiusBlocks);
        if (farlandsFogMode == 1) {
            // Fast: dense fog at the outer edge of Farlands
            float start = Math.max((float) vanillaBlocks, end * 0.85F);
            start = Math.min(end - 1.0F, start);
            return new float[] {start, end};
        } else {
            // Fancy: smooth gradient from the vanilla border across Farlands to the LOD edge
            float start = (float) vanillaBlocks;
            if (end <= start + 16.0F) {
                start = end * 0.8F;
            }
            start = Math.min(end - 1.0F, start);
            return new float[] {start, end};
        }
    }

    /** Horizontal distance from the camera to the nearest point of sector {@code (sectorX, sectorZ)}; 0 inside it. */
    public static double sectorDistance(double cameraX, double cameraZ, int sectorX, int sectorZ) {
        double minX = (double) sectorX * LodDataStore.SECTOR_BLOCKS;
        double minZ = (double) sectorZ * LodDataStore.SECTOR_BLOCKS;
        double dx = axisGap(cameraX, minX, minX + LodDataStore.SECTOR_BLOCKS);
        double dz = axisGap(cameraZ, minZ, minZ + LodDataStore.SECTOR_BLOCKS);
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Horizontal distance from the camera to the farthest corner of sector {@code (sectorX, sectorZ)}. */
    public static double sectorFarthest(double cameraX, double cameraZ, int sectorX, int sectorZ) {
        double minX = (double) sectorX * LodDataStore.SECTOR_BLOCKS;
        double minZ = (double) sectorZ * LodDataStore.SECTOR_BLOCKS;
        double dx = Math.max(Math.abs(cameraX - minX), Math.abs(cameraX - minX - LodDataStore.SECTOR_BLOCKS));
        double dz = Math.max(Math.abs(cameraZ - minZ), Math.abs(cameraZ - minZ - LodDataStore.SECTOR_BLOCKS));
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * Whether the whole sector lies where vanilla terrain is drawn: every point of it horizontally closer than
     * {@code clipRadius}, the cylinder the LOD shader discards. Such a sector cannot produce a fragment.
     */
    public static boolean insideClip(double cameraX, double cameraZ, int sectorX, int sectorZ, float clipRadius) {
        return sectorFarthest(cameraX, cameraZ, sectorX, sectorZ) < clipRadius;
    }

    /**
     * Chunk offsets within {@code radius} chunks (Euclidean, inclusive) as {@code dx, dz} pairs, nearest first;
     * offsets at the same distance keep a fixed order. {@code radius} is 0..{@value #MAX_SPIRAL_RADIUS}.
     */
    public static synchronized int[] spiral(int radius) {
        if (radius < 0 || radius > MAX_SPIRAL_RADIUS) {
            throw new IllegalArgumentException("Spiral radius " + radius + " outside 0.." + MAX_SPIRAL_RADIUS);
        }
        int[] cached = SPIRAL_CACHE.get(radius);
        if (cached != null) {
            return cached;
        }
        int side = 2 * radius + 1;
        long[] packed = new long[side * side];
        int count = 0;
        long limit = (long) radius * radius;
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                long distance = (long) dx * dx + (long) dz * dz;
                if (distance <= limit) {
                    // The squared distance in the high bits sorts the entries; both offsets ride along in the low 32.
                    packed[count++] = distance << 32 | (long) (dx + radius) << 16 | dz + radius;
                }
            }
        }
        Arrays.sort(packed, 0, count);
        int[] out = new int[count * 2];
        for (int i = 0; i < count; i++) {
            out[2 * i] = (int) (packed[i] >>> 16 & 0xFFFF) - radius;
            out[2 * i + 1] = (int) (packed[i] & 0xFFFF) - radius;
        }
        SPIRAL_CACHE.put(radius, out);
        return out;
    }

    /**
     * {@code projection} with its depth range replaced by {@code [near, far]}, into {@code dest}.
     *
     * <p>The game's projection is {@code Z * perspective * X} ({@code GameRenderer.renderLevel}): X holds view
     * bobbing and the nausea warp, both affine, and the zoom Z only mixes w into the x and y rows. The w row of such a
     * matrix is the negated z row of X, and its z row is {@code A * Xz + B * (0, 0, 0, 1)} with A and B fixed by the
     * planes alone. Rebuilding the z row from the w row therefore changes the planes and nothing else: field of view,
     * bobbing and zoom stay exactly as vanilla computed them, so LOD and vanilla terrain line up pixel for pixel.
     *
     * @return false (and {@code dest} a plain copy) for a projection without a perspective w row, e.g. orthographic
     */
    public static boolean withDepthRange(Matrix4fc projection, float near, float far, Matrix4f dest) {
        float w0 = projection.m03();
        float w1 = projection.m13();
        float w2 = projection.m23();
        float w3 = projection.m33();
        if (!(w0 * w0 + w1 * w1 + w2 * w2 > 1.0E-12F) || !(near > 0.0F) || !(far > near)) {
            dest.set(projection);
            return false;
        }
        float a = (far + near) / (near - far);
        float b = 2.0F * far * near / (near - far);
        dest.set(projection.m00(), projection.m01(), -a * w0, w0,
                projection.m10(), projection.m11(), -a * w1, w1,
                projection.m20(), projection.m21(), -a * w2, w2,
                projection.m30(), projection.m31(), -a * w3 + b, w3);
        return true;
    }

    private static double axisGap(double value, double min, double max) {
        return value < min ? min - value : value > max ? value - max : 0.0;
    }
}
