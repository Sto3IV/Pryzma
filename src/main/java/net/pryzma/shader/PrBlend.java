package net.pryzma.shader;

import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import com.mojang.blaze3d.systems.RenderSystem;

/**
 * {@code blend.<program>=<src> <dst> [<srcAlpha> <dstAlpha>]} or {@code off}: the blending a
 * program draws with. {@link #OFF} draws without blending; a missing setting keeps what the
 * draw would do anyway.
 */
record PrBlend(boolean enabled, int src, int dst, int srcAlpha, int dstAlpha) {
    static final PrBlend OFF = new PrBlend(false, 0, 0, 0, 0);

    private static final Map<String, Integer> FACTORS = Map.ofEntries(
            Map.entry("ZERO", 0), Map.entry("ONE", 1), Map.entry("SRC_COLOR", 0x300), Map.entry("ONE_MINUS_SRC_COLOR", 0x301),
            Map.entry("SRC_ALPHA", 0x302), Map.entry("ONE_MINUS_SRC_ALPHA", 0x303), Map.entry("DST_ALPHA", 0x304),
            Map.entry("ONE_MINUS_DST_ALPHA", 0x305), Map.entry("DST_COLOR", 0x306), Map.entry("ONE_MINUS_DST_COLOR", 0x307),
            Map.entry("SRC_ALPHA_SATURATE", 0x308));

    /** The setting of {@code text}, or {@code null} when absent or malformed. */
    static PrBlend parse(String text, Consumer<String> warn) {
        if (text == null) {
            return null;
        }
        String[] parts = text.trim().toUpperCase(Locale.ROOT).split("\\s+");
        if (parts.length == 1 && parts[0].equals("OFF")) {
            return OFF;
        }
        if (parts.length != 2 && parts.length != 4) {
            warn.accept("Invalid blend: " + text);
            return null;
        }
        Integer[] f = new Integer[4];
        for (int i = 0; i < 4; i++) {
            f[i] = FACTORS.get(parts[parts.length == 2 ? i % 2 : i]);
            if (f[i] == null) {
                warn.accept("Invalid blend factor in: " + text);
                return null;
            }
        }
        return new PrBlend(true, f[0], f[1], f[2], f[3]);
    }

    /** Applies a setting; {@code null} restores the default (no blending, standard factors). */
    static void apply(PrBlend blend) {
        if (blend == null || !blend.enabled) {
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
        } else {
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(blend.src, blend.dst, blend.srcAlpha, blend.dstAlpha);
        }
    }
}
