package net.pryzma.lightmap;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.pryzma.Pryzma;
import net.pryzma.PryzmaConfig;
import net.pryzma.core.res.PrImage;
import net.pryzma.core.res.PrPaths;
import net.pryzma.core.res.PrResources;

/**
 * OptiFine custom lightmaps ({@code optifine/lightmap/world<N>.png}, plus {@code _rain} and
 * {@code _thunder} variants), evaluated without flicker.
 *
 * <p>Why other implementations strobe, and what this one does instead:
 * <ul>
 *   <li>The block-light axis is driven by vanilla's torch flicker, a slow random walk, interpolated
 *       between ticks; it is never re-randomised per frame.</li>
 *   <li>Both axes interpolate linearly between image columns, so nothing steps.</li>
 *   <li>Clear, rain and thunder images are blended by weather strength with non-negative weights
 *       that always sum to one; nothing switches when rain starts.</li>
 *   <li>Night vision rows are blended by the effect's own fade value, including its end blink.</li>
 *   <li>The table is rebuilt every frame from interpolated inputs and uploaded once, at the point
 *       where vanilla uploads its own lightmap, before any geometry is drawn.</li>
 * </ul>
 *
 * Dimension ids follow OptiFine: the Nether is -1, the End 1, every other dimension 0.
 */
public final class PryzmaLightmap {
    private static final String PREFIX = "optifine/lightmap/world";

    private static volatile Data data = Data.EMPTY;
    private static float prevFlicker;
    private static float flicker;

    // Scratch buffers, render thread only.
    private static final float[][] SKY = new float[16][3];
    private static final float[][] TORCH = new float[16][3];

    private PryzmaLightmap() {
    }

    /** Clear/rain/thunder images of one dimension; rain and thunder fall back as in OptiFine. */
    public record Set(PrLightmapImage clear, PrLightmapImage rain, PrLightmapImage thunder) {
        static Set of(PrLightmapImage clear, PrLightmapImage rain, PrLightmapImage thunder) {
            if (rain != null || thunder != null) {
                if (rain == null) {
                    rain = clear;
                }
                if (thunder == null) {
                    thunder = rain;
                }
            }
            return new Set(clear, rain, thunder);
        }
    }

    public record Data(Map<Integer, Set> byDimension) {
        static final Data EMPTY = new Data(Map.of());
    }

    // ------------------------------------------------------------------ loading

    public static Data prepare(PrResources res) {
        Map<Integer, PrLightmapImage> clear = new HashMap<>();
        Map<Integer, PrLightmapImage> rain = new HashMap<>();
        Map<Integer, PrLightmapImage> thunder = new HashMap<>();
        for (Map.Entry<ResourceLocation, Resource> e : res.list("lightmap", ".png").entrySet()) {
            ResourceLocation loc = e.getKey();
            if (!"minecraft".equals(loc.getNamespace()) || !loc.getPath().startsWith(PREFIX)) {
                continue;
            }
            String stem = PrPaths.stripExtension(loc.getPath().substring(PREFIX.length()), ".png");
            Map<Integer, PrLightmapImage> target = clear;
            if (stem.endsWith("_rain")) {
                target = rain;
                stem = stem.substring(0, stem.length() - "_rain".length());
            } else if (stem.endsWith("_thunder")) {
                target = thunder;
                stem = stem.substring(0, stem.length() - "_thunder".length());
            }
            int dim;
            try {
                dim = Integer.parseInt(stem);
            } catch (NumberFormatException ex) {
                continue;
            }
            PrImage image = res.image(loc).orElse(null);
            PrLightmapImage lightmap = image == null ? null : PrLightmapImage.of(image);
            if (lightmap == null) {
                Pryzma.LOGGER.warn("CustomColors: invalid lightmap {} ({})", loc,
                        image == null ? "unreadable" : image.width() + "x" + image.height());
                continue;
            }
            target.put(dim, lightmap);
        }
        Map<Integer, Set> sets = new HashMap<>();
        clear.forEach((dim, img) -> sets.put(dim, Set.of(img, rain.get(dim), thunder.get(dim))));
        if (!sets.isEmpty()) {
            Pryzma.LOGGER.info("CustomColors: lightmaps for dimensions {}", sets.keySet());
        }
        return sets.isEmpty() ? Data.EMPTY : new Data(Map.copyOf(sets));
    }

    public static void apply(Data d) {
        data = d;
    }

    // ------------------------------------------------------------------ per frame

    /** Records the new torch flicker; called at the end of {@code LightTexture.tick()}. */
    public static void onFlickerTick(float value) {
        prevFlicker = flicker;
        flicker = value;
    }

    public static int dimensionId(Level level) {
        if (level.dimension() == Level.NETHER) {
            return -1;
        }
        return level.dimension() == Level.END ? 1 : 0;
    }

    public static boolean isActive(ClientLevel level) {
        return PryzmaConfig.prCustomColors && data.byDimension().containsKey(dimensionId(level));
    }

    /** Inputs gathered by the {@code LightTexture} mixin; all are already interpolated. */
    public record Frame(float skyDarken, boolean lightningFlash, float partialTick, float rain, float thunder,
            float nightVision, float darkLight, float gamma) {
    }

