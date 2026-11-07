package net.pryzma.shader.gl.blending;

import com.mojang.renderpearl.backend.opengl.GlStateManager;
import org.lwjgl.opengl.GL11C;

import java.nio.ByteBuffer;

public class DepthColorStorage {
	private static boolean originalDepthEnable;
	private static ColorMask originalColor;
	private static boolean depthColorLocked;

	public static boolean isDepthColorLocked() {
		return depthColorLocked;
	}

	public static void disableDepthColor() {
		if (!depthColorLocked) {
			// Only save the previous state if the depth and color mask wasn't already locked
			originalDepthEnable = GL11C.glGetBoolean(GL11C.GL_DEPTH_WRITEMASK);
			ByteBuffer buf = ByteBuffer.allocateDirect(4);
			GL11C.glGetBooleanv(GL11C.GL_COLOR_WRITEMASK, buf);
			originalColor = new ColorMask(buf.get(0) != 0, buf.get(1) != 0, buf.get(2) != 0, buf.get(3) != 0);
		}

		depthColorLocked = false;

		GlStateManager._depthMask(false);
		GlStateManager._colorMask(0);

		depthColorLocked = true;
	}

	public static void deferDepthEnable(boolean enabled) {
		originalDepthEnable = enabled;
	}

	public static void deferColorMask(boolean red, boolean green, boolean blue, boolean alpha) {
		originalColor = new ColorMask(red, green, blue, alpha);
	}

	public static void unlockDepthColor() {
		if (!depthColorLocked) {
			return;
		}

		depthColorLocked = false;

		GlStateManager._depthMask(originalDepthEnable);
		GlStateManager._colorMask(originalColor != null ? originalColor.toMask() : 15);
	}
}
