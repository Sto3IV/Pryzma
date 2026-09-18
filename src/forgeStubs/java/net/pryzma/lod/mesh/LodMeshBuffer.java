package net.pryzma.lod.mesh;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;

/**
 * OpenGL VAO and VBO wrapper for uploading and drawing LOD meshes.
 * Strictly adheres to DefaultVertexFormat.BLOCK attributes so shaders
 * render terrain with correct lighting, fog, and texture mapping.
 */
public final class LodMeshBuffer {
    private int vaoId = 0;
    private int vboId = 0;
    private int vertexCount = 0;
    private boolean uploaded = false;

    public synchronized void upload(ByteBuffer buffer, int count) {
        delete();
        if (count <= 0 || buffer == null || !buffer.hasRemaining()) {
            return;
        }

        try {
            vaoId = GL30.glGenVertexArrays();
            vboId = GL15.glGenBuffers();

            GL30.glBindVertexArray(vaoId);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buffer, GL15.GL_STATIC_DRAW);

            int stride = LodMeshBuilder.STRIDE; // 32 bytes

            // Attrib 0: Position (float x, y, z)
            GL20.glEnableVertexAttribArray(0);
            GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, stride, 0);

            // Attrib 1: Color (ubyte r, g, b, a normalized)
            GL20.glEnableVertexAttribArray(1);
            GL20.glVertexAttribPointer(1, 4, GL11.GL_UNSIGNED_BYTE, true, stride, 12);

            // Attrib 2: UV0 Texture coordinates (float u, v)
            GL20.glEnableVertexAttribArray(2);
            GL20.glVertexAttribPointer(2, 2, GL11.GL_FLOAT, false, stride, 16);

            // Attrib 3: UV2 Lightmap (short blockLight, skyLight)
            GL20.glEnableVertexAttribArray(3);
            GL20.glVertexAttribPointer(3, 2, GL11.GL_SHORT, false, stride, 24);

            // Attrib 4: Normal (byte nx, ny, nz normalized)
            GL20.glEnableVertexAttribArray(4);
            GL20.glVertexAttribPointer(4, 3, GL11.GL_BYTE, true, stride, 28);

            GL30.glBindVertexArray(0);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

            this.vertexCount = count;
            this.uploaded = true;
        } catch (Throwable t) {
            delete();
        }
    }

    public void render() {
        if (!uploaded || vaoId == 0 || vertexCount <= 0) {
            return;
        }

        GL30.glBindVertexArray(vaoId);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, vertexCount);
        GL30.glBindVertexArray(0);
    }

    public synchronized void delete() {
        if (vboId != 0) {
            try {
                GL15.glDeleteBuffers(vboId);
            } catch (Throwable ignored) {
            }
            vboId = 0;
        }
        if (vaoId != 0) {
            try {
                GL30.glDeleteVertexArrays(vaoId);
            } catch (Throwable ignored) {
            }
            vaoId = 0;
        }
        vertexCount = 0;
        uploaded = false;
    }

    public boolean isUploaded() {
        return uploaded;
    }

    public int getVertexCount() {
        return vertexCount;
    }
}
