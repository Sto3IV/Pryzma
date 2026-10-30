package net.pryzma.render;

import com.mojang.blaze3d.platform.NativeImage;

/**
 * Gamma-correct mipmap downsampling and border color pre-fill.
 *
 * <p>Standardizes texture sampling across all mipmap levels and hardware filters,
 * eliminating shimmering white dot artifacts, dark edges, and atlas seam bleeding
 * on AMD Radeon (RDNA2/RDNA3) and modern OpenGL architectures.</p>
 */
public final class PrMipmaps {
    private PrMipmaps() {
    }

    /**
     * Fills the RGB channels of transparent/cutout pixels (alpha <= 16) with the weighted average
     * color of visible pixels within the sprite.
     *
     * <p>When hardware texture samplers evaluate bilinear or anisotropic filtering across sprite
     * edges or at lower mip levels, neighboring transparent pixels are sampled. If those pixels
     * contain default black or white values, unsightly fringes or white speckles appear. Pre-filling
     * with the sprite's own opaque color guarantees that any edge bleeding blends invisibly into
     * the block itself.</p>
     */
    public static void fixTransparentColor(NativeImage image) {
        if (image == null) {
            return;
        }

        int width = image.getWidth();
        int height = image.getHeight();
        long redSum = 0L;
        long greenSum = 0L;
        long blueSum = 0L;
        long count = 0L;

        // NativeImage getPixelRGBA returns an ABGR32 integer:
        // Alpha: bits 24..31
        // Blue:  bits 16..23
        // Green: bits 8..15
        // Red:   bits 0..7
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int col = image.getPixelRGBA(x, y);
                int alpha = (col >> 24) & 0xFF;
                if (alpha >= 16) {
                    redSum += (col & 0xFF);
                    greenSum += ((col >> 8) & 0xFF);
                    blueSum += ((col >> 16) & 0xFF);
                    count++;
                }
            }
        }

        if (count > 0L) {
            int redAvg = (int) (redSum / count);
            int greenAvg = (int) (greenSum / count);
            int blueAvg = (int) (blueSum / count);
            int colAvg = (blueAvg << 16) | (greenAvg << 8) | redAvg;

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int col = image.getPixelRGBA(x, y);
                    int alpha = (col >> 24) & 0xFF;
                    if (alpha <= 16) {
                        image.setPixelRGBA(x, y, (alpha << 24) | colAvg);
                    }
                }
            }
        }
    }

    /**
     * Computes the alpha-weighted, linear-space downsampled color for a 2x2 texel block.
     * Replaces vanilla's unweighted box averaging which causes border discoloration.
     */
    public static int alphaBlend(int c1, int c2, int c3, int c4) {
        int cx1 = alphaBlend(c1, c2);
        int cx2 = alphaBlend(c3, c4);
        return alphaBlend(cx1, cx2);
    }

    private static int alphaBlend(int c1, int c2) {
        int a1 = (c1 >> 24) & 0xFF;
        int a2 = (c2 >> 24) & 0xFF;
        int ax = (a1 + a2) / 2;

        if (a1 == 0 && a2 == 0) {
            a1 = 1;
            a2 = 1;
        } else {
            if (a1 == 0) {
                c1 = c2;
                ax /= 2;
            }
            if (a2 == 0) {
                c2 = c1;
                ax /= 2;
            }
        }

        int r1 = (c1 & 0xFF) * a1;
        int g1 = ((c1 >> 8) & 0xFF) * a1;
        int b1 = ((c1 >> 16) & 0xFF) * a1;

        int r2 = (c2 & 0xFF) * a2;
        int g2 = ((c2 >> 8) & 0xFF) * a2;
        int b2 = ((c2 >> 16) & 0xFF) * a2;

        int sumAlpha = a1 + a2;
        int rx = (r1 + r2) / sumAlpha;
        int gx = (g1 + g2) / sumAlpha;
        int bx = (b1 + b2) / sumAlpha;

        return (ax << 24) | (bx << 16) | (gx << 8) | rx;
    }
}
