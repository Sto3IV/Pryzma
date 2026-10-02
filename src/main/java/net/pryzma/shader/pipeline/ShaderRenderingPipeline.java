package net.pryzma.shader.pipeline;

import net.pryzma.shader.pipeline.programs.ShaderMap;
import net.pryzma.shader.uniforms.FrameUpdateNotifier;

public interface ShaderRenderingPipeline extends WorldRenderingPipeline {
	ShaderMap getShaderMap();

	FrameUpdateNotifier getFrameUpdateNotifier();

	boolean shouldOverrideShaders();
}
