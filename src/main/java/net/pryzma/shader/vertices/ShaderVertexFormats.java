package net.pryzma.shader.vertices;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import com.mojang.renderpearl.api.vertex.VertexFormatElement;

public class ShaderVertexFormats {
	public static final VertexFormatElement ENTITY_ELEMENT;
	public static final VertexFormatElement ENTITY_ID_ELEMENT;
	public static final VertexFormatElement MID_TEXTURE_ELEMENT;
	public static final VertexFormatElement TANGENT_ELEMENT;
	public static final VertexFormatElement MID_BLOCK_ELEMENT;

	public static final VertexFormat TERRAIN;
	public static final VertexFormat ENTITY;
	public static final VertexFormat GLYPH;
	public static final VertexFormat CLOUDS;

	static {
		TERRAIN = VertexFormat.builder(0)
			.addAttribute("Position", GpuFormat.RGB32_FLOAT)
			.addAttribute("Color", GpuFormat.RGBA8_UNORM)
			.addAttribute("UV0", GpuFormat.RG32_FLOAT)
			.addAttribute("UV2", GpuFormat.RG16_SINT)
			.addAttribute("Normal", GpuFormat.RGBA8_SNORM)
			.addAttribute("mc_Entity", GpuFormat.RG16_SINT)
			.addAttribute("mc_midTexCoord", GpuFormat.RG32_FLOAT)
			.addAttribute("at_tangent", GpuFormat.RGBA8_SNORM)
			.addAttribute("at_midBlock", GpuFormat.RGBA8_SNORM)
			.build();

		ENTITY = VertexFormat.builder(0)
			.addAttribute("Position", GpuFormat.RGB32_FLOAT)
			.addAttribute("Color", GpuFormat.RGBA8_UNORM)
			.addAttribute("UV0", GpuFormat.RG32_FLOAT)
			.addAttribute("UV1", GpuFormat.RG16_SINT)
			.addAttribute("UV2", GpuFormat.RG16_SINT)
			.addAttribute("Normal", GpuFormat.RGBA8_SNORM)
			.addAttribute("iris_Entity", 8, GpuFormat.RGB16_UINT)
			.addAttribute("mc_midTexCoord", GpuFormat.RG32_FLOAT)
			.addAttribute("at_tangent", GpuFormat.RGBA8_SNORM)
			.build();

		GLYPH = VertexFormat.builder(0)
			.addAttribute("Position", GpuFormat.RGB32_FLOAT)
			.addAttribute("Color", GpuFormat.RGBA8_UNORM)
			.addAttribute("UV0", GpuFormat.RG32_FLOAT)
			.addAttribute("UV2", GpuFormat.RG16_SINT)
			.addAttribute("Normal", GpuFormat.RGBA8_SNORM)
			.addAttribute("iris_Entity", 8, GpuFormat.RGB16_UINT)
			.addAttribute("mc_midTexCoord", GpuFormat.RG32_FLOAT)
			.addAttribute("at_tangent", GpuFormat.RGBA8_SNORM)
			.build();

		CLOUDS = VertexFormat.builder(0)
			.addAttribute("Position", GpuFormat.RGB32_FLOAT)
			.addAttribute("Color", GpuFormat.RGBA8_UNORM)
			.addAttribute("Normal", GpuFormat.RGBA8_SNORM)
			.build();

		ENTITY_ELEMENT = TERRAIN.getElement("mc_Entity");
		ENTITY_ID_ELEMENT = ENTITY.getElement("iris_Entity");
		MID_TEXTURE_ELEMENT = TERRAIN.getElement("mc_midTexCoord");
		TANGENT_ELEMENT = TERRAIN.getElement("at_tangent");
		MID_BLOCK_ELEMENT = TERRAIN.getElement("at_midBlock");
	}
}
