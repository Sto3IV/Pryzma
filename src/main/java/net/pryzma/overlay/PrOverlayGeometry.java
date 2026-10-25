package net.pryzma.overlay;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/**
 * Camera-relative quad emitter for world-space overlays.
 *
 * <p>Every overlay is emitted into {@link PrOverlayRenderType#OVERLAY_QUADS}; nothing here touches
 * {@code RenderType.lines()} or {@code glLineWidth}. A "line" becomes a four-vertex quad whose
 * width is derived from the projection so it keeps a constant pixel thickness at any distance.</p>
 *
 * <p>All helpers assume the pose stack has already been translated by {@code -cameraPosition}, so
 * the camera sits at the origin of emitted coordinates. That is what makes billboarding a cross
 * product instead of a matrix inversion.</p>
 */
public final class PrOverlayGeometry {
    /** Below this a segment is degenerate; clamp so close-up overlays stay visible. */
    private static final float MIN_HALF_WIDTH = 0.0015F;

    private PrOverlayGeometry() {
    }

    /** Camera distance of a point in overlay space (the camera is the origin). */
    public static float distance(float x, float y, float z) {
        return Mth.sqrt(x * x + y * y + z * z);
    }

    /**
     * World units covered by one framebuffer pixel at the given camera distance.
     *
     * <p>For a perspective projection the element {@code m11} of the projection matrix is
     * {@code 1 / tan(fovY / 2)}, so the vertical extent of the frustum at depth {@code d} is
     * {@code 2 * d * tan(fovY / 2)} world units == {@code framebufferHeight} pixels.</p>
     */
    public static float worldPerPixel(float distance) {
        Minecraft minecraft = Minecraft.getInstance();
        int framebufferHeight = Math.max(1, minecraft.getWindow().getHeight());
        float tanHalfFov = 1.0F / Math.max(0.05F, RenderSystem.getProjectionMatrix().m11());
        float depth = Math.max(0.5F, distance);
        return 2.0F * depth * tanHalfFov / framebufferHeight;
    }

    /** Half width, in world units, of a segment that should be {@code pixels} wide on screen. */
    public static float halfWidth(float pixels, float distance, float minWorld, float maxWorld) {
        float half = 0.5F * pixels * worldPerPixel(distance);
        return Mth.clamp(half, Math.max(MIN_HALF_WIDTH, minWorld), maxWorld);
    }

    /**
     * A segment that always faces the camera, for lines seen from every side (F9).
     *
     * <p>The width axis is {@code cross(direction, camera)}, i.e. perpendicular to both the segment
     * and the view ray, so the quad is edge-on to nobody.</p>
     */
    public static void billboard(VertexConsumer vc, PoseStack.Pose pose,
                                 float x1, float y1, float z1,
                                 float x2, float y2, float z2,
                                 float halfWidth,
                                 float r, float g, float b, float a) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        if (dx * dx + dy * dy + dz * dz < 1.0E-6F) {
            return;
        }

        float mx = (x1 + x2) * 0.5F;
        float my = (y1 + y2) * 0.5F;
        float mz = (z1 + z2) * 0.5F;

        // cross(direction, camera - midpoint), with the camera at the origin.
        float sx = dy * mz - dz * my;
        float sy = dz * mx - dx * mz;
        float sz = dx * my - dy * mx;
        float length = Mth.sqrt(sx * sx + sy * sy + sz * sz);

        if (length < 1.0E-4F) {
            // Looking straight down the segment: any perpendicular is as good as another.
            sx = -dz;
            sy = 0.0F;
            sz = dx;
            length = Mth.sqrt(sx * sx + sz * sz);
            if (length < 1.0E-4F) {
                sx = 1.0F;
                sy = 0.0F;
                sz = 0.0F;
                length = 1.0F;
            }
        }

        float scale = halfWidth / length;
        sx *= scale;
        sy *= scale;
        sz *= scale;

        quad(vc, pose,
                x1 - sx, y1 - sy, z1 - sz,
                x2 - sx, y2 - sy, z2 - sz,
                x2 + sx, y2 + sy, z2 + sz,
                x1 + sx, y1 + sy, z1 + sz,
                r, g, b, a);
    }

    /**
     * A segment that stays flat in the horizontal plane, for decals painted on a block face (F7).
     *
     * <p>The width axis is horizontal, so the quad never tilts into the block below it and never
     * clips through the face at grazing angles. Both endpoints must share the same Y.</p>
     */
    public static void flat(VertexConsumer vc, PoseStack.Pose pose,
                            float x1, float y1, float z1,
                            float x2, float y2, float z2,
                            float halfWidth,
                            float r, float g, float b, float a) {
        float dx = x2 - x1;
        float dz = z2 - z1;
        float length = Mth.sqrt(dx * dx + dz * dz);
        if (length < 1.0E-4F) {
            return;
        }

        // cross(up, direction) = (dz, 0, -dx), already horizontal.
        float sx = dz / length * halfWidth;
        float sz = -dx / length * halfWidth;

        quad(vc, pose,
                x1 - sx, y1, z1 - sz,
                x2 - sx, y1, z2 - sz,
                x2 + sx, y1, z2 + sz,
                x1 + sx, y1, z1 + sz,
                r, g, b, a);
    }

    public static void quad(VertexConsumer vc, PoseStack.Pose pose,
                            float x0, float y0, float z0,
                            float x1, float y1, float z1,
                            float x2, float y2, float z2,
                            float x3, float y3, float z3,
                            float r, float g, float b, float a) {
        vc.addVertex(pose, x0, y0, z0).setColor(r, g, b, a);
        vc.addVertex(pose, x1, y1, z1).setColor(r, g, b, a);
        vc.addVertex(pose, x2, y2, z2).setColor(r, g, b, a);
        vc.addVertex(pose, x3, y3, z3).setColor(r, g, b, a);
    }
}
