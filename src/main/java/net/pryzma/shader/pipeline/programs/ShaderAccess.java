package net.pryzma.shader.pipeline.programs;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.pryzma.shader.PryzmaShaders;
import net.pryzma.shader.pipeline.ShaderRenderingPipeline;
import net.pryzma.shader.pipeline.WorldRenderingPipeline;
import net.pryzma.shader.shadows.ShadowRenderingState;

public class ShaderAccess {
	public static final VertexFormat IE_FORMAT = VertexFormat.builder(0)
		.addAttribute("Position", GpuFormat.RGB32_FLOAT)
		.addAttribute("Color", GpuFormat.RGBA8_UNORM)
		.addAttribute("UV0", GpuFormat.RG32_FLOAT)
		.addAttribute("Normal", GpuFormat.RGB32_FLOAT)
		.build();

	public static ShaderInstance getParticleTranslucentShader() {
		WorldRenderingPipeline pipeline = PryzmaShaders.getPipelineManager().getPipelineNullable();

		if (pipeline instanceof ShaderRenderingPipeline) {
			return ((ShaderRenderingPipeline) pipeline).getShaderMap().getShader(ShaderKey.PARTICLES_TRANS);
		}

		return null;
	}

	public static ShaderInstance getIEVBOShader() {
		WorldRenderingPipeline pipeline = PryzmaShaders.getPipelineManager().getPipelineNullable();

		if (pipeline instanceof ShaderRenderingPipeline) {
			return ((ShaderRenderingPipeline) pipeline).getShaderMap().getShader(ShadowRenderingState.areShadowsCurrentlyBeingRendered() ? ShaderKey.IE_COMPAT_SHADOW : ShaderKey.IE_COMPAT);
		}

		return null;
	}

	public static ShaderInstance getMekanismFlameShader() {
		WorldRenderingPipeline pipeline = PryzmaShaders.getPipelineManager().getPipelineNullable();

		if (pipeline instanceof ShaderRenderingPipeline) {
			return ((ShaderRenderingPipeline) pipeline).getShaderMap().getShader(ShadowRenderingState.areShadowsCurrentlyBeingRendered() ? ShaderKey.MEKANISM_FLAME_SHADOW : ShaderKey.MEKANISM_FLAME);
		}

		return null;
	}

	public static ShaderInstance getMekasuitShader() {
		WorldRenderingPipeline pipeline = PryzmaShaders.getPipelineManager().getPipelineNullable();

		if (pipeline instanceof ShaderRenderingPipeline) {
			return ((ShaderRenderingPipeline) pipeline).getShaderMap().getShader(ShadowRenderingState.areShadowsCurrentlyBeingRendered() ? ShaderKey.SHADOW_ENTITIES_CUTOUT : ShaderKey.ENTITIES_TRANSLUCENT);
		}

		return null;
	}

	public static ShaderInstance getSPSShader() {
		WorldRenderingPipeline pipeline = PryzmaShaders.getPipelineManager().getPipelineNullable();

		if (pipeline instanceof ShaderRenderingPipeline) {
			return ((ShaderRenderingPipeline) pipeline).getShaderMap().getShader(ShadowRenderingState.areShadowsCurrentlyBeingRendered() ? ShaderKey.SHADOW_ENTITIES_CUTOUT : ShaderKey.SPS);
		}

		return null;
	}
}
