package net.pryzma.shader;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import com.mojang.blaze3d.platform.GlStateManager;

/**
 * Texture binding past the game's reach: its state cache tracks 12 texture units, and shader
 * packs sample more. Units below 12 go through the cache; higher units are bound directly, and
 * the active unit is put back so the cache stays true.
 */
final class PrGl {
    /** Units the game's texture state tracks. */
    static final int TRACKED_UNITS = 12;

    private PrGl() {
    }

    static void bindTexture(int unit, int texture) {
        if (unit < TRACKED_UNITS) {
            GlStateManager._activeTexture(GL13.GL_TEXTURE0 + unit);
            GlStateManager._bindTexture(texture);
        } else {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        }
    }

    /** Makes GL's active unit match the game's cached one again after direct binds. */
    static void restoreActiveTexture() {
        GL13.glActiveTexture(GlStateManager._getActiveTexture());
    }
}
