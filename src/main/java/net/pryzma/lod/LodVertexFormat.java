package net.pryzma.lod;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;

/**
 * The GL side of the {@link NativeLodMesher} vertex layout, which is the Distant Horizons vertex ABI that shader packs'
 * {@code dh_terrain} and {@code dh_water} programs are written against:
 * <pre>
 * location 0  vPosition  uvec4  x, y, z (unsigned shorts) and meta (light in the low byte)   glVertexAttribIPointer
 * location 1  iris_color vec4   r, g, b, a (normalized unsigned bytes)
 * location 2  irisExtra  uvec4  material, normal index, 0, 0 (unsigned bytes)              glVertexAttribIPointer
 * </pre>
 * The native {@code pryzma:lod_terrain} shader reads the same attributes, so one vertex buffer and one VAO serve both
 * the vanilla pass and a shader pack's G-buffer pass. {@code UV}-usage elements of integer types are bound with
 * {@code glVertexAttribIPointer}; {@link VertexFormat#setupBufferState()} assigns locations in element order.
 */
public final class LodVertexFormat {
    /** Position and meta: four unsigned shorts read as {@code uvec4}. */
    public static final VertexFormatElement POSITION_META = VertexFormatElement.register(VertexFormatElement.findNextId(), 0,
            VertexFormatElement.Type.USHORT, VertexFormatElement.Usage.UV, 4);
    /** Material and normal index: four unsigned bytes read as {@code uvec4}. */
    public static final VertexFormatElement EXTRA = VertexFormatElement.register(VertexFormatElement.findNextId(), 0,
            VertexFormatElement.Type.UBYTE, VertexFormatElement.Usage.UV, 4);

    public static final String ATTRIBUTE_POSITION = "vPosition";
    public static final String ATTRIBUTE_COLOR = "iris_color";
    public static final String ATTRIBUTE_EXTRA = "irisExtra";

    public static final VertexFormat FORMAT = VertexFormat.builder()
            .add(ATTRIBUTE_POSITION, POSITION_META)
            .add(ATTRIBUTE_COLOR, VertexFormatElement.COLOR)
            .add(ATTRIBUTE_EXTRA, EXTRA)
            .build();

    private LodVertexFormat() {
    }
}
