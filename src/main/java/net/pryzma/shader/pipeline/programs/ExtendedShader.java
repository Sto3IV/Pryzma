package net.pryzma.shader.pipeline.programs;

import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import com.mojang.blaze3d.shaders.Program;
import com.mojang.blaze3d.shaders.ProgramManager;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.pryzma.shader.PryzmaShaders;
import net.pryzma.shader.gl.GLDebug;
import net.pryzma.shader.gl.ShaderRenderSystem;
import net.pryzma.shader.gl.blending.AlphaTest;
import net.pryzma.shader.gl.blending.BlendModeOverride;
import net.pryzma.shader.gl.blending.BufferBlendOverride;
import net.pryzma.shader.gl.framebuffer.GlFramebuffer;
import net.pryzma.shader.gl.image.ImageHolder;
import net.pryzma.shader.gl.program.ShaderProgramTypes;
import net.pryzma.shader.gl.program.ProgramImages;
import net.pryzma.shader.gl.program.ProgramSamplers;
import net.pryzma.shader.gl.program.ProgramUniforms;
import net.pryzma.shader.gl.sampler.SamplerHolder;
import net.pryzma.shader.gl.texture.TextureType;
import net.pryzma.shader.gl.uniform.DynamicLocationalUniformHolder;
import net.pryzma.shader.mixinterface.ShaderInstanceInterface;
import net.pryzma.shader.pipeline.ShadedWorldRenderingPipeline;
import net.pryzma.shader.samplers.ShaderSamplers;
import net.pryzma.shader.uniforms.CapturedRenderingState;
import net.pryzma.shader.uniforms.custom.CustomUniforms;
import net.pryzma.shader.vertices.ImmediateState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.opengl.ARBTextureSwizzle;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.KHRDebug;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class ExtendedShader extends ShaderInstance implements ShaderInstanceInterface {
	private static final Matrix4f IDENTITY = new Matrix4f().identity();
	private static final Uniform FAKE_UNIFORM = new Uniform("", 1, 2, null);

	private final boolean intensitySwizzle;
	private final List<BufferBlendOverride> bufferBlendOverrides;
	private final boolean hasOverrides;
	private final Uniform modelViewInverse;
	private final Uniform projectionInverse;
	private final Uniform normalMatrix;
	private final CustomUniforms customUniforms;
	private final ShadedWorldRenderingPipeline parent;
	private final ProgramUniforms uniforms;
	private final ProgramSamplers samplers;
	private final ProgramImages images;
	private final GlFramebuffer writingToBeforeTranslucent;
	private final GlFramebuffer writingToAfterTranslucent;
	private final BlendModeOverride blendModeOverride;
	private final float alphaTest;
	private final boolean usesTessellation;
	private final Matrix4f tempMatrix4f = new Matrix4f();
	private final Matrix3f tempMatrix3f = new Matrix3f();
	private final float[] tempFloats = new float[16];
	private final float[] tempFloats2 = new float[9];
	private Program geometry, tessControl, tessEval;

	public ExtendedShader(ResourceProvider resourceFactory, String name, VertexFormat vertexFormat,
						  boolean usesTessellation, GlFramebuffer writingToBeforeTranslucent,
						  GlFramebuffer writingToAfterTranslucent, BlendModeOverride blendModeOverride,
						  AlphaTest alphaTest, Consumer<DynamicLocationalUniformHolder> uniformCreator,
						  BiConsumer<SamplerHolder, ImageHolder> samplerCreator, boolean isIntensity,
						  ShadedWorldRenderingPipeline parent, @Nullable List<BufferBlendOverride> bufferBlendOverrides,
						  CustomUniforms customUniforms) throws IOException {
		super(resourceFactory, name, vertexFormat);

		setupDebugNames(name);

		ProgramUniforms.Builder uniformBuilder = ProgramUniforms.builder(name, this.getId());
		ProgramSamplers.Builder samplerBuilder = ProgramSamplers.builder(this.getId(), ShaderSamplers.WORLD_RESERVED_TEXTURE_UNITS);
		ProgramImages.Builder imageBuilder = ProgramImages.builder(this.getId());

		uniformCreator.accept(uniformBuilder);
		samplerCreator.accept(samplerBuilder, imageBuilder);
		customUniforms.mapholderToPass(uniformBuilder, this);

		this.uniforms = uniformBuilder.buildUniforms();
		this.samplers = samplerBuilder.build();
		this.images = imageBuilder.build();

		this.usesTessellation = usesTessellation;
		this.writingToBeforeTranslucent = writingToBeforeTranslucent;
		this.writingToAfterTranslucent = writingToAfterTranslucent;
		this.blendModeOverride = blendModeOverride;
		this.bufferBlendOverrides = bufferBlendOverrides;
		this.hasOverrides = bufferBlendOverrides != null && !bufferBlendOverrides.isEmpty();
		this.alphaTest = alphaTest.reference();
		this.parent = parent;
		this.customUniforms = customUniforms;
		this.intensitySwizzle = isIntensity;

		this.modelViewInverse = this.getUniform("ModelViewMatInverse");
		this.projectionInverse = this.getUniform("ProjMatInverse");
		this.normalMatrix = this.getUniform("NormalMat");
	}

	private void setupDebugNames(String name) {
		GLDebug.nameObject(KHRDebug.GL_SHADER, this.getVertexProgram().getId(), name + "_vertex.vsh");
		GLDebug.nameObject(KHRDebug.GL_SHADER, this.getFragmentProgram().getId(), name + "_fragment.fsh");
		GLDebug.nameObject(KHRDebug.GL_PROGRAM, this.getId(), name);
	}

	@Override
	public void clear() {
		ProgramUniforms.clearActiveUniforms();
		ProgramSamplers.clearActiveSamplers();

		if (this.blendModeOverride != null || hasOverrides) {
			BlendModeOverride.restore();
		}

		Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
	}

	@Override
	public void apply() {
		CapturedRenderingState.INSTANCE.setCurrentAlphaTest(alphaTest);

		ProgramManager.glUseProgram(this.getId());

		setupTextures();
		updateMatrices();
		updateUniforms();
		applyBlendModes();
		bindFramebuffer();
	}

	private void setupTextures() {
		if (intensitySwizzle) {
			ShaderRenderSystem.texParameteriv(RenderSystem.getShaderTexture(0), TextureType.TEXTURE_2D.getGlType(),
				ARBTextureSwizzle.GL_TEXTURE_SWIZZLE_RGBA, new int[]{GL30C.GL_RED, GL30C.GL_RED, GL30C.GL_RED, GL30C.GL_RED});
		}

		ShaderRenderSystem.bindTextureToUnit(TextureType.TEXTURE_2D.getGlType(), ShaderSamplers.ALBEDO_TEXTURE_UNIT, RenderSystem.getShaderTexture(0));
		ShaderRenderSystem.bindTextureToUnit(TextureType.TEXTURE_2D.getGlType(), ShaderSamplers.OVERLAY_TEXTURE_UNIT, RenderSystem.getShaderTexture(1));
		ShaderRenderSystem.bindTextureToUnit(TextureType.TEXTURE_2D.getGlType(), ShaderSamplers.LIGHTMAP_TEXTURE_UNIT, RenderSystem.getShaderTexture(2));

		ImmediateState.usingTessellation = usesTessellation;
	}

	private void updateMatrices() {
		if (PROJECTION_MATRIX != null && projectionInverse != null) {
			projectionInverse.set(tempMatrix4f.set(PROJECTION_MATRIX.getFloatBuffer()).invert().get(tempFloats));
		} else if (projectionInverse != null) {
			projectionInverse.set(IDENTITY);
		}

		if (MODEL_VIEW_MATRIX != null) {
			if (modelViewInverse != null) {
				modelViewInverse.set(tempMatrix4f.set(MODEL_VIEW_MATRIX.getFloatBuffer()).invert().get(tempFloats));
			}

			if (normalMatrix != null) {
				normalMatrix.set(tempMatrix3f.set(tempMatrix4f.set(MODEL_VIEW_MATRIX.getFloatBuffer())).invert().transpose().get(tempFloats2));
			}
		}
	}

	private void updateUniforms() {
		uploadIfNotNull(projectionInverse);
		uploadIfNotNull(modelViewInverse);
		uploadIfNotNull(normalMatrix);

		// Per draw: an indexed loop, not forEach with a method reference bound to this (one allocation per call).
		for (int i = 0, n = super.uniforms.size(); i < n; i++) {
			uploadIfNotNull(super.uniforms.get(i));
		}

		samplers.update();
		uniforms.update();
		customUniforms.push(this);
		images.update();
	}

	private void applyBlendModes() {
		if (this.blendModeOverride != null) {
			this.blendModeOverride.apply();
		}

		if (hasOverrides) {
			bufferBlendOverrides.forEach(BufferBlendOverride::apply);
		}
	}

	private void bindFramebuffer() {
		if (parent.isBeforeTranslucent) {
			writingToBeforeTranslucent.bind();
		} else {
			writingToAfterTranslucent.bind();
		}
	}

	@Nullable
	@Override
	public Uniform getUniform(@NotNull String name) {
		// Prefix all uniforms with PryzmaShaders to help avoid conflicts with existing names within the shader.
		Uniform uniform = super.getUniform("iris_" + name);

		if (uniform == null && (name.equalsIgnoreCase("OverlayUV") || name.equalsIgnoreCase("LightUV"))) {
			return FAKE_UNIFORM;
		}
		return uniform;
	}

	private void uploadIfNotNull(Uniform uniform) {
		if (uniform != null) {
			uniform.upload();
		}
	}

	@Override
	public void attachToProgram() {
		super.attachToProgram();
		attachExtraShaders();
	}

	private void attachExtraShaders() {
		if (this.geometry != null) {
			this.geometry.attachToShader(this);
		}
		if (this.tessControl != null) {
			this.tessControl.attachToShader(this);
		}
		if (this.tessEval != null) {
			this.tessEval.attachToShader(this);
		}
	}

	@Override
	public void pryzma$createExtraShaders(ResourceProvider factory, String name) {
		createGeometryShader(factory, name);
		createTessControlShader(factory, name);
		createTessEvalShader(factory, name);
	}

	@Override
	public void setShouldSkip(MethodHandle s) {

	}

	private void createGeometryShader(ResourceProvider factory, String name) {
		createShader(factory, name, "_geometry.gsh", ShaderProgramTypes.GEOMETRY,
			program -> this.geometry = program);
	}

	private void createTessControlShader(ResourceProvider factory, String name) {
		createShader(factory, name, "_tessControl.tcs", ShaderProgramTypes.TESS_CONTROL,
			program -> this.tessControl = program);
	}

	private void createTessEvalShader(ResourceProvider factory, String name) {
		createShader(factory, name, "_tessEval.tes", ShaderProgramTypes.TESS_EVAL,
			program -> this.tessEval = program);
	}

	private void createShader(ResourceProvider factory, String name, String suffix,
							  Program.Type type, Consumer<Program> programSetter) {
		factory.getResource(ResourceLocation.fromNamespaceAndPath("minecraft", name + suffix)).ifPresent(resource -> {
			try {
				Program program = Program.compileShader(type, name, resource.open(), resource.sourcePackId(),
					new GlslPreprocessor() {
						@Nullable
						@Override
						public String applyImport(boolean bl, String string) {
							return null;
						}
					});
				GLDebug.nameObject(KHRDebug.GL_SHADER, program.getId(), name + suffix);
				programSetter.accept(program);
			} catch (IOException e) {
				PryzmaShaders.logger.error("Failed to create shader program", e);
			}
		});
	}

	public Program getGeometry() {
		return this.geometry;
	}

	public Program getTessControl() {
		return this.tessControl;
	}

	public Program getTessEval() {
		return this.tessEval;
	}

	public boolean hasActiveImages() {
		return images.getActiveImages() > 0;
	}
}
