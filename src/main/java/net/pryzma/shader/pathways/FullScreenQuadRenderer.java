package net.pryzma.shader.pathways;

import com.mojang.blaze3d.systems.RenderSystem;
import net.pryzma.shader.gl.ShaderRenderSystem;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

/**
 * Renders a full-screen textured quad to the screen. Used in composite / deferred rendering.
 */
public class FullScreenQuadRenderer {
	public static final FullScreenQuadRenderer INSTANCE = new FullScreenQuadRenderer();

	private int vao = -1;
	private int vbo = -1;

	private FullScreenQuadRenderer() {
	}

	private void ensureInitialized() {
		if (vao != -1) {
			return;
		}

		vao = GL30C.glGenVertexArrays();
		vbo = GL15C.glGenBuffers();

		GL30C.glBindVertexArray(vao);
		GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, vbo);

		float[] vertices = {
			0.0F, 0.0F, 0.0F, 0.0F, 0.0F,
			1.0F, 0.0F, 0.0F, 1.0F, 0.0F,
			1.0F, 1.0F, 0.0F, 1.0F, 1.0F,

			0.0F, 0.0F, 0.0F, 0.0F, 0.0F,
			1.0F, 1.0F, 0.0F, 1.0F, 1.0F,
			0.0F, 1.0F, 0.0F, 0.0F, 1.0F
		};

		FloatBuffer buffer = MemoryUtil.memAllocFloat(vertices.length);
		buffer.put(vertices).flip();
		GL15C.glBufferData(GL15C.GL_ARRAY_BUFFER, buffer, GL15C.GL_STATIC_DRAW);
		MemoryUtil.memFree(buffer);

		// Position: attribute 0, 3 floats
		GL20C.glEnableVertexAttribArray(0);
		GL20C.glVertexAttribPointer(0, 3, GL11C.GL_FLOAT, false, 5 * Float.BYTES, 0);

		// UV: attribute 1, 2 floats
		GL20C.glEnableVertexAttribArray(1);
		GL20C.glVertexAttribPointer(1, 2, GL11C.GL_FLOAT, false, 5 * Float.BYTES, 3 * Float.BYTES);

		GL30C.glBindVertexArray(0);
		GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, 0);
	}

	public void render() {
		begin();
		renderQuad();
		end();
	}

	public void begin() {
		ensureInitialized();
		GL11C.glDisable(GL11C.GL_DEPTH_TEST);
		GL30C.glBindVertexArray(vao);
	}

	public void renderQuad() {
		ShaderRenderSystem.overridePolygonMode();
		GL11C.glDrawArrays(GL11C.GL_TRIANGLES, 0, 6);
		ShaderRenderSystem.restorePolygonMode();
	}

	public void end() {
		GL30C.glBindVertexArray(0);
		GL11C.glEnable(GL11C.GL_DEPTH_TEST);
	}
}
