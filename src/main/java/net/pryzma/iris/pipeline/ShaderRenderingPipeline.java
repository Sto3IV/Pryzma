package net.pryzma.iris.pipeline;

import net.pryzma.iris.pipeline.programs.ShaderMap;
import net.pryzma.iris.uniforms.FrameUpdateNotifier;

public interface ShaderRenderingPipeline extends WorldRenderingPipeline {
	ShaderMap getShaderMap();

	FrameUpdateNotifier getFrameUpdateNotifier();

	boolean shouldOverrideShaders();
}
