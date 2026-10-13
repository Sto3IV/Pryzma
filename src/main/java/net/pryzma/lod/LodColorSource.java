package net.pryzma.lod;

/**
 * Palette of block states for {@link NativeLodMesher}: the untinted albedo of a state's top face and its material.
 * The mesher multiplies the albedo with the column's biome tint; shading is left to the shaders. Called from meshing
 * threads.
 */
@FunctionalInterface
public interface LodColorSource {
    /** RGB ({@code 0xRRGGBB}) base colour of block state {@code stateId}; the alpha byte is ignored. */
    int baseColor(int stateId);

    /** {@link LodMaterial} id of block state {@code stateId}. */
    default int material(int stateId) {
        return LodMaterial.UNKNOWN;
    }
}
