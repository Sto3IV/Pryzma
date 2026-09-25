package net.pryzma.item;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * Blend modes of enchantment layers ({@code blend=}), with the GL factors and the colour rule of
 * OptiFine's {@code Blender}: the layer's strength goes into alpha, into RGB, or into both.
 */
public enum PrCitBlend {
    ALPHA(0x0302, 0x0303, Strength.ALPHA),
    ADD(0x0302, 1, Strength.ALPHA),
    SUBTRACT(0x0307, 0, Strength.RGB),
    MULTIPLY(0x0306, 0x0303, Strength.RGBA),
    DODGE(1, 1, Strength.RGB),
    BURN(0, 0x0301, Strength.RGB),
    SCREEN(1, 0x0301, Strength.RGB),
    OVERLAY(0x0306, 0x0300, Strength.RGB),
    REPLACE(-1, -1, Strength.ALPHA);

    private enum Strength {
        ALPHA, RGB, RGBA
    }

    /** GL source and destination factors; {@code -1} for REPLACE, which draws without blending. */
    final int src;
    final int dst;
    private final Strength strength;

    PrCitBlend(int src, int dst, Strength strength) {
        this.src = src;
        this.dst = dst;
        this.strength = strength;
    }

    /** The shader colour that draws a layer at {@code strength} (0..1). */
    float[] color(float strength) {
        return switch (this.strength) {
            case ALPHA -> new float[] {1.0F, 1.0F, 1.0F, strength};
            case RGB -> new float[] {strength, strength, strength, 1.0F};
            case RGBA -> new float[] {strength, strength, strength, strength};
        };
    }

    /** OptiFine {@code Blender.parseBlend}: missing or unknown means {@code add}. */
    static PrCitBlend parse(String text, Consumer<String> warn) {
        if (text == null) {
            return ADD;
        }
        String s = text.trim().toLowerCase(Locale.ROOT);
        for (PrCitBlend blend : values()) {
            if (blend.name().toLowerCase(Locale.ROOT).equals(s)) {
                return blend;
            }
        }
        warn.accept("Unknown blend: " + text);
        return ADD;
    }
}
