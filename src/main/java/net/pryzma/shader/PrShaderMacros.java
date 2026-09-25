package net.pryzma.shader;

import java.util.Locale;

import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.platform.GlUtil;

import net.minecraft.Util;

/**
 * The standard macros OptiFine defines in every program ({@code MC_VERSION}, {@code MC_GL_VERSION},
 * the OS, GPU vendor and renderer, quality settings, {@code MC_RENDER_STAGE_*}), read from the
 * live GL context.
 */
public final class PrShaderMacros {
    /** OptiFine's render stages, in order: their ordinal is the {@code renderStage} uniform. */
    public enum Stage {
        NONE, SKY, SUNSET, CUSTOM_SKY, SUN, MOON, STARS, VOID, TERRAIN_SOLID, TERRAIN_CUTOUT_MIPPED, TERRAIN_CUTOUT,
        ENTITIES, BLOCK_ENTITIES, DESTROY, OUTLINE, DEBUG, HAND_SOLID, TERRAIN_TRANSLUCENT, TRIPWIRE, PARTICLES, CLOUDS,
        RAIN_SNOW, WORLD_BORDER, HAND_TRANSLUCENT
    }

    public static final int MC_VERSION = 12101;
    public static final float HAND_DEPTH = 0.125F;

    private PrShaderMacros() {
    }

    public static String header() {
        StringBuilder sb = new StringBuilder(1024);
        line(sb, "MC_VERSION " + MC_VERSION);
        int gl = glVersion(GL11.glGetString(GL11.GL_VERSION));
        line(sb, "MC_GL_VERSION " + gl);
        line(sb, "MC_GLSL_VERSION " + glVersion(GL11.glGetString(0x8B8C)));
        line(sb, switch (Util.getPlatform()) {
            case WINDOWS -> "MC_OS_WINDOWS";
            case OSX -> "MC_OS_MAC";
            case LINUX -> "MC_OS_LINUX";
            default -> "MC_OS_OTHER";
        });
        String vendor = lower(GlUtil.getVendor());
        String renderer = lower(GlUtil.getRenderer());
        line(sb, vendor.startsWith("ati") ? "MC_GL_VENDOR_ATI"
                : vendor.contains("amd") || vendor.contains("advanced micro") ? "MC_GL_VENDOR_AMD"
                : vendor.startsWith("intel") ? "MC_GL_VENDOR_INTEL"
                : vendor.startsWith("nvidia") ? "MC_GL_VENDOR_NVIDIA"
                : vendor.contains("mesa") ? "MC_GL_VENDOR_MESA"
                : vendor.startsWith("x.org") ? "MC_GL_VENDOR_XORG" : "MC_GL_VENDOR_OTHER");
        line(sb, renderer.contains("radeon") || renderer.startsWith("amd") || renderer.startsWith("ati") ? "MC_GL_RENDERER_RADEON"
                : renderer.startsWith("gallium") ? "MC_GL_RENDERER_GALLIUM"
                : renderer.startsWith("intel") ? "MC_GL_RENDERER_INTEL"
                : renderer.startsWith("geforce") || renderer.startsWith("nvidia") ? "MC_GL_RENDERER_GEFORCE"
                : renderer.startsWith("quadro") || renderer.startsWith("nvs") ? "MC_GL_RENDERER_QUADRO"
                : renderer.startsWith("mesa") ? "MC_GL_RENDERER_MESA" : "MC_GL_RENDERER_OTHER");
        line(sb, "MC_RENDER_QUALITY 1.0");
        line(sb, "MC_SHADOW_QUALITY 1.0");
        line(sb, "MC_HAND_DEPTH " + HAND_DEPTH);
        for (Stage stage : Stage.values()) {
            line(sb, "MC_RENDER_STAGE_" + stage.name() + " " + stage.ordinal());
        }
        return sb.toString();
    }

    private static void line(StringBuilder sb, String macro) {
        sb.append("#define ").append(macro).append('\n');
    }

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    /** "4.6.0 Compatibility..." and the GLSL form "4.60 ..." both → 460. */
    static int glVersion(String text) {
        if (text == null) {
            return 0;
        }
        String[] parts = text.trim().split("[ .]");
        try {
            int major = Integer.parseInt(parts[0]);
            String minor = parts.length > 1 ? parts[1].replaceAll("\\D.*", "") : "0";
            int m = minor.isEmpty() ? 0 : Integer.parseInt(minor);
            return major * 100 + (minor.length() >= 2 ? m : m * 10);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
