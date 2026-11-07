package net.pryzma.render;

import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.core.Direction;
import net.pryzma.Pryzma;

/**
 * Rotation and reflection rule for natural textures, with an allocation-free per-variant quad cache.
 */
public final class PrNaturalProperties {
    public final int rotation;
    public final boolean flip;

    @SuppressWarnings("unchecked")
    private final ConcurrentHashMap<BakedQuad, BakedQuad>[] quadMaps = new ConcurrentHashMap[8];

    public PrNaturalProperties(int rotation, boolean flip) {
        this.rotation = rotation;
        this.flip = flip;
        for (int i = 0; i < 8; i++) {
            this.quadMaps[i] = new ConcurrentHashMap<>();
        }
    }

    public static PrNaturalProperties parse(String type) {
        if (type == null) {
            return null;
        }
        String trimmed = type.trim();
        int rot = 1;
        boolean flp = false;
        if ("4".equals(trimmed)) {
            rot = 4;
        } else if ("2".equals(trimmed)) {
            rot = 2;
        } else if ("F".equalsIgnoreCase(trimmed)) {
            flp = true;
        } else if ("4F".equalsIgnoreCase(trimmed)) {
            rot = 4;
            flp = true;
        } else if ("2F".equalsIgnoreCase(trimmed)) {
            rot = 2;
            flp = true;
        } else {
            Pryzma.LOGGER.warn("NaturalTextures: Unknown type: {}", type);
            return null;
        }
        return new PrNaturalProperties(rot, flp);
    }

    public boolean isValid() {
        return this.rotation == 2 || this.rotation == 4 || this.flip;
    }

    public BakedQuad getQuad(BakedQuad quadIn, int rotate, boolean flipU) {
        int index = rotate;
        if (flipU) {
            index |= 4;
        }
        if (index <= 0 || index >= this.quadMaps.length) {
            return quadIn;
        }
        return this.quadMaps[index].computeIfAbsent(quadIn, q -> this.makeQuad(q, rotate, flipU));
    }

    public void clearCache() {
        for (ConcurrentHashMap<BakedQuad, BakedQuad> map : this.quadMaps) {
            map.clear();
        }
    }

    private BakedQuad makeQuad(BakedQuad quad, int rotate, boolean flipU) {
        if (!this.isFullSprite(quad)) {
            rotate = 0;
        }
        if (rotate == 0 && !flipU) {
            return quad;
        }
        int v2 = 4 - rotate;
        if (flipU) {
            v2 += 3;
        }
        v2 %= 4;

        long[] newUV = new long[4];
        for (int v = 0; v < 4; v++) {
            newUV[v2] = quad.packedUV(v);
            if (flipU) {
                if (--v2 < 0) {
                    v2 = 3;
                }
            } else if (++v2 > 3) {
                v2 = 0;
            }
        }

        return new BakedQuad(
            quad.position0(), quad.position1(), quad.position2(), quad.position3(),
            newUV[0], newUV[1], newUV[2], newUV[3],
            quad.direction(), quad.materialInfo(),
            quad.bakedNormals(), quad.bakedColors()
        );
    }

    private boolean isFullSprite(BakedQuad quad) {
        TextureAtlasSprite sprite = quad.materialInfo().sprite();
        if (sprite == null) {
            return false;
        }
        float uMin = sprite.getU0();
        float uMax = sprite.getU1();
        float uDelta = (uMax - uMin) / 256.0F;
        float vMin = sprite.getV0();
        float vMax = sprite.getV1();
        float vDelta = (vMax - vMin) / 256.0F;

        for (int i = 0; i < 4; i++) {
            long packed = quad.packedUV(i);
            float u = UVPair.unpackU(packed);
            float v = UVPair.unpackV(packed);
            if (!this.equalsDelta(u, uMin, uDelta) && !this.equalsDelta(u, uMax, uDelta)) {
                return false;
            }
            if (!this.equalsDelta(v, vMin, vDelta) && !this.equalsDelta(v, vMax, vDelta)) {
                return false;
            }
        }
        return true;
    }

    private boolean equalsDelta(float x1, float x2, float deltaMax) {
        return Math.abs(x1 - x2) < deltaMax;
    }
}
