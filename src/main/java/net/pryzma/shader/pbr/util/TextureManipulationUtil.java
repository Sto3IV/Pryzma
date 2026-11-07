package net.pryzma.shader.pbr.util;

import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL30C;

public class TextureManipulationUtil {
	private static int colorFillFBO = -1;

	public static void fillWithColor(int textureId, int maxLevel, int rgba) {
		if (colorFillFBO == -1) {
			colorFillFBO = GL30C.glGenFramebuffers();
		}

		int previousFramebufferId = GL11C.glGetInteger(GL30C.GL_FRAMEBUFFER_BINDING);
		float[] previousClearColor = new float[4];
		GL11C.glGetFloatv(GL11C.GL_COLOR_CLEAR_VALUE, previousClearColor);
		int previousTextureId = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
		int[] previousViewport = new int[4];
		GL11C.glGetIntegerv(GL11C.GL_VIEWPORT, previousViewport);

		GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, colorFillFBO);
		GL11C.glClearColor(
			(rgba >> 24 & 0xFF) / 255.0f,
			(rgba >> 16 & 0xFF) / 255.0f,
			(rgba >> 8 & 0xFF) / 255.0f,
			(rgba & 0xFF) / 255.0f
		);
		GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, textureId);
		for (int level = 0; level <= maxLevel; ++level) {
			int width = GL11C.glGetTexLevelParameteri(GL11C.GL_TEXTURE_2D, level, GL11C.GL_TEXTURE_WIDTH);
			int height = GL11C.glGetTexLevelParameteri(GL11C.GL_TEXTURE_2D, level, GL11C.GL_TEXTURE_HEIGHT);
			GL11C.glViewport(0, 0, width, height);
			GL30C.glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0, GL11C.GL_TEXTURE_2D, textureId, level);
			GL11C.glClear(GL11C.GL_COLOR_BUFFER_BIT);
			GL30C.glFramebufferTexture2D(GL30C.GL_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0, GL11C.GL_TEXTURE_2D, 0, level);
		}

		GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, previousFramebufferId);
		GL11C.glClearColor(previousClearColor[0], previousClearColor[1], previousClearColor[2], previousClearColor[3]);
		GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, previousTextureId);
		GL11C.glViewport(previousViewport[0], previousViewport[1], previousViewport[2], previousViewport[3]);
	}
}
