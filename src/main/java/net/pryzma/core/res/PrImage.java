package net.pryzma.core.res;

import java.io.IOException;
import java.io.InputStream;

import com.mojang.blaze3d.platform.NativeImage;

/** A decoded image as packed {@code 0xAARRGGBB} pixels, row major. */
public record PrImage(int width, int height, int[] argb) {
    public int get(int x, int y) {
        return argb[y * width + x];
    }

    /** Pixel with coordinates clamped to the image, as OptiFine's colormap lookups do. */
    public int getClamped(int x, int y) {
        x = x < 0 ? 0 : Math.min(x, width - 1);
        y = y < 0 ? 0 : Math.min(y, height - 1);
        return argb[y * width + x];
    }

    public static PrImage read(InputStream in) throws IOException {
        try (NativeImage image = NativeImage.read(in)) {
            int[] abgr = image.getPixelsRGBA();
            for (int i = 0; i < abgr.length; i++) {
                int c = abgr[i];
                abgr[i] = (c & 0xFF00FF00) | ((c >>> 16) & 0xFF) | ((c & 0xFF) << 16);
            }
            return new PrImage(image.getWidth(), image.getHeight(), abgr);
        }
    }
}
