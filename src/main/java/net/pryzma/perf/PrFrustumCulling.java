package net.pryzma.perf;

import net.minecraft.world.phys.AABB;

/**
 * Frustum tests for boxes with non-finite bounds.
 *
 * <p>NeoForge 21.1 dropped {@code IBlockEntityExtension.INFINITE_EXTENT_AABB} and only exits
 * early when all six bounds of a box are infinite ({@code AABB.isInfinite()}). A box that is
 * infinite on some axes still reaches JOML's plane test, where a zero plane component times an
 * infinite bound is {@code NaN}; every comparison with {@code NaN} is false, so the box is culled
 * whenever the camera is axis aligned. Clamping the bounds to a range far beyond any far plane
 * keeps the test exact: nothing outside that range can intersect the frustum anyway.
 */
public final class PrFrustumCulling {
    /** Beyond every far plane the game uses, and still well inside float precision near the camera. */
    static final double EXTENT = 1.0E6;

    private PrFrustumCulling() {
    }

    public static boolean isFinite(AABB box) {
        return Double.isFinite(box.minX) && Double.isFinite(box.minY) && Double.isFinite(box.minZ)
                && Double.isFinite(box.maxX) && Double.isFinite(box.maxY) && Double.isFinite(box.maxZ);
    }

    /** A {@code NaN} bound carries no position at all; such a box is treated as visible. */
    public static boolean hasNaN(AABB box) {
        return Double.isNaN(box.minX) || Double.isNaN(box.minY) || Double.isNaN(box.minZ)
                || Double.isNaN(box.maxX) || Double.isNaN(box.maxY) || Double.isNaN(box.maxZ);
    }

    /** One bound clamped to {@code camera ± EXTENT}. */
    public static double clamp(double bound, double camera) {
        return Math.clamp(bound, camera - EXTENT, camera + EXTENT);
    }

    /** The box with every bound clamped around the camera; finite whenever the box has no {@code NaN}. */
    public static AABB clamped(AABB box, double camX, double camY, double camZ) {
        return new AABB(clamp(box.minX, camX), clamp(box.minY, camY), clamp(box.minZ, camZ),
                clamp(box.maxX, camX), clamp(box.maxY, camY), clamp(box.maxZ, camZ));
    }
}
