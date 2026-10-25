package net.pryzma.overlay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * Geometry-level checks for the overlay quad emitter: the billboard must stay perpendicular to the
 * view ray and keep an exact width, otherwise F9 lines would shear or disappear edge-on.
 */
class PrOverlayGeometryTest {

    private static final class Capture implements VertexConsumer {
        final List<float[]> vertices = new ArrayList<>();

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            vertices.add(new float[] {x, y, z});
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }
    }

    @Test
    void billboardIsPerpendicularToTheViewRay() {
        Capture capture = new Capture();
        // Vertical line 16 blocks away on +X; the camera sits at the origin of overlay space.
        PrOverlayGeometry.billboard(capture, new PoseStack().last(),
                16.0F, 0.0F, 0.0F, 16.0F, 10.0F, 0.0F,
                0.1F, 1.0F, 0.0F, 0.0F, 1.0F);

        assertEquals(4, capture.vertices.size(), "one segment must emit exactly one quad");

        float[] a = capture.vertices.get(0);
        float[] b = capture.vertices.get(1);
        float[] c = capture.vertices.get(2);
        float[] d = capture.vertices.get(3);

        // Width axis is +-Z here, so the whole quad stays in the X = 16 plane.
        assertEquals(16.0F, a[0], 1.0E-4F);
        assertEquals(16.0F, b[0], 1.0E-4F);
        assertEquals(0.1F, a[2], 1.0E-4F);
        assertEquals(-0.1F, d[2], 1.0E-4F);
        assertEquals(0.2F, Math.abs(a[2] - d[2]), 1.0E-4F, "quad width must be 2 * halfWidth");
        assertEquals(10.0F, Math.abs(b[1] - a[1]), 1.0E-4F, "quad must span the full segment");
        assertEquals(b[1], c[1], 1.0E-4F);
    }

    @Test
    void billboardNeverCollapsesWhenLookingDownTheLine() {
        Capture capture = new Capture();
        // The camera is on the line's axis: the cross product degenerates and must fall back.
        PrOverlayGeometry.billboard(capture, new PoseStack().last(),
                0.0F, -5.0F, 0.0F, 0.0F, 5.0F, 0.0F,
                0.05F, 1.0F, 1.0F, 1.0F, 1.0F);

        assertEquals(4, capture.vertices.size());
        float[] a = capture.vertices.get(0);
        float[] d = capture.vertices.get(3);
        float width = (float) Math.hypot(a[0] - d[0], a[2] - d[2]);
        assertEquals(0.1F, width, 1.0E-4F, "the fallback width axis must still be 2 * halfWidth");
    }

    @Test
    void flatDecalStaysInTheHorizontalPlane() {
        Capture capture = new Capture();
        PrOverlayGeometry.flat(capture, new PoseStack().last(),
                0.0F, 5.0F, 0.0F, 10.0F, 5.0F, 10.0F,
                0.05F, 0.9F, 0.0F, 0.0F, 1.0F);

        assertEquals(4, capture.vertices.size());
        for (float[] vertex : capture.vertices) {
            assertEquals(5.0F, vertex[1], 1.0E-4F, "a flat decal must not change Y");
        }

        // Width axis must be perpendicular to the segment inside the XZ plane.
        float[] a = capture.vertices.get(0);
        float[] d = capture.vertices.get(3);
        float dot = (a[0] - d[0]) * 10.0F + (a[2] - d[2]) * 10.0F;
        assertEquals(0.0F, dot, 1.0E-3F);
        assertTrue(Math.abs(a[0] - d[0]) > 0.0F && Math.abs(a[2] - d[2]) > 0.0F);
    }
}
