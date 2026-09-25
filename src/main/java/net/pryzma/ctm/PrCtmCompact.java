package net.pryzma.ctm;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.neoforged.neoforge.client.model.IQuadTransformer;

/**
 * OptiFine {@code ctm_compact}: the 47 connection states are built from 5 tiles by splitting the
 * face into halves or quarters, each quarter cut out of the tile that matches its corner.
 */
final class PrCtmCompact {
    private enum Part {
        UP(0, 0, 16, 8), UP_RIGHT(8, 0, 16, 8), RIGHT(8, 0, 16, 16), DOWN_RIGHT(8, 8, 16, 16),
        DOWN(0, 8, 16, 16), DOWN_LEFT(0, 8, 8, 16), LEFT(0, 0, 8, 16), UP_LEFT(0, 0, 8, 8);

        final int x1;
        final int y1;
        final int x2;
        final int y2;

        Part(int x1, int y1, int x2, int y2) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }
    }

    private static final int STRIDE = IQuadTransformer.STRIDE;
    private static final int POS = IQuadTransformer.POSITION;
    private static final int UV = IQuadTransformer.UV0;
    private static final Map<TextureAtlasSprite, Map<BakedQuad, BakedQuad[]>> CACHE = new ConcurrentHashMap<>();

    private PrCtmCompact() {
    }

    static void clearCache() {
        CACHE.clear();
    }

    /** OptiFine ConnectedTexturesCompact.getConnectedTextureCtmCompact. */
    static BakedQuad[] quads(int ctm, PrCtmRule r, int side, BakedQuad quad) {
        if (r.ctmTileIndexes != null && ctm >= 0 && ctm < r.ctmTileIndexes.length) {
            int tile = r.ctmTileIndexes[ctm];
            if (tile >= 0 && tile < r.tileSprites.length) {
                return PryzmaCtm.tileAt(r, tile, quad);
            }
        }
        return switch (ctm) {
            case 1 -> h(0, 3, r, side, quad);
            case 2 -> PryzmaCtm.tileAt(r, 3, quad);
            case 3 -> h(3, 0, r, side, quad);
            case 4 -> four(0, 3, 2, 4, r, side, quad);
            case 5 -> four(3, 0, 4, 2, r, side, quad);
            case 6 -> four(2, 4, 2, 4, r, side, quad);
            case 7 -> four(3, 3, 4, 4, r, side, quad);
            case 8 -> four(4, 1, 4, 4, r, side, quad);
            case 9 -> four(4, 4, 4, 1, r, side, quad);
            case 10 -> four(1, 4, 1, 4, r, side, quad);
            case 11 -> four(1, 1, 4, 4, r, side, quad);
            case 12 -> v(0, 2, r, side, quad);
            case 13 -> four(0, 3, 2, 1, r, side, quad);
            case 14 -> v(3, 1, r, side, quad);
            case 15 -> four(3, 0, 1, 2, r, side, quad);
            case 16 -> four(2, 4, 0, 3, r, side, quad);
            case 17 -> four(4, 2, 3, 0, r, side, quad);
            case 18 -> four(4, 4, 3, 3, r, side, quad);
            case 19 -> four(4, 2, 4, 2, r, side, quad);
            case 20 -> four(1, 4, 4, 4, r, side, quad);
            case 21 -> four(4, 4, 1, 4, r, side, quad);
            case 22 -> four(4, 4, 1, 1, r, side, quad);
            case 23 -> four(4, 1, 4, 1, r, side, quad);
            case 24 -> PryzmaCtm.tileAt(r, 2, quad);
            case 25 -> h(2, 1, r, side, quad);
            case 26 -> PryzmaCtm.tileAt(r, 1, quad);
            case 27 -> h(1, 2, r, side, quad);
            case 28 -> four(2, 4, 2, 1, r, side, quad);
            case 29 -> four(3, 3, 1, 4, r, side, quad);
            case 30 -> four(2, 1, 2, 4, r, side, quad);
            case 31 -> four(3, 3, 4, 1, r, side, quad);
            case 32 -> four(1, 1, 1, 4, r, side, quad);
            case 33 -> four(1, 1, 4, 1, r, side, quad);
            case 34 -> four(4, 1, 1, 4, r, side, quad);
            case 35 -> four(1, 4, 4, 1, r, side, quad);
            case 36 -> v(2, 0, r, side, quad);
            case 37 -> four(2, 1, 0, 3, r, side, quad);
            case 38 -> v(1, 3, r, side, quad);
            case 39 -> four(1, 2, 3, 0, r, side, quad);
            case 40 -> four(4, 1, 3, 3, r, side, quad);
            case 41 -> four(1, 2, 4, 2, r, side, quad);
            case 42 -> four(1, 4, 3, 3, r, side, quad);
            case 43 -> four(4, 2, 1, 2, r, side, quad);
            case 44 -> four(1, 4, 1, 1, r, side, quad);
            case 45 -> four(4, 1, 1, 1, r, side, quad);
            case 46 -> PryzmaCtm.tileAt(r, 4, quad);
            default -> PryzmaCtm.tileAt(r, 0, quad);
        };
    }

    private static BakedQuad[] h(int left, int right, PrCtmRule r, int side, BakedQuad quad) {
        return parts(r, side, quad, Part.LEFT, left, Part.RIGHT, right);
    }

    private static BakedQuad[] v(int up, int down, PrCtmRule r, int side, BakedQuad quad) {
        return parts(r, side, quad, Part.UP, up, Part.DOWN, down);
    }

    /** OptiFine getQuadsCompact4: merges equal neighbouring quarters into halves. */
    private static BakedQuad[] four(int upLeft, int upRight, int downLeft, int downRight, PrCtmRule r, int side, BakedQuad quad) {
        if (upLeft == upRight) {
            return downLeft == downRight
                    ? parts(r, side, quad, Part.UP, upLeft, Part.DOWN, downLeft)
                    : parts(r, side, quad, Part.UP, upLeft, Part.DOWN_LEFT, downLeft, Part.DOWN_RIGHT, downRight);
        }
        if (downLeft == downRight) {
            return parts(r, side, quad, Part.UP_LEFT, upLeft, Part.UP_RIGHT, upRight, Part.DOWN, downLeft);
        }
        if (upLeft == downLeft) {
            return upRight == downRight
                    ? parts(r, side, quad, Part.LEFT, upLeft, Part.RIGHT, upRight)
                    : parts(r, side, quad, Part.LEFT, upLeft, Part.UP_RIGHT, upRight, Part.DOWN_RIGHT, downRight);
        }
        return upRight == downRight
                ? parts(r, side, quad, Part.UP_LEFT, upLeft, Part.DOWN_LEFT, downLeft, Part.RIGHT, upRight)
                : parts(r, side, quad, Part.UP_LEFT, upLeft, Part.UP_RIGHT, upRight, Part.DOWN_LEFT, downLeft,
                        Part.DOWN_RIGHT, downRight);
    }

    /** Arguments come in (part, tile) pairs. */
    private static BakedQuad[] parts(PrCtmRule r, int side, BakedQuad quad, Object... pairs) {
        BakedQuad[] out = new BakedQuad[pairs.length / 2];
        for (int i = 0; i < out.length; i++) {
            Part part = (Part) pairs[i * 2];
            int tile = (Integer) pairs[i * 2 + 1];
            TextureAtlasSprite sprite = r.tileKeep[tile] ? quad.getSprite() : r.tileSprites[tile];
            if (sprite == null) {
                return null;
            }
            out[i] = cut(quad, sprite, side, part);
        }
        return out;
    }

    private static BakedQuad cut(BakedQuad quad, TextureAtlasSprite sprite, int side, Part part) {
        Map<BakedQuad, BakedQuad[]> perQuad = CACHE.computeIfAbsent(sprite, s -> new ConcurrentHashMap<>());
        BakedQuad[] byPart = perQuad.computeIfAbsent(quad, q -> new BakedQuad[Part.values().length * 6]);
        int slot = part.ordinal() * 6 + side;
        BakedQuad cut = byPart[slot];
        if (cut == null) {
            cut = make(quad, sprite, side, part);
            byPart[slot] = cut;
        }
        return cut;
    }

    /** OptiFine makeSpriteQuadCompact: moves each vertex to the part's edge and remaps its UV. */
    private static BakedQuad make(BakedQuad quad, TextureAtlasSprite to, int side, Part part) {
        int[] data = quad.getVertices().clone();
        TextureAtlasSprite from = quad.getSprite();
        float atlasW = from.contents().width() / (from.getU1() - from.getU0());
        float atlasH = from.contents().height() / (from.getV1() - from.getV0());
        float k = 4.0F / Math.max(atlasH, atlasW);
        float scale = 16.0F * (1.0F - k);
        for (int v = 0; v < 4; v++) {
            int o = v * STRIDE;
            float u = Float.intBitsToFloat(data[o + UV]);
            float w = Float.intBitsToFloat(data[o + UV + 1]);
            double su = from.getUOffset(u) * 16.0;
            double sv = from.getVOffset(w) * 16.0;
            float x = Float.intBitsToFloat(data[o + POS]);
            float y = Float.intBitsToFloat(data[o + POS + 1]);
            float z = Float.intBitsToFloat(data[o + POS + 2]);
            float cu;
            float cv;
            switch (side) {
                case 0 -> {
                    cu = x;
                    cv = 1.0F - z;
                }
                case 1 -> {
                    cu = x;
                    cv = z;
                }
                case 2 -> {
                    cu = 1.0F - x;
                    cv = 1.0F - y;
                }
                case 3 -> {
                    cu = x;
                    cv = 1.0F - y;
                }
                case 4 -> {
                    cu = z;
                    cv = 1.0F - y;
                }
                default -> {
                    cu = 1.0F - z;
                    cv = 1.0F - y;
                }
            }
            if (su < part.x1) {
                cu += (float) ((part.x1 - su) / scale);
                su = part.x1;
            }
            if (su > part.x2) {
                cu -= (float) ((su - part.x2) / scale);
                su = part.x2;
            }
            if (sv < part.y1) {
                cv += (float) ((part.y1 - sv) / scale);
                sv = part.y1;
            }
            if (sv > part.y2) {
                cv -= (float) ((sv - part.y2) / scale);
                sv = part.y2;
            }
            switch (side) {
                case 0 -> {
                    x = cu;
                    z = 1.0F - cv;
                }
                case 1 -> {
                    x = cu;
                    z = cv;
                }
                case 2 -> {
                    x = 1.0F - cu;
                    y = 1.0F - cv;
                }
                case 3 -> {
                    x = cu;
                    y = 1.0F - cv;
                }
                case 4 -> {
                    z = cu;
                    y = 1.0F - cv;
                }
                default -> {
                    z = 1.0F - cu;
                    y = 1.0F - cv;
                }
            }
            data[o + UV] = Float.floatToRawIntBits(to.getU((float) (su / 16.0)));
            data[o + UV + 1] = Float.floatToRawIntBits(to.getV((float) (sv / 16.0)));
            data[o + POS] = Float.floatToRawIntBits(x);
            data[o + POS + 1] = Float.floatToRawIntBits(y);
            data[o + POS + 2] = Float.floatToRawIntBits(z);
        }
        return new BakedQuad(data, quad.getTintIndex(), quad.getDirection(), to, quad.isShade(), quad.hasAmbientOcclusion());
    }
}
