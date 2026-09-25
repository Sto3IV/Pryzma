package net.pryzma.render;

import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLCapabilities;

import net.pryzma.PryzmaConfig;

/**
 * Mipmap Type and Anisotropic Filtering for mipmapped textures (the block atlas). Vanilla sets
 * the block atlas filter every frame, so both apply at once without a texture reload.
 */
public final class PrTextureFilter {
    private static int anisotropy = -1;
    private static float maxAnisotropy;

    private PrTextureFilter() {
    }

    /** GL minification filter for mipmapped, non-blurred textures: Nearest, Linear, Bilinear, Trilinear. */
    public static int minFilter() {
        return switch (PryzmaConfig.prMipmapType) {
            case 1 -> GL11.GL_NEAREST_MIPMAP_LINEAR;
            case 2 -> GL11.GL_LINEAR_MIPMAP_NEAREST;
            case 3 -> GL11.GL_LINEAR_MIPMAP_LINEAR;
            default -> GL11.GL_NEAREST_MIPMAP_NEAREST;
        };
    }

    /** Sets the anisotropy of the bound texture; level 1 is off. Render thread. */
    public static void applyAnisotropy() {
        if (anisotropy < 0) {
            GLCapabilities caps = GL.getCapabilities();
            anisotropy = caps.GL_EXT_texture_filter_anisotropic || caps.GL_ARB_texture_filter_anisotropic ? 1 : 0;
            if (anisotropy == 1) {
                maxAnisotropy = GL11.glGetFloat(EXTTextureFilterAnisotropic.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT);
            }
        }
        if (anisotropy == 1) {
            float level = Math.max(1.0F, Math.min(PryzmaConfig.prAfLevel, maxAnisotropy));
            GL11.glTexParameterf(GL11.GL_TEXTURE_2D, EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT, level);
        }
    }
}
