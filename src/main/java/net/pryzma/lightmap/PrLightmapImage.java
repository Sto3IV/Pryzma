package net.pryzma.lightmap;

import net.pryzma.core.res.PrImage;

/**
 * An OptiFine lightmap image, pre-split into float RGB.
 *
 * <p>Layout: rows 0-15 are the sky light levels, rows 16-31 the block light levels; a 64 px tall
 * image repeats both blocks in rows 32-63 for night vision. The horizontal axis is sampled
 * continuously: sun brightness for the sky rows, torch flicker for the block rows. Any width of
 * at least 2 px is valid.
 */
public final class PrLightmapImage {
    private final int width;
    private final int height;
    private final float[] r;
    private final float[] g;
    private final float[] b;

    private PrLightmapImage(int width, int height, float[] r, float[] g, float[] b) {
        this.width = width;
        this.height = height;
        this.r = r;
        this.g = g;
        this.b = b;
    }

    /** {@code null} when the image cannot be a lightmap (too narrow or fewer than 32 rows). */
    public static PrLightmapImage of(PrImage image) {
        if (image.width() < 2 || image.height() < 32) {
            return null;
        }
        return of(image.width(), image.height(), image.argb());
    }

    static PrLightmapImage of(int width, int height, int[] argb) {
        int n = width * height;
        float[] r = new float[n];
        float[] g = new float[n];
        float[] b = new float[n];
        for (int i = 0; i < n; i++) {
            int c = argb[i];
            r[i] = (c >> 16 & 0xFF) / 255.0F;
            g[i] = (c >> 8 & 0xFF) / 255.0F;
            b[i] = (c & 0xFF) / 255.0F;
        }
        return new PrLightmapImage(width, height, r, g, b);
    }

    public int width() {
        return width;
    }

    public boolean hasNightVision() {
        return height >= 64;
    }

    /**
     * Samples the 16 rows starting at {@code row0} at horizontal position {@code t} in [0, 1],
     * interpolating linearly between neighbouring columns, and adds {@code weight} times the result
     * into {@code out} ({@code out[level][channel]}).
     */
    public void accumulate(int row0, float t, float weight, float[][] out) {
        float x = Math.clamp(t, 0.0F, 1.0F) * (width - 1);
        int lo = (int) x;
        int hi = Math.min(lo + 1, width - 1);
        float fHi = x - lo;
        float fLo = 1.0F - fHi;
        for (int level = 0; level < 16; level++) {
            int row = (row0 + level) * width;
            float[] o = out[level];
            o[0] += weight * (r[row + lo] * fLo + r[row + hi] * fHi);
            o[1] += weight * (g[row + lo] * fLo + g[row + hi] * fHi);
            o[2] += weight * (b[row + lo] * fLo + b[row + hi] * fHi);
        }
    }
}
