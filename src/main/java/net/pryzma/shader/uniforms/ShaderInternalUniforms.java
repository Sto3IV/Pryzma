package net.pryzma.shader.uniforms;

import com.mojang.blaze3d.systems.RenderSystem;
import net.pryzma.shader.gl.state.FogMode;
import net.pryzma.shader.gl.uniform.DynamicUniformHolder;
import org.joml.Vector4f;

import static net.pryzma.shader.gl.uniform.UniformUpdateFrequency.PER_FRAME;

/**
 * Internal PryzmaShaders uniforms that are not directly accessible by shaders.
 */
public class ShaderInternalUniforms {
	private ShaderInternalUniforms() {
		// no construction
	}

	public static void addFogUniforms(DynamicUniformHolder uniforms, FogMode fogMode) {
		uniforms
			.uniform4f(PER_FRAME, "iris_FogColor", () -> {
				org.joml.Vector3d fogColor = CapturedRenderingState.INSTANCE.getFogColor();
				return new Vector4f((float) fogColor.x, (float) fogColor.y, (float) fogColor.z, 1.0F);
			});

		uniforms.uniform1f(PER_FRAME, "iris_FogStart", CapturedRenderingState.INSTANCE::getFogStart)
			.uniform1f(PER_FRAME, "iris_FogEnd", CapturedRenderingState.INSTANCE::getFogEnd);

		uniforms.uniform1f("iris_FogDensity", () -> {
			// ensure that the minimum value is 0.0
			return Math.max(0.0F, CapturedRenderingState.INSTANCE.getFogDensity());
		}, notifier -> {
		});

		uniforms.uniform1f("iris_currentAlphaTest", CapturedRenderingState.INSTANCE::getCurrentAlphaTest, notifier -> {
		});

		// Optifine compatibility
		uniforms.uniform1f("alphaTestRef", CapturedRenderingState.INSTANCE::getCurrentAlphaTest, notifier -> {
		});
	}
}
