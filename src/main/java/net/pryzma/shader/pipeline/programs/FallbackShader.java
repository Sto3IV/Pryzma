package net.pryzma.shader.pipeline.programs;

import com.mojang.renderpearl.api.vertex.VertexFormat;
import com.mojang.renderpearl.backend.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.pryzma.shader.gl.ShaderRenderSystem;
import net.pryzma.shader.gl.blending.BlendModeOverride;
import net.pryzma.shader.gl.framebuffer.GlFramebuffer;
import net.pryzma.shader.gl.texture.TextureType;
import net.pryzma.shader.pbr.TextureTracker;
import net.pryzma.shader.pipeline.ShadedWorldRenderingPipeline;
import org.lwjgl.opengl.GL30C;
import net.pryzma.shader.samplers.ShaderSamplers;
import net.pryzma.shader.uniforms.CapturedRenderingState;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;

public class FallbackShader extends ShaderInstance {
	private final ShadedWorldRenderingPipeline parent;
	private final BlendModeOverride blendModeOverride;
	private final GlFramebuffer writingToBeforeTranslucent;
	private final GlFramebuffer writingToAfterTranslucent;

	@Nullable
	private final Uniform FOG_DENSITY;

	@Nullable
	private final Uniform FOG_IS_EXP2;
	private final int gtexture;
	private final int overlay;
	private final int lightmap;

	public FallbackShader(ResourceProvider resourceFactory, String string, VertexFormat vertexFormat,
						  GlFramebuffer writingToBeforeTranslucent, GlFramebuffer writingToAfterTranslucent,
						  BlendModeOverride blendModeOverride, float alphaValue, ShadedWorldRenderingPipeline parent) throws IOException {
		super(resourceFactory, string, vertexFormat);

		this.parent = parent;
		this.blendModeOverride = blendModeOverride;
		this.writingToBeforeTranslucent = writingToBeforeTranslucent;
		this.writingToAfterTranslucent = writingToAfterTranslucent;

		this.FOG_DENSITY = this.getUniform("FogDensity");
		this.FOG_IS_EXP2 = this.getUniform("FogIsExp2");

		this.gtexture = GlStateManager._glGetUniformLocation(getId(), "gtexture");
		this.overlay = GlStateManager._glGetUniformLocation(getId(), "overlay");
		this.lightmap = GlStateManager._glGetUniformLocation(getId(), "lightmap");


		Uniform ALPHA_TEST_VALUE = this.getUniform("AlphaTestValue");

		if (ALPHA_TEST_VALUE != null) {
			ALPHA_TEST_VALUE.set(alphaValue);
		}
	}

	@Override
	public void clear() {
		super.clear();

		if (this.blendModeOverride != null) {
			BlendModeOverride.restore();
		}

		GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, 0);
	}

	@Override
	public void apply() {
		if (FOG_DENSITY != null && FOG_IS_EXP2 != null) {
			float fogDensity = CapturedRenderingState.INSTANCE.getFogDensity();

			if (fogDensity >= 0.0) {
				FOG_DENSITY.set(fogDensity);
				FOG_IS_EXP2.set(1);
			} else {
				FOG_DENSITY.set(0.0F);
				FOG_IS_EXP2.set(0);
			}
		}

		ShaderRenderSystem.bindTextureToUnit(TextureType.TEXTURE_2D.getGlType(), ShaderSamplers.ALBEDO_TEXTURE_UNIT, TextureTracker.INSTANCE.getShaderTexture(0));
		ShaderRenderSystem.bindTextureToUnit(TextureType.TEXTURE_2D.getGlType(), ShaderSamplers.OVERLAY_TEXTURE_UNIT, TextureTracker.INSTANCE.getShaderTexture(1));
		ShaderRenderSystem.bindTextureToUnit(TextureType.TEXTURE_2D.getGlType(), ShaderSamplers.LIGHTMAP_TEXTURE_UNIT, TextureTracker.INSTANCE.getShaderTexture(2));

		GlStateManager._glUseProgram(this.getId());

		List<Uniform> uniformList = super.uniforms;
		for (Uniform uniform : uniformList) {
			uploadIfNotNull(uniform);
		}

		GlStateManager._glUniform1i(gtexture, 0);
		GlStateManager._glUniform1i(overlay, 1);
		GlStateManager._glUniform1i(lightmap, 2);

		if (this.blendModeOverride != null) {
			this.blendModeOverride.apply();
		}

		if (parent.isBeforeTranslucent) {
			writingToBeforeTranslucent.bind();
		} else {
			writingToAfterTranslucent.bind();
		}
	}

	private void uploadIfNotNull(Uniform uniform) {
		if (uniform != null) {
			uniform.upload();
		}
	}
}
