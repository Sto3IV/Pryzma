package net.pryzma.entity.model;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.core.Direction;

/**
 * Geometry of CEM boxes, face for face as vanilla's {@code ModelPart.Cube} (texture-offset boxes)
 * and OptiFine's face-UV cube build it, including the mirrored variants. Positions are in model
 * pixels (1/16 block), texture coordinates normalised by the part's texture size.
 */
public final class PrCemGeometry {
    private PrCemGeometry() {
    }

    /** One textured quad: corner positions {@code x y z} x4, texture coordinates {@code u v} x4, face normal. */
    public record Quad(float[] xyz, float[] uv, float nx, float ny, float nz) {
    }

    /** Every face of {@code box}; a face-UV box leaves out faces without a rectangle. */
    public static List<Quad> quads(PrJem.Box box, float texWidth, float texHeight, boolean mirror) {
        float x0 = box.x() - box.growX();
        float y0 = box.y() - box.growY();
        float z0 = box.z() - box.growZ();
        float x1 = box.x() + box.width() + box.growX();
        float y1 = box.y() + box.height() + box.growY();
        float z1 = box.z() + box.depth() + box.growZ();
        if (mirror) {
            float swap = x1;
            x1 = x0;
            x0 = swap;
        }
        float[] p0 = {x0, y0, z0};
        float[] p1 = {x1, y0, z0};
        float[] p2 = {x1, y1, z0};
        float[] p3 = {x0, y1, z0};
        float[] p4 = {x0, y0, z1};
        float[] p5 = {x1, y0, z1};
        float[] p6 = {x1, y1, z1};
        float[] p7 = {x0, y1, z1};
        List<Quad> out = new ArrayList<>(6);
        float[][] f = box.faceUvs();
        if (f == null) {
            float u = box.u();
            float v = box.v();
            float w = box.width();
            float h = box.height();
            float d = box.depth();
            // Vanilla ModelPart.Cube: the corners it names vertex, vertex1 ... map to p0 p1 p2 p3 p4 p5 p6 p7 here.
            out.add(quad(new float[][] {p5, p4, p0, p1}, u + d, v, u + d + w, v + d, texWidth, texHeight, mirror, Direction.DOWN));
            out.add(quad(new float[][] {p2, p3, p7, p6}, u + d + w, v + d, u + d + w + w, v, texWidth, texHeight, mirror, Direction.UP));
            out.add(quad(new float[][] {p0, p4, p7, p3}, u, v + d, u + d, v + d + h, texWidth, texHeight, mirror, Direction.WEST));
            out.add(quad(new float[][] {p1, p0, p3, p2}, u + d, v + d, u + d + w, v + d + h, texWidth, texHeight, mirror, Direction.NORTH));
            out.add(quad(new float[][] {p5, p1, p2, p6}, u + d + w, v + d, u + d + w + d, v + d + h, texWidth, texHeight, mirror, Direction.EAST));
            out.add(quad(new float[][] {p4, p5, p6, p7}, u + d + w + d, v + d, u + d + w + d + w, v + d + h, texWidth, texHeight, mirror, Direction.SOUTH));
            return out;
        }
        // OptiFine's face-UV cube: model space is upside down and mirrored in x, so the face drawn
        // at the world bottom is the one named up, and east / west swap.
        addReversed(out, new float[][] {p5, p4, p0, p1}, f[1], texWidth, texHeight, mirror, Direction.DOWN);
        addReversed(out, new float[][] {p2, p3, p7, p6}, f[0], texWidth, texHeight, mirror, Direction.UP);
        add(out, new float[][] {p0, p4, p7, p3}, f[5], texWidth, texHeight, mirror, Direction.WEST);
        add(out, new float[][] {p1, p0, p3, p2}, f[2], texWidth, texHeight, mirror, Direction.NORTH);
        add(out, new float[][] {p5, p1, p2, p6}, f[4], texWidth, texHeight, mirror, Direction.EAST);
        add(out, new float[][] {p4, p5, p6, p7}, f[3], texWidth, texHeight, mirror, Direction.SOUTH);
        return out;
    }

    private static void add(List<Quad> out, float[][] corners, float[] uv, float w, float h, boolean mirror, Direction dir) {
        if (uv != null) {
            out.add(quad(corners, uv[0], uv[1], uv[2], uv[3], w, h, mirror, dir));
        }
    }

    private static void addReversed(List<Quad> out, float[][] corners, float[] uv, float w, float h, boolean mirror, Direction dir) {
        if (uv != null) {
            out.add(quad(corners, uv[2], uv[3], uv[0], uv[1], w, h, mirror, dir));
        }
    }

    /** Vanilla {@code ModelPart.Polygon}: UV corners assigned as it does, order reversed when mirrored. */
    static Quad quad(float[][] corners, float u1, float v1, float u2, float v2, float texWidth, float texHeight,
            boolean mirror, Direction dir) {
        float[][] uv = {
                {u2 / texWidth, v1 / texHeight},
                {u1 / texWidth, v1 / texHeight},
                {u1 / texWidth, v2 / texHeight},
                {u2 / texWidth, v2 / texHeight}};
        int[] order = mirror ? new int[] {3, 2, 1, 0} : new int[] {0, 1, 2, 3};
        float[] xyz = new float[12];
        float[] tex = new float[8];
        for (int i = 0; i < 4; i++) {
            int k = order[i];
            xyz[i * 3] = corners[k][0];
            xyz[i * 3 + 1] = corners[k][1];
            xyz[i * 3 + 2] = corners[k][2];
            tex[i * 2] = uv[k][0];
            tex[i * 2 + 1] = uv[k][1];
        }
        Vector3f n = dir.step();
        return new Quad(xyz, tex, mirror ? -n.x() : n.x(), n.y(), n.z());
    }

    /** Emits quads exactly as {@code ModelPart.Cube.compile} does. */
    public static void render(Quad[] quads, PoseStack.Pose pose, VertexConsumer consumer, int light, int overlay, int color) {
        Matrix4f m = pose.pose();
        Vector3f normal = new Vector3f();
        Vector3f pos = new Vector3f();
        for (Quad q : quads) {
            pose.transformNormal(q.nx(), q.ny(), q.nz(), normal);
            float nx = normal.x();
            float ny = normal.y();
            float nz = normal.z();
            float[] xyz = q.xyz();
            float[] uv = q.uv();
            for (int i = 0; i < 4; i++) {
                m.transformPosition(xyz[i * 3] / 16.0F, xyz[i * 3 + 1] / 16.0F, xyz[i * 3 + 2] / 16.0F, pos);
                consumer.addVertex(pos.x(), pos.y(), pos.z(), color, uv[i * 2], uv[i * 2 + 1], overlay, light, nx, ny, nz);
            }
        }
    }
}
