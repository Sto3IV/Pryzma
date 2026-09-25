package net.pryzma.lightmap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

class PryzmaLightmapTest {
    /**
     * Grey image: rows 0-15 {@code skyGrey[column]}, rows 16-31 {@code blockGrey[column]}, and for
     * a 64 px image night vision sky rows 32-47 {@code nightGrey[column]} with black night block rows.
     */
    private static PrLightmapImage image(int width, int height, int[] skyGrey, int[] blockGrey, int[] nightGrey) {
        int[] argb = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int v;
                if (y < 16) {
                    v = skyGrey[x];
                } else if (y < 32) {
                    v = blockGrey[x];
                } else if (y < 48) {
                    v = nightGrey[x];
                } else {
                    v = 0;
                }
                argb[y * width + x] = 0xFF000000 | v << 16 | v << 8 | v;
            }
        }
        return PrLightmapImage.of(width, height, argb);
    }

    private static int red(int abgr) {
        return abgr & 0xFF;
    }

    @Test
    void columnsAreInterpolatedNotRounded() {
        PrLightmapImage img = image(3, 32, new int[] {0, 100, 200}, new int[3], new int[3]);
        float[][] out = new float[16][3];
        img.accumulate(0, 0.25F, 1.0F, out);
        assertEquals(50 / 255.0F, out[0][0], 1e-5, "a quarter of the way is half way to column 1");
        out = new float[16][3];
        img.accumulate(0, 1.0F, 1.0F, out);
        assertEquals(200 / 255.0F, out[7][0], 1e-5);
    }

    @Test
    void torchFlickerNeverJumpsBetweenFrames() {
        // Vanilla LightTexture.tick() random walk, three frames per tick.
        Random random = new Random(42);
        float walk = 0.0F;
        PryzmaLightmap.onFlickerTick(0.0F);
        PryzmaLightmap.onFlickerTick(0.0F);
        float previous = PryzmaLightmap.torchPosition(0.0F);
        float maxStep = 0.0F;
        for (int tick = 0; tick < 20_000; tick++) {
            walk += (float) ((random.nextDouble() - random.nextDouble()) * random.nextDouble() * random.nextDouble() * 0.1);
            walk *= 0.9F;
            PryzmaLightmap.onFlickerTick(walk);
            for (int frame = 0; frame < 3; frame++) {
                float t = PryzmaLightmap.torchPosition(frame / 3.0F);
                maxStep = Math.max(maxStep, Math.abs(t - previous));
                previous = t;
            }
        }
        assertTrue(maxStep < 0.05F, "largest per-frame move along the torch axis was " + maxStep);

        // What a per-frame reseeded sampler does to the same axis (Polytone's default "random").
        Random reseeded = new Random();
        float last = 0.5F;
        float strobe = 0.0F;
        for (int frame = 0; frame < 60_000; frame++) {
            reseeded.setSeed(Float.floatToIntBits(frame * 0.00001F));
            float t = reseeded.nextFloat();
            strobe = Math.max(strobe, Math.abs(t - last));
            last = t;
        }
        assertTrue(strobe > 0.9F, "the reseeded sampler jumps across the whole image: " + strobe);
    }

    @Test
    void weatherBlendIsConvexAndNeverOvershoots() {
        int[] zero = new int[16];
        PrLightmapImage clear = image(16, 32, filled(0), zero, zero);
        PrLightmapImage rain = image(16, 32, filled(100), zero, zero);
        PrLightmapImage thunder = image(16, 32, filled(200), zero, zero);
        PryzmaLightmap.Set set = PryzmaLightmap.Set.of(clear, rain, thunder);
        for (int ri = 0; ri <= 10; ri++) {
            for (int ti = 0; ti <= 10; ti++) {
                float rainLevel = ri / 10.0F;
                float thunderLevel = ti / 10.0F * rainLevel; // vanilla scales thunder by rain
                int value = red(PryzmaLightmap.compute(set, 0.5F, 0.5F, rainLevel, thunderLevel, 0, 0, 0)[15 * 16]);
                float expected = 255 * ((rainLevel - thunderLevel) * 100 / 255.0F + thunderLevel * 200 / 255.0F);
                assertEquals(expected, value, 1.01, "rain=" + rainLevel + " thunder=" + thunderLevel);
            }
        }
        // Storm starting while rain is still fading in: OptiFine's weights add up to 1.7 here.
        int onset = red(PryzmaLightmap.compute(set, 0.5F, 0.5F, 0.3F, 0.3F, 0, 0, 0)[15 * 16]);
        assertEquals(0.3F * 200, onset, 1.01);
    }

    @Test
    void nightVisionFadesInsteadOfSwitching() {
        int[] zero = new int[16];
        PrLightmapImage img = image(16, 64, filled(40), zero, filled(240));
        PryzmaLightmap.Set set = PryzmaLightmap.Set.of(img, null, null);
        int off = red(PryzmaLightmap.compute(set, 0.5F, 0.5F, 0, 0, 0.0F, 0, 0)[15 * 16]);
        int half = red(PryzmaLightmap.compute(set, 0.5F, 0.5F, 0, 0, 0.5F, 0, 0)[15 * 16]);
        int full = red(PryzmaLightmap.compute(set, 0.5F, 0.5F, 0, 0, 1.0F, 0, 0)[15 * 16]);
        assertEquals(40, off, 1);
        assertEquals(140, half, 1);
        assertEquals(240, full, 1);
    }

    @Test
    void skyAndBlockAddUpLikeOptifine() {
        int[] sky = new int[16];
        int[] block = new int[16];
        for (int i = 0; i < 16; i++) {
            sky[i] = 10 * i;
            block[i] = 5 * i;
        }
        PrLightmapImage img = image(16, 32, sky, block, new int[16]);
        int[] table = PryzmaLightmap.compute(PryzmaLightmap.Set.of(img, null, null), 1.0F, 0.0F, 0, 0, 0, 0, 0);
        // sky axis at column 15 -> 150, block axis at column 0 -> 0, every level row is uniform
        assertEquals(150, red(table[3 * 16 + 9]), 1);
        int[] dark = PryzmaLightmap.compute(PryzmaLightmap.Set.of(img, null, null), 1.0F, 1.0F, 0, 0, 0, 0.2F, 0);
        assertEquals(Math.round(255 * (150 / 255.0F + 75 / 255.0F - 0.2F)), red(dark[0]), 1, "darkness subtracts");
    }

    @Test
    void sunPositionFollowsOptifine() {
        assertEquals(0.0F, PryzmaLightmap.sunPosition(0.2F, false), 1e-6);
        assertEquals(0.9333F, PryzmaLightmap.sunPosition(1.0F, false), 1e-3);
        assertEquals(1.0F, PryzmaLightmap.sunPosition(0.0F, true), 1e-6);
    }

    @Test
    void weatherImagesFallBackLikeOptifine() {
        PrLightmapImage a = image(16, 32, new int[16], new int[16], new int[16]);
        PrLightmapImage b = image(16, 32, new int[16], new int[16], new int[16]);
        PryzmaLightmap.Set onlyThunder = PryzmaLightmap.Set.of(a, null, b);
        assertSame(a, onlyThunder.rain());
        assertSame(b, onlyThunder.thunder());
        PryzmaLightmap.Set onlyRain = PryzmaLightmap.Set.of(a, b, null);
        assertSame(b, onlyRain.thunder());
        PryzmaLightmap.Set none = PryzmaLightmap.Set.of(a, null, null);
        assertNull(none.rain());
        assertNull(none.thunder());
    }

    private static int[] filled(int v) {
        int[] a = new int[16];
        java.util.Arrays.fill(a, v);
        return a;
    }
}