    /**
     * Writes the 16x16 lightmap into {@code pixels} (x = block light, y = sky light, ABGR).
     * Returns {@code false} when this dimension has no custom lightmap.
     */
    public static boolean update(ClientLevel level, Frame f, NativeImage pixels) {
        Set set = data.byDimension().get(dimensionId(level));
        if (set == null) {
            return false;
        }
        boolean weather = level.dimension() != Level.NETHER && level.dimension() != Level.END;
        int[] abgr = compute(set, sunPosition(f.skyDarken(), f.lightningFlash()),
                torchPosition(f.partialTick()), weather ? f.rain() : 0.0F, weather ? f.thunder() : 0.0F,
                f.nightVision(), f.darkLight(), f.gamma());
        for (int sky = 0; sky < 16; sky++) {
            for (int block = 0; block < 16; block++) {
                pixels.setPixelRGBA(block, sky, abgr[sky * 16 + block]);
            }
        }
        return true;
    }

    /** Block-light axis position for this frame: the flicker random walk, interpolated between ticks. */
    static float torchPosition(float partialTick) {
        return Math.clamp(Mth.lerp(partialTick, prevFlicker, flicker) + 0.5F, 0.0F, 1.0F);
    }

    /**
     * The whole 16x16 table, row = sky light, column = block light, packed ABGR.
     *
     * @param rain    vanilla rain level
     * @param thunder vanilla thunder level, which already includes the rain factor (never above rain)
     */
    static int[] compute(Set set, float sun, float torch, float rain, float thunder, float nightVision,
            float darkLight, float gamma) {
        float wThunder = set.rain() != null ? Math.clamp(thunder, 0.0F, 1.0F) : 0.0F;
        float wRain = set.rain() != null ? Math.max(0.0F, Math.clamp(rain, 0.0F, 1.0F) - wThunder) : 0.0F;
        float wClear = 1.0F - wRain - wThunder;
        clear(SKY);
        clear(TORCH);
        boolean nvRows = sample(set.clear(), wClear, sun, torch, nightVision);
        if (wRain > 0.0F) {
            nvRows &= sample(set.rain(), wRain, sun, torch, nightVision);
        }
        if (wThunder > 0.0F) {
            nvRows &= sample(set.thunder(), wThunder, sun, torch, nightVision);
        }
        return combine(nightVision, darkLight, gamma, !nvRows);
    }

    /** OptiFine sun position: 0 at night, 1 at full day, 1 during a lightning flash. */
    static float sunPosition(float skyDarken, boolean lightningFlash) {
        return lightningFlash ? 1.0F : Math.clamp(1.1666666F * (skyDarken - 0.2F), 0.0F, 1.0F);
    }

    /** Accumulates one weather image; returns whether it carried its own night vision rows. */
    private static boolean sample(PrLightmapImage img, float weight, float sun, float torch, float nightVision) {
        if (weight <= 0.0F) {
            return true;
        }
        if (nightVision > 0.0F && img.hasNightVision()) {
            img.accumulate(0, sun, weight * (1.0F - nightVision), SKY);
            img.accumulate(16, torch, weight * (1.0F - nightVision), TORCH);
            img.accumulate(32, sun, weight * nightVision, SKY);
            img.accumulate(48, torch, weight * nightVision, TORCH);
            return true;
        }
        img.accumulate(0, sun, weight, SKY);
        img.accumulate(16, torch, weight, TORCH);
        return img.hasNightVision() || nightVision <= 0.0F;
    }

    /** OptiFine combination: sky + block - darkness, then the brightness (gamma) curve. */
    private static int[] combine(float nightVision, float darkLight, float gammaOption, boolean vanillaNightVision) {
        float gamma = Math.clamp(gammaOption, 0.0F, 1.0F);
        int[] out = new int[256];
        float[] c = new float[3];
        for (int sky = 0; sky < 16; sky++) {
            for (int block = 0; block < 16; block++) {
                for (int i = 0; i < 3; i++) {
                    c[i] = Math.clamp(SKY[sky][i] + TORCH[block][i] - darkLight, 0.0F, 1.0F);
                }
                if (vanillaNightVision && nightVision > 0.0F) {
                    float max = Math.max(c[0], Math.max(c[1], c[2]));
                    if (max > 0.0F && max < 1.0F) {
                        for (int i = 0; i < 3; i++) {
                            c[i] = Mth.lerp(nightVision, c[i], c[i] / max);
                        }
                    }
                }
                if (gamma > 1.0E-4F) {
                    for (int i = 0; i < 3; i++) {
                        float inv = 1.0F - c[i];
                        c[i] = gamma * (1.0F - inv * inv * inv * inv) + (1.0F - gamma) * c[i];
                    }
                }
                int r = (int) (c[0] * 255.0F);
                int g = (int) (c[1] * 255.0F);
                int b = (int) (c[2] * 255.0F);
                out[sky * 16 + block] = 0xFF000000 | b << 16 | g << 8 | r;
            }
        }
        return out;
    }

    private static void clear(float[][] a) {
        for (float[] row : a) {
            row[0] = 0.0F;
            row[1] = 0.0F;
            row[2] = 0.0F;
        }
    }

    /** Vanilla's darkness pulse ({@code LightTexture.calculateDarknessScale}). */
    public static float darknessScale(int tickCount, float gamma, float partialTick) {
        return Math.max(0.0F, Mth.cos((tickCount - partialTick) * (float) Math.PI * 0.025F) * 0.45F * gamma);
    }
}
