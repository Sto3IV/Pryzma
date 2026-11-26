package net.pryzma.farlands.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.pipeline.RenderPipeline.Builder;
import com.mojang.renderpearl.api.pipeline.RenderPipeline.Snippet;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;

public final class FarlandsPipelines {
   public static final BindGroupLayout LOD_DATA = BindGroupLayout.builder()
      .withUniform("FarlandsInfo", UniformType.UNIFORM_BUFFER)
      .withUniform("FarlandsTiles", UniformType.TEXEL_BUFFER, GpuFormat.RGBA32_FLOAT)
      .withUniform("FarlandsSprites", UniformType.TEXEL_BUFFER, GpuFormat.RGBA32_FLOAT)
      .withUniform("FarlandsQuads", UniformType.TEXEL_BUFFER, GpuFormat.RGBA32_UINT)
      .build();
   public static final BindGroupLayout LOD_COMPACT = BindGroupLayout.builder()
      .withUniform("FarlandsInfo", UniformType.UNIFORM_BUFFER)
      .withUniform("FarlandsSprites", UniformType.TEXEL_BUFFER, GpuFormat.RGBA32_FLOAT)
      .withUniform("FarlandsCompact", UniformType.TEXEL_BUFFER, GpuFormat.RG32_UINT)
      .build();
   public static final VertexFormat COMPACT_TILE = VertexFormat.builder(1)
      .addAttribute("TileOrigin", GpuFormat.RGBA32_FLOAT)
      .addAttribute("TileExtra", GpuFormat.RGBA32_UINT)
      .build();
   public static final BindGroupLayout LOD_WATER = BindGroupLayout.builder()
      .withUniform("FarlandsWater", UniformType.TEXEL_BUFFER, GpuFormat.RGBA32_UINT)
      .withUniform("FarlandsWaterSprites", UniformType.TEXEL_BUFFER, GpuFormat.RGBA32_FLOAT)
      .build();
   public static final BindGroupLayout LOD_MASK = BindGroupLayout.builder().withUniform("FarlandsMask", UniformType.TEXEL_BUFFER, GpuFormat.R32_UINT).build();
   public static final BindGroupLayout LOD_INFO = BindGroupLayout.builder().withUniform("FarlandsInfo", UniformType.UNIFORM_BUFFER).build();
   public static final RenderPipeline WATER = water("pipeline/farlands_water", false);
   public static final RenderPipeline WATER_MULTIDRAW = water("pipeline/farlands_water_multidraw", true);
   public static final RenderPipeline TERRAIN = terrain("pipeline/farlands_terrain", false, false);
   public static final RenderPipeline TERRAIN_CLIP = terrain("pipeline/farlands_terrain_clip", true, false);
   public static final RenderPipeline FLUID = terrain("pipeline/farlands_fluid", false, true);
   public static final RenderPipeline FLUID_CLIP = terrain("pipeline/farlands_fluid_clip", true, true);
   public static final RenderPipeline TERRAIN_COMPACT = compact("pipeline/farlands_terrain_compact", false);
   public static final RenderPipeline TERRAIN_COMPACT_CLIP = compact("pipeline/farlands_terrain_compact_clip", true);

   private static RenderPipeline compact(String location, boolean clip) {
      Builder builder = RenderPipeline.builder(new Snippet[0])
         .withLocation(Identifier.fromNamespaceAndPath("pryzma", location))
         .withVertexShader(Identifier.fromNamespaceAndPath("pryzma", "core/farlands"))
         .withFragmentShader(Identifier.fromNamespaceAndPath("pryzma", "core/farlands"))
         .withBindGroupLayout(BindGroupLayouts.PROJECTION)
         .withBindGroupLayout(BindGroupLayouts.FOG)
         .withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER2)
         .withBindGroupLayout(LOD_COMPACT)
         .withVertexBinding(0, COMPACT_TILE)
         .withShaderDefine("FARLANDS_COMPACT")
         .withPrimitiveTopology(PrimitiveTopology.QUADS)
         .withDepthStencilState(DepthStencilState.DEFAULT)
         .withColorTargetState(ColorTargetState.DEFAULT)
         .withCull(true);
      if (clip) {
         builder.withShaderDefine("FARLANDS_CLIP").withBindGroupLayout(LOD_MASK);
      }

      return builder.build();
   }

   private static RenderPipeline terrain(String location, boolean clip, boolean fluid) {
      Builder builder = RenderPipeline.builder(new Snippet[0])
         .withLocation(Identifier.fromNamespaceAndPath("pryzma", location))
         .withVertexShader(Identifier.fromNamespaceAndPath("pryzma", "core/farlands"))
         .withFragmentShader(Identifier.fromNamespaceAndPath("pryzma", "core/farlands"))
         .withBindGroupLayout(BindGroupLayouts.PROJECTION)
         .withBindGroupLayout(BindGroupLayouts.FOG)
         .withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER2)
         .withBindGroupLayout(LOD_DATA)
         .withPrimitiveTopology(PrimitiveTopology.QUADS)
         .withDepthStencilState(DepthStencilState.DEFAULT)
         .withColorTargetState(ColorTargetState.DEFAULT)
         .withCull(true);
      if (clip) {
         builder.withShaderDefine("FARLANDS_CLIP").withBindGroupLayout(LOD_MASK);
      }

      if (fluid) {
         builder.withShaderDefine("FARLANDS_FLUID")
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false));
      }

      return builder.build();
   }

   private static RenderPipeline water(String location, boolean multidraw) {
      Builder builder = RenderPipeline.builder(new Snippet[0])
         .withLocation(Identifier.fromNamespaceAndPath("pryzma", location))
         .withVertexShader(Identifier.fromNamespaceAndPath("pryzma", "core/farlands_water"))
         .withFragmentShader(Identifier.fromNamespaceAndPath("pryzma", "core/farlands_water"))
         .withBindGroupLayout(BindGroupLayouts.GLOBALS)
         .withBindGroupLayout(BindGroupLayouts.FOG)
         .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
         .withBindGroupLayout(BindGroupLayouts.SAMPLER2)
         .withBindGroupLayout(BindGroupLayouts.PROJECTION)
         .withBindGroupLayout(BindGroupLayouts.TERRAIN_INFO)
         .withBindGroupLayout(LOD_INFO)
         .withBindGroupLayout(LOD_WATER)
         .withPrimitiveTopology(PrimitiveTopology.QUADS)
         .withDepthStencilState(DepthStencilState.DEFAULT)
         .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
         .withShaderDefine("ALPHA_CUTOUT", 0.1F);
      if (multidraw) {
         builder.withVertexBinding(0, DefaultVertexFormat.CHUNK_DATA_INSTANCED).withShaderDefine("MULTIDRAW_TERRAIN");
      } else {
         builder.withBindGroupLayout(BindGroupLayouts.CHUNK_SECTION);
      }

      return builder.build();
   }

   private FarlandsPipelines() {
   }
}
