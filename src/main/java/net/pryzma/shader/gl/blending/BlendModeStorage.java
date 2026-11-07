package net.pryzma.shader.gl.blending;

import com.mojang.renderpearl.backend.opengl.GlStateManager;
import net.pryzma.shader.gl.ShaderRenderSystem;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL14C;
import org.lwjgl.opengl.GL20C;

public class BlendModeStorage {
	private static boolean originalBlendEnable;
	private static BlendMode originalBlend;
	private static boolean blendLocked;

	public static boolean isBlendLocked() {
		return blendLocked;
	}

	public static void overrideBlend(BlendMode override) {
		if (!blendLocked) {
			// Only save the previous state if the blend mode wasn't already locked
			originalBlendEnable = GL11C.glIsEnabled(GL11C.GL_BLEND);
			originalBlend = new BlendMode(
				GL11C.glGetInteger(GL14C.GL_BLEND_SRC_RGB),
				GL11C.glGetInteger(GL14C.GL_BLEND_DST_RGB),
				GL11C.glGetInteger(GL20C.GL_BLEND_SRC_ALPHA),
				GL11C.glGetInteger(GL20C.GL_BLEND_DST_ALPHA)
			);
		}

		blendLocked = false;

		if (override == null) {
			GlStateManager._disableBlend(0);
		} else {
			GlStateManager._enableBlend(0);
			GlStateManager._blendFuncSeparate(override.srcRgb(), override.dstRgb(), override.srcAlpha(), override.dstAlpha());
		}

		blendLocked = true;
	}

	public static void overrideBufferBlend(int index, BlendMode override) {
		if (!blendLocked) {
			// Only save the previous state if the blend mode wasn't already locked
			originalBlendEnable = GL11C.glIsEnabled(GL11C.GL_BLEND);
			originalBlend = new BlendMode(
				GL11C.glGetInteger(GL14C.GL_BLEND_SRC_RGB),
				GL11C.glGetInteger(GL14C.GL_BLEND_DST_RGB),
				GL11C.glGetInteger(GL20C.GL_BLEND_SRC_ALPHA),
				GL11C.glGetInteger(GL20C.GL_BLEND_DST_ALPHA)
			);
		}

		if (override == null) {
			ShaderRenderSystem.disableBufferBlend(index);
		} else {
			ShaderRenderSystem.enableBufferBlend(index);
			ShaderRenderSystem.blendFuncSeparatei(index, override.srcRgb(), override.dstRgb(), override.srcAlpha(), override.dstAlpha());
		}

		blendLocked = true;
	}

	public static void deferBlendModeToggle(boolean enabled) {
		originalBlendEnable = enabled;
	}

	public static void deferBlendFunc(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
		originalBlend = new BlendMode(srcRgb, dstRgb, srcAlpha, dstAlpha);
	}

	public static void restoreBlend() {
		if (!blendLocked) {
			return;
		}

		blendLocked = false;

		if (originalBlendEnable) {
			GlStateManager._enableBlend(0);
		} else {
			GlStateManager._disableBlend(0);
		}

		if (originalBlend != null) {
			GlStateManager._blendFuncSeparate(originalBlend.srcRgb(), originalBlend.dstRgb(),
				originalBlend.srcAlpha(), originalBlend.dstAlpha());
		}
	}
}
