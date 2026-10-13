package net.pryzma.lod;

import java.nio.ByteBuffer;

/**
 * The output of {@link NativeLodMesher} for one sector at one detail level: quads in the
 * {@link NativeLodMesher#VERTEX_BYTES}-byte vertex layout, four vertices per quad, positioned relative to the
 * sector's minimum corner on x and z and biased by {@link NativeLodMesher#Y_BIAS} on y. The opaque quads (ground
 * and canopy) come first, the translucent water quads after them, so one buffer serves both passes. Lives on the
 * heap until the render thread uploads it.
 *
 * @param vertices    native-order vertex data, position 0, limit at the end of the last quad
 * @param opaqueQuads quads of the opaque pass, at the start of {@code vertices}
 * @param waterQuads  quads of the translucent pass, after the opaque ones
 * @param minY        lowest absolute y of any vertex (the sector's culling box); 0 when empty
 * @param maxY        highest absolute y of any vertex; 0 when empty
 */
public record LodMesh(int sectorX, int sectorZ, int level, ByteBuffer vertices, int opaqueQuads, int waterQuads, int minY, int maxY) {
    /** All quads, opaque and water. */
    public int quadCount() {
        return opaqueQuads + waterQuads;
    }

    public boolean isEmpty() {
        return quadCount() == 0;
    }

    /** Bytes of vertex data. */
    public int byteSize() {
        return quadCount() * NativeLodMesher.QUAD_BYTES;
    }
}
