package net.pryzma.lod;

/** Packed RGB arithmetic of the Farlands LOD colours. */
public final class LodColors {
    private LodColors() {
    }

    /** Channel-wise product of two {@code 0xRRGGBB} colours, rounded; white is the identity. */
    public static int multiply(int rgb, int tint) {
        int r = mul(rgb >> 16 & 0xFF, tint >> 16 & 0xFF);
        int g = mul(rgb >> 8 & 0xFF, tint >> 8 & 0xFF);
        int b = mul(rgb & 0xFF, tint & 0xFF);
        return r << 16 | g << 8 | b;
    }

    /** {@code rgb} with every channel scaled by {@code factor} (0..1), rounded. */
    public static int shade(int rgb, float factor) {
        int r = scale(rgb >> 16 & 0xFF, factor);
        int g = scale(rgb >> 8 & 0xFF, factor);
        int b = scale(rgb & 0xFF, factor);
        return r << 16 | g << 8 | b;
    }

    /** RGB of a {@code NativeImage} pixel, which packs channels as {@code 0xAABBGGRR}. */
    public static int abgrToRgb(int abgr) {
        return (abgr & 0xFF) << 16 | abgr & 0xFF00 | abgr >> 16 & 0xFF;
    }

    private static int mul(int a, int b) {
        return (a * b + 127) / 255;
    }

    private static int scale(int channel, float factor) {
        return Math.min(255, Math.max(0, (int) (channel * factor + 0.5F)));
    }
}
