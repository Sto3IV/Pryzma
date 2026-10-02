package net.pryzma.shader.apiimpl;

import net.pryzma.shader.PryzmaShaders;
import net.pryzma.shader.api.v0.ShaderApi;
import net.pryzma.shader.api.v0.ShaderApiConfig;
import net.pryzma.shader.api.v0.ShaderTextVertexSink;
import net.pryzma.shader.gui.screen.ShaderPackScreen;
import net.pryzma.shader.pipeline.VanillaRenderingPipeline;
import net.pryzma.shader.pipeline.WorldRenderingPipeline;
import net.pryzma.shader.shadows.ShadowRenderingState;
import net.pryzma.shader.vertices.ShaderTextVertexSinkImpl;
import net.minecraft.client.gui.screens.Screen;

import java.nio.ByteBuffer;
import java.util.function.IntFunction;

public class ShaderApiV0Impl implements ShaderApi {
	public static final ShaderApiV0Impl INSTANCE = new ShaderApiV0Impl();
	private static final ShaderApiV0ConfigImpl CONFIG = new ShaderApiV0ConfigImpl();

	@Override
	public int getMinorApiRevision() {
		return 2;
	}

	@Override
	public boolean isShaderPackInUse() {
		WorldRenderingPipeline pipeline = PryzmaShaders.getPipelineManager().getPipelineNullable();

		if (pipeline == null) {
			return false;
		}

		return !(pipeline instanceof VanillaRenderingPipeline);
	}

	@Override
	public boolean isRenderingShadowPass() {
		return ShadowRenderingState.areShadowsCurrentlyBeingRendered();
	}

	@Override
	public Object openMainShaderScreenObj(Object parent) {
		return new ShaderPackScreen((Screen) parent);
	}

	@Override
	public Object openMainIrisScreenObj(Object parent) {
		return openMainShaderScreenObj(parent);
	}

	@Override
	public String getMainScreenLanguageKey() {
		return "options.pryzma.shaderPackSelection";
	}

	@Override
	public ShaderApiConfig getConfig() {
		return CONFIG;
	}

	@Override
	public ShaderTextVertexSink createTextVertexSink(int maxQuadCount, IntFunction<ByteBuffer> bufferProvider) {
		return new ShaderTextVertexSinkImpl(maxQuadCount, bufferProvider);
	}

	@Override
	public float getSunPathRotation() {
		WorldRenderingPipeline pipeline = PryzmaShaders.getPipelineManager().getPipelineNullable();

		if (pipeline == null) {
			return 0;
		}

		return pipeline.getSunPathRotation();
	}
}
