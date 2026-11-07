package net.pryzma.lod;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import com.mojang.renderpearl.api.vertex.VertexFormatElement;

/**
 * The GL side of the {@link NativeLodMesher} vertex layout, which is the Distant Horizons vertex ABI that shader packs'
 * {@code dh_terrain} and {@code dh_water} programs are written against:
 * <pre>
 * location 0  vPosition  uvec4  x, y, z (unsigned shorts) and meta (light in the low byte)   glVertexAttribIPointer
 * location 1  iris_color vec4   r, g, b, a (normalized unsigned bytes)
 * location 2  irisExtra  uvec4  material, normal index, 0, 0 (unsigned bytes)              glVertexAttribIPointer
 * </pre>
 * The native {@code pryzma:lod_terrain} shader reads the same attributes, so one vertex buffer and one VAO serve both
 * the vanilla pass and a shader pack's G-buffer pass.
 */
public final class LodVertexFormat {
    public static final String ATTRIBUTE_POSITION = "vPosition";
    public static final String ATTRIBUTE_COLOR = "iris_color";
    public static final String ATTRIBUTE_EXTRA = "irisExtra";

    public static final VertexFormat FORMAT = VertexFormat.builder(0)
            .addAttribute(ATTRIBUTE_POSITION, GpuFormat.RGBA16_UINT)
            .addAttribute(ATTRIBUTE_COLOR, GpuFormat.RGBA8_UNORM)
            .addAttribute(ATTRIBUTE_EXTRA, GpuFormat.RGBA8_UINT)
            .build();

    public static final VertexFormatElement POSITION_META = FORMAT.getElement(ATTRIBUTE_POSITION);
    public static final VertexFormatElement COLOR = FORMAT.getElement(ATTRIBUTE_COLOR);
    public static final VertexFormatElement EXTRA = FORMAT.getElement(ATTRIBUTE_EXTRA);

    private LodVertexFormat() {
    }
}
