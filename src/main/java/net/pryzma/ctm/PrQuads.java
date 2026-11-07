package net.pryzma.ctm;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.neoforged.neoforge.client.model.quad.MutableQuad;

/** Quad copies with another sprite, full-face quads for overlays, and quad shape tests. */
public final class PrQuads {
    private static final Map<TextureAtlasSprite, Map<BakedQuad, BakedQuad>> REMAPPED = new ConcurrentHashMap<>();
    private static final Map<TextureAtlasSprite, BakedQuad[]> FULL = new ConcurrentHashMap<>();

    private PrQuads() {
    }

    public static void clearCaches() {
        REMAPPED.clear();
        FULL.clear();
    }

    /** {@code quad} with its texture coordinates moved from its own sprite onto {@code sprite}. Cached. */
    public static BakedQuad remap(BakedQuad quad, TextureAtlasSprite sprite) {
        if (quad.materialInfo().sprite() == sprite) {
            return quad;
        }
        return REMAPPED.computeIfAbsent(sprite, s -> new ConcurrentHashMap<>())
                .computeIfAbsent(quad, q -> {
                    MutableQuad mq = new MutableQuad();
                    mq.setFrom(q);
                    BakedQuad.MaterialInfo mi = q.materialInfo();
                    mq.setSpriteAndMoveUv(sprite, mi.layer(), mi.itemRenderType(), mi.itemGlintRenderType(), mi.itemGlintSpecialRenderType());
                    return mq.toBakedQuad();
                });
    }

    public static BakedQuad retexture(BakedQuad quad, TextureAtlasSprite sprite, int tintIndex) {
        if (quad == null) {
            return null;
        }
        MutableQuad mq = new MutableQuad();
        mq.setFrom(quad);
        BakedQuad.MaterialInfo mi = quad.materialInfo();
        if (sprite != null) {
            mq.setSpriteAndMoveUv(sprite, mi.layer(), mi.itemRenderType(), mi.itemGlintRenderType(), mi.itemGlintSpecialRenderType());
        }
        mq.setTintIndex(tintIndex);
        return mq.toBakedQuad();
    }

    /** A full block face with {@code sprite}, culled like a normal face. Cached per face and sprite. */
    public static BakedQuad fullFace(Direction face, TextureAtlasSprite sprite, int tintIndex) {
        return fullFace(face, sprite, tintIndex, ChunkSectionLayer.SOLID);
    }

    public static BakedQuad fullFace(Direction face, TextureAtlasSprite sprite, int tintIndex, ChunkSectionLayer layer) {
        BakedQuad[] faces = FULL.computeIfAbsent(sprite, s -> new BakedQuad[36]);
        int slot = layer.ordinal() * 12 + face.get3DDataValue() + (tintIndex >= 0 ? 6 : 0);
        BakedQuad quad = faces[slot];
        if (quad == null) {
            MutableQuad mq = new MutableQuad();
            mq.setCubeFace(face, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
            mq.setSprite(sprite, layer, null, null, null);
            mq.setTintIndex(tintIndex >= 0 ? tintIndex : -1);
            mq.bakeUvsFromPosition();
            quad = mq.toBakedQuad();
            faces[slot] = quad;
        }
        return quad;
    }

    /** A block face with {@code sprite} up to {@code yMax} height (e.g. 15.0F for dirt path). */
    public static BakedQuad faceQuad(Direction face, TextureAtlasSprite sprite, int tintIndex, float yMax) {
        if (yMax >= 15.99F) {
            return fullFace(face, sprite, tintIndex);
        }
        MutableQuad mq = new MutableQuad();
        mq.setCubeFace(face, 0.0F, 0.0F, 0.0F, 1.0F, yMax / 16.0F, 1.0F);
        mq.setSprite(sprite, ChunkSectionLayer.SOLID, null, null, null);
        mq.setTintIndex(tintIndex >= 0 ? tintIndex : -1);
        mq.bakeUvsFromPosition();
        return mq.toBakedQuad();
    }

    /** {@code quad} with {@code rgb} multiplied into its vertex colours and no tint index. */
    public static BakedQuad withColor(BakedQuad quad, int rgb) {
        MutableQuad mq = new MutableQuad();
        mq.setFrom(quad);
        mq.setTintIndex(-1);
        int r = rgb >> 16 & 0xFF;
        int g = rgb >> 8 & 0xFF;
        int b = rgb & 0xFF;
        for (int v = 0; v < 4; v++) {
            int current = quad.bakedColors().color(v);
            int curA = ARGB.alpha(current);
            int curR = ARGB.red(current) * r / 255;
            int curG = ARGB.green(current) * g / 255;
            int curB = ARGB.blue(current) * b / 255;
            mq.setColor(v, curR, curG, curB, curA);
        }
        return mq.toBakedQuad();
    }

    public static float x(BakedQuad q, int v) {
        return q.position(v).x();
    }

    public static float y(BakedQuad q, int v) {
        return q.position(v).y();
    }

    public static float z(BakedQuad q, int v) {
        return q.position(v).z();
    }

    public static float u(BakedQuad q, int v) {
        return UVPair.unpackU(q.packedUV(v));
    }

    public static float v(BakedQuad q, int v) {
        return UVPair.unpackV(q.packedUV(v));
    }

    /** The quad lies on its block face plane (e.g. y == 1 for UP). */
    public static boolean isFaceQuad(BakedQuad q) {
        Direction d = q.direction();
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
    public static boolean isFullQuad(BakedQuad q) {
        if (!isFaceQuad(q)) {
            return false;
        }
        float minA = 1.0F;
        float maxA = 0.0F;
        float minB = 1.0F;
        float maxB = 0.0F;
        Direction.Axis axis = q.direction().getAxis();
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

    public static float midX(BakedQuad q) {
        return (x(q, 0) + x(q, 1) + x(q, 2) + x(q, 3)) / 4.0F;
    }

    public static float midY(BakedQuad q) {
        return (y(q, 0) + y(q, 1) + y(q, 2) + y(q, 3)) / 4.0F;
    }

    public static float midZ(BakedQuad q) {
        return (z(q, 0) + z(q, 1) + z(q, 2) + z(q, 3)) / 4.0F;
    }
}
