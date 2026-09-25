package net.pryzma.sky;

import com.mojang.blaze3d.platform.GlStateManager.DestFactor;
import com.mojang.blaze3d.platform.GlStateManager.SourceFactor;
import com.mojang.blaze3d.systems.RenderSystem;

/** OptiFine sky layer blend modes, with OptiFine's exact GL factors and brightness channel. */
public enum PrSkyBlend {
    ALPHA(SourceFactor.SRC_ALPHA, DestFactor.ONE_MINUS_SRC_ALPHA, false),
    ADD(SourceFactor.SRC_ALPHA, DestFactor.ONE, false),
    SUBTRACT(SourceFactor.ONE_MINUS_DST_COLOR, DestFactor.ZERO, true),
    MULTIPLY(SourceFactor.DST_COLOR, DestFactor.ONE_MINUS_SRC_ALPHA, true),
    DODGE(SourceFactor.ONE, DestFactor.ONE, true),
    BURN(SourceFactor.ZERO, DestFactor.ONE_MINUS_SRC_COLOR, true),
    SCREEN(SourceFactor.ONE, DestFactor.ONE_MINUS_SRC_COLOR, true),
    OVERLAY(SourceFactor.DST_COLOR, DestFactor.SRC_COLOR, true),
    REPLACE(null, null, false);

    private final SourceFactor src;
    private final DestFactor dst;
    /** Brightness scales RGB (true) or alpha (false). */
    private final boolean rgbBrightness;

    PrSkyBlend(SourceFactor src, DestFactor dst, boolean rgbBrightness) {
        this.src = src;
        this.dst = dst;
        this.rgbBrightness = rgbBrightness;
    }

    public static PrSkyBlend parse(String s) {
        if (s == null) {
            return ADD;
        }
        return switch (s.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "alpha" -> ALPHA;
            case "subtract" -> SUBTRACT;
            case "multiply" -> MULTIPLY;
            case "dodge" -> DODGE;
            case "burn" -> BURN;
            case "screen" -> SCREEN;
            case "overlay" -> OVERLAY;
            case "replace" -> REPLACE;
            default -> ADD;
        };
    }

    void apply(float brightness) {
        if (this == REPLACE) {
            RenderSystem.disableBlend();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, brightness);
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(src, dst);
        if (this == MULTIPLY) {
            RenderSystem.setShaderColor(brightness, brightness, brightness, brightness);
        } else if (rgbBrightness) {
            RenderSystem.setShaderColor(brightness, brightness, brightness, 1.0F);
        } else {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, brightness);
        }
    }
}
