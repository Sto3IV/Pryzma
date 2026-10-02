package net.pryzma.render;

/**
 * Phase B4: Decorator LOD Truncation.
 * Duck interface on {@link com.mojang.blaze3d.vertex.MeshData} carrying the vertex count of the core
 * (non-decorator) geometry. {@code -1} indicates no split (entire mesh is core).
 */
public interface IPrMeshData {
    int pryzma$getCoreVertices();

    void pryzma$setCoreVertices(int count);
}
