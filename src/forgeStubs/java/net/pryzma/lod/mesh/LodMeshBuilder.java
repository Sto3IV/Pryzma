package net.pryzma.lod.mesh;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * High-performance direct buffer builder for LOD vertices.
 * Binary format strictly matches Minecraft 1.21.1 DefaultVertexFormat.BLOCK (32 bytes per vertex):
 * <ul>
 *   <li>Offset 0:  Position (float x, y, z = 12 bytes)</li>
 *   <li>Offset 12: Color (ubyte r, g, b, a = 4 bytes)</li>
 *   <li>Offset 16: UV0 (float u, v = 8 bytes)</li>
 *   <li>Offset 24: UV2 Lightmap (short block, sky = 4 bytes)</li>
 *   <li>Offset 28: Normal (byte nx, ny, nz, padding = 4 bytes)</li>
 * </ul>
 */
public final class LodMeshBuilder {
    public static final int STRIDE = 32; // bytes per vertex
    private ByteBuffer byteBuffer;
    private int vertexCount = 0;
    private int capacityVertices;

    public LodMeshBuilder(int initialVertexCapacity) {
        this.capacityVertices = initialVertexCapacity;
        this.byteBuffer = ByteBuffer.allocateDirect(initialVertexCapacity * STRIDE).order(ByteOrder.nativeOrder());
    }

    private void ensureCapacity(int additionalVertices) {
        if (vertexCount + additionalVertices > capacityVertices) {
            int newCap = Math.max(capacityVertices * 2, vertexCount + additionalVertices);
            ByteBuffer newBuf = ByteBuffer.allocateDirect(newCap * STRIDE).order(ByteOrder.nativeOrder());
            byteBuffer.flip();
            newBuf.put(byteBuffer);
            byteBuffer = newBuf;
            capacityVertices = newCap;
        }
    }

    public void addVertex(float x, float y, float z,
                          int rgba,
                          float u, float v,
                          int skyLight, int blockLight,
                          float nx, float ny, float nz) {
        ensureCapacity(1);
        // 0: Position (float x, y, z)
        byteBuffer.putFloat(x);
        byteBuffer.putFloat(y);
        byteBuffer.putFloat(z);

        // 12: Color (ubyte r, g, b, a)
        byteBuffer.put((byte) ((rgba >> 16) & 0xFF));
        byteBuffer.put((byte) ((rgba >> 8) & 0xFF));
        byteBuffer.put((byte) (rgba & 0xFF));
        byteBuffer.put((byte) ((rgba >> 24) & 0xFF));

        // 16: UV0 Texture coordinates (float u, v)
        byteBuffer.putFloat(u);
        byteBuffer.putFloat(v);

        // 24: UV2 Lightmap (short blockLight, skyLight)
        byteBuffer.putShort((short) ((blockLight & 0xF) << 4));
        byteBuffer.putShort((short) ((skyLight & 0xF) << 4));

        // 28: Normal (byte nx, ny, nz, padding)
        byteBuffer.put((byte) (Math.clamp(nx, -1.0f, 1.0f) * 127.0f));
        byteBuffer.put((byte) (Math.clamp(ny, -1.0f, 1.0f) * 127.0f));
        byteBuffer.put((byte) (Math.clamp(nz, -1.0f, 1.0f) * 127.0f));
        byteBuffer.put((byte) 0);

        vertexCount++;
    }

    public void addQuad(float x0, float y0, float z0, float u0, float v0,
                        float x1, float y1, float z1, float u1, float v1,
                        float x2, float y2, float z2, float u2, float v2,
                        float x3, float y3, float z3, float u3, float v3,
                        float nx, float ny, float nz,
                        int rgba, int skyLight, int blockLight) {
        // Two triangles: (0, 1, 2) and (0, 2, 3)
        addVertex(x0, y0, z0, rgba, u0, v0, skyLight, blockLight, nx, ny, nz);
        addVertex(x1, y1, z1, rgba, u1, v1, skyLight, blockLight, nx, ny, nz);
        addVertex(x2, y2, z2, rgba, u2, v2, skyLight, blockLight, nx, ny, nz);

        addVertex(x0, y0, z0, rgba, u0, v0, skyLight, blockLight, nx, ny, nz);
        addVertex(x2, y2, z2, rgba, u2, v2, skyLight, blockLight, nx, ny, nz);
        addVertex(x3, y3, z3, rgba, u3, v3, skyLight, blockLight, nx, ny, nz);
    }

    public int getVertexCount() {
        return vertexCount;
    }

    public ByteBuffer getByteBuffer() {
        ByteBuffer slice = byteBuffer.duplicate();
        slice.flip();
        return slice;
    }

    public void clear() {
        byteBuffer.clear();
        vertexCount = 0;
    }
}
