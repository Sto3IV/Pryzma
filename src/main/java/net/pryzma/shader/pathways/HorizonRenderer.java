package net.pryzma.shader.pathways;

import net.minecraft.client.Minecraft;
import org.joml.Matrix4fc;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;

import java.nio.FloatBuffer;

/**
 * Renders an octagonal prism around the world for custom horizon rendering.
 * Adapted for Minecraft 26.3 using direct LWJGL OpenGL geometry buffers.
 */
public class HorizonRenderer {
	private static final float TOP = 16.0F;
	private static final float BOTTOM = -16.0F;
	private static final float COS_22_5 = (float) Math.cos(Math.toRadians(22.5));
	private static final float SIN_22_5 = (float) Math.sin(Math.toRadians(22.5));

	private int vao = 0;
	private int vbo = 0;
	private int vertexCount = 0;
	private int currentRenderDistance;

	public HorizonRenderer() {
		currentRenderDistance = Minecraft.getInstance().options.getEffectiveRenderDistance();
		rebuildBuffer();
	}

	private void rebuildBuffer() {
		destroy();

		// Calculate total vertices needed
		// 8 prism quads = 32 vertices
		// Top plane: (384 * 2 / 64 + 1)^2 * 4 ~= 676 * 4 ~= 2704 vertices
		// Bottom plane: ~= 2704 vertices
		// Total buffer size: 8192 vertices * 3 floats = 24576 floats
		FloatBuffer buffer = BufferUtils.createFloatBuffer(32768);
		vertexCount = 0;

		buildHorizon(currentRenderDistance * 16, buffer);
		buffer.flip();

		vao = GL30C.glGenVertexArrays();
		vbo = GL15C.glGenBuffers();

		GL30C.glBindVertexArray(vao);
		GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, vbo);
		GL15C.glBufferData(GL15C.GL_ARRAY_BUFFER, buffer, GL15C.GL_STATIC_DRAW);

		// Attribute 0: vec3 position
		GL20C.glEnableVertexAttribArray(0);
		GL20C.glVertexAttribPointer(0, 3, GL11C.GL_FLOAT, false, 3 * Float.BYTES, 0);

		GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, 0);
		GL30C.glBindVertexArray(0);
	}

	private void addVertex(FloatBuffer buffer, float x, float y, float z) {
		buffer.put(x).put(y).put(z);
		vertexCount++;
	}

	private void buildQuad(FloatBuffer buffer, float x1, float z1, float x2, float z2) {
		addVertex(buffer, x1, BOTTOM, z1);
		addVertex(buffer, x1, TOP, z1);
		addVertex(buffer, x2, TOP, z2);
		addVertex(buffer, x2, BOTTOM, z2);
	}

	private void buildHalf(FloatBuffer buffer, float adjacent, float opposite, boolean invert) {
		if (invert) {
			adjacent = -adjacent;
			opposite = -opposite;
		}

		buildQuad(buffer, adjacent, -opposite, opposite, -adjacent);
		buildQuad(buffer, adjacent, opposite, adjacent, -opposite);
		buildQuad(buffer, opposite, adjacent, adjacent, opposite);
		buildQuad(buffer, -opposite, adjacent, opposite, adjacent);
	}

	private void buildOctagonalPrism(FloatBuffer buffer, float adjacent, float opposite) {
		buildHalf(buffer, adjacent, opposite, false);
		buildHalf(buffer, adjacent, opposite, true);
	}

	private void buildRegularOctagonalPrism(FloatBuffer buffer, float radius) {
		buildOctagonalPrism(buffer, radius * COS_22_5, radius * SIN_22_5);
	}

	private void buildBottomPlane(FloatBuffer buffer, int radius) {
		for (int x = -radius; x <= radius; x += 64) {
			for (int z = -radius; z <= radius; z += 64) {
				addVertex(buffer, x + 64, BOTTOM, z);
				addVertex(buffer, x, BOTTOM, z);
				addVertex(buffer, x, BOTTOM, z + 64);
				addVertex(buffer, x + 64, BOTTOM, z + 64);
			}
		}
	}

	private void buildTopPlane(FloatBuffer buffer, int radius) {
		for (int x = -radius; x <= radius; x += 64) {
			for (int z = -radius; z <= radius; z += 64) {
				addVertex(buffer, x + 64, TOP, z);
				addVertex(buffer, x + 64, TOP, z + 64);
				addVertex(buffer, x, TOP, z + 64);
				addVertex(buffer, x, TOP, z);
			}
		}
	}

	private void buildHorizon(int radius, FloatBuffer buffer) {
		if (radius > 256) {
			radius = 256;
		}

		buildRegularOctagonalPrism(buffer, radius);
		buildTopPlane(buffer, 384);
		buildBottomPlane(buffer, 384);
	}

	public void renderHorizon(Matrix4fc modelView, Matrix4fc projection) {
		if (currentRenderDistance != Minecraft.getInstance().options.getEffectiveRenderDistance()) {
			currentRenderDistance = Minecraft.getInstance().options.getEffectiveRenderDistance();
			rebuildBuffer();
		}

		if (vao != 0 && vertexCount > 0) {
			GL30C.glBindVertexArray(vao);
			GL11C.glDrawArrays(GL11C.GL_QUADS, 0, vertexCount);
			GL30C.glBindVertexArray(0);
		}
	}

	public void destroy() {
		if (vao != 0) {
			GL30C.glDeleteVertexArrays(vao);
			vao = 0;
		}
		if (vbo != 0) {
			GL15C.glDeleteBuffers(vbo);
			vbo = 0;
		}
		vertexCount = 0;
	}
}
