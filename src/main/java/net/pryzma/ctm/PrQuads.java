package net.pryzma.ctm;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.joml.Vector3f;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockFaceUV;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.client.model.IQuadTransformer;

/** Quad copies with another sprite, full-face quads for overlays, and quad shape tests. */
final class PrQuads {
    private static final int STRIDE = IQuadTransformer.STRIDE;
    private static final int POS = IQuadTransformer.POSITION;
    private static final int UV = IQuadTransformer.UV0;
    private static final int COLOR = IQuadTransformer.COLOR;
    private static final FaceBakery BAKERY = new FaceBakery();

    private static final Map<TextureAtlasSprite, Map<BakedQuad, BakedQuad>> REMAPPED = new ConcurrentHashMap<>();
    private static final Map<TextureAtlasSprite, BakedQuad[]> FULL = new ConcurrentHashMap<>();

    private PrQuads() {
    }

    static void clearCaches() {
        REMAPPED.clear();
        FULL.clear();
    }

    /** {@code quad} with its texture coordinates moved from its own sprite onto {@code sprite}. Cached. */
    static BakedQuad remap(BakedQuad quad, TextureAtlasSprite sprite) {
        if (quad.getSprite() == sprite) {
            return quad;
        }
        return REMAPPED.computeIfAbsent(sprite, s -> new ConcurrentHashMap<>())
                .computeIfAbsent(quad, q -> {
                    int[] data = q.getVertices().clone();
                    TextureAtlasSprite from = q.getSprite();
                    for (int v = 0; v < 4; v++) {
                        int o = v * STRIDE + UV;
                        float u = Float.intBitsToFloat(data[o]);
                        float w = Float.intBitsToFloat(data[o + 1]);
                        data[o] = Float.floatToRawIntBits(sprite.getU(from.getUOffset(u)));
                        data[o + 1] = Float.floatToRawIntBits(sprite.getV(from.getVOffset(w)));
                    }
                    return new BakedQuad(data, q.getTintIndex(), q.getDirection(), sprite, q.isShade(), q.hasAmbientOcclusion());
                });
    }

    /** A full block face with {@code sprite}, culled like a normal face. Cached per face and sprite. */
    static BakedQuad fullFace(Direction face, TextureAtlasSprite sprite, int tintIndex) {
        BakedQuad[] faces = FULL.computeIfAbsent(sprite, s -> new BakedQuad[12]);
        int slot = face.get3DDataValue() + (tintIndex >= 0 ? 6 : 0);
        BakedQuad quad = faces[slot];
        if (quad == null) {
            BlockElementFace element = new BlockElementFace(face, tintIndex >= 0 ? tintIndex : -1, "#all",
                    new BlockFaceUV(new float[] {0.0F, 0.0F, 16.0F, 16.0F}, 0));
            quad = BAKERY.bakeQuad(new Vector3f(0.0F, 0.0F, 0.0F), new Vector3f(16.0F, 16.0F, 16.0F), element, sprite, face,
                    BlockModelRotation.X0_Y0, null, true);
            faces[slot] = quad;
        }
        return quad;
    }

    /** {@code quad} with {@code rgb} multiplied into its vertex colours and no tint index. */
    static BakedQuad withColor(BakedQuad quad, int rgb) {
        int[] data = quad.getVertices().clone();
        int r = rgb >> 16 & 0xFF;
        int g = rgb >> 8 & 0xFF;
        int b = rgb & 0xFF;
        for (int v = 0; v < 4; v++) {
            int o = v * STRIDE + COLOR;
            int abgr = data[o];
            int nr = (abgr & 0xFF) * r / 255;
            int ng = (abgr >> 8 & 0xFF) * g / 255;
            int nb = (abgr >> 16 & 0xFF) * b / 255;
            data[o] = (abgr & 0xFF000000) | nb << 16 | ng << 8 | nr;
        }
        return new BakedQuad(data, -1, quad.getDirection(), quad.getSprite(), quad.isShade(), quad.hasAmbientOcclusion());
    }

    static float x(BakedQuad q, int v) {
        return Float.intBitsToFloat(q.getVertices()[v * STRIDE + POS]);
    }

    static float y(BakedQuad q, int v) {
        return Float.intBitsToFloat(q.getVertices()[v * STRIDE + POS + 1]);
    }

    static float z(BakedQuad q, int v) {
        return Float.intBitsToFloat(q.getVertices()[v * STRIDE + POS + 2]);
    }

    /** The quad lies on its block face plane (e.g. y == 1 for UP). */
    static boolean isFaceQuad(BakedQuad q) {
        Direction d = q.getDirection();
        float plane = d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0F : 0.0F;
        for (int v = 0; v < 4; v++) {
            float c = switch (d.getAxis()) {
                case X -> x(q, v);
                case Y -> y(q, v);
                case Z -> z(q, v);
            };
            if (Math.abs(c - plane) > 1.0E-4F) {
                return false;
            }
        }
        return true;
    }

    /** The quad covers its whole block face. */
    static boolean isFullQuad(BakedQuad q) {
        if (!isFaceQuad(q)) {
            return false;
        }
        float minA = 1.0F;
        float maxA = 0.0F;
        float minB = 1.0F;
        float maxB = 0.0F;
        Direction.Axis axis = q.getDirection().getAxis();
        for (int v = 0; v < 4; v++) {
            float a = axis == Direction.Axis.X ? y(q, v) : x(q, v);
            float b = axis == Direction.Axis.Z ? y(q, v) : z(q, v);
            minA = Math.min(minA, a);
            maxA = Math.max(maxA, a);
            minB = Math.min(minB, b);
            maxB = Math.max(maxB, b);
        }
        return minA < 1.0E-4F && maxA > 1.0F - 1.0E-4F && minB < 1.0E-4F && maxB > 1.0F - 1.0E-4F;
    }

    static float midX(BakedQuad q) {
        return (x(q, 0) + x(q, 1) + x(q, 2) + x(q, 3)) / 4.0F;
    }

    static float midZ(BakedQuad q) {
        return (z(q, 0) + z(q, 1) + z(q, 2) + z(q, 3)) / 4.0F;
    }
}
