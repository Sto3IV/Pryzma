package net.pryzma.farlands.client.iris;

import com.google.common.primitives.Ints;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.pryzma.shader.gl.blending.BlendModeOverride;
import net.pryzma.shader.gl.blending.BufferBlendOverride;
import net.pryzma.shader.gl.program.ProgramImages;
import net.pryzma.shader.gl.program.ProgramSamplers;
import net.pryzma.shader.gl.program.ProgramUniforms;
import net.pryzma.shader.gl.program.ProgramUniforms.Builder;
import net.pryzma.shader.gl.shader.GlShader;
import net.pryzma.shader.gl.shader.ShaderType;
import net.pryzma.shader.gl.state.FogMode;
import net.pryzma.shader.pipeline.ShadedWorldRenderingPipeline;
import net.pryzma.shader.pipeline.transform.PatchShaderType;
import net.pryzma.shader.pipeline.transform.TransformPatcher;
import net.pryzma.shader.samplers.ShaderSamplers;
import net.pryzma.shader.shaderpack.programs.ProgramSource;
import net.pryzma.shader.shaderpack.texture.TextureStage;
import net.pryzma.shader.uniforms.CommonUniforms;
import net.pryzma.shader.uniforms.builtin.BuiltinReplacementUniforms;
import net.pryzma.shader.uniforms.custom.CustomUniforms;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL43C;
import org.lwjgl.system.MemoryStack;

final class IrisLodProgram {
   private final int id = GL43C.glCreateProgram();
   private final ProgramUniforms uniforms;
   private final ProgramSamplers samplers;
   private final ProgramImages images;
   private final CustomUniforms customUniforms;
   private final @Nullable BlendModeOverride blend;
   private final BufferBlendOverride[] bufferBlends;
   private final int modelOffset;
   private final int modelView;
   private final int modelViewInverse;
   private final int projection;
   private final int projectionInverse;
   private final int normalMatrix;
   private final int microOffset;
   private final int worldYOffset;
   private final int clipDistance;

   private IrisLodProgram(String name, boolean translucent, ProgramSource source, Map<PatchShaderType, String> code, ShadedWorldRenderingPipeline pipeline) {
      GL20C.glBindAttribLocation(this.id, 0, "vPosition");
      GL20C.glBindAttribLocation(this.id, 1, "iris_color");
      GL20C.glBindAttribLocation(this.id, 2, "irisExtra");
      List<GlShader> shaders = new ArrayList<>();
      shaders.add(new GlShader(ShaderType.VERTEX, name + ".vsh", code.get(PatchShaderType.VERTEX)));
      if (code.get(PatchShaderType.TESS_CONTROL) != null) {
         shaders.add(new GlShader(ShaderType.TESSELATION_CONTROL, name + ".tcs", code.get(PatchShaderType.TESS_CONTROL)));
      }

      if (code.get(PatchShaderType.TESS_EVAL) != null) {
         shaders.add(new GlShader(ShaderType.TESSELATION_EVAL, name + ".tes", code.get(PatchShaderType.TESS_EVAL)));
      }

      if (code.get(PatchShaderType.GEOMETRY) != null) {
         shaders.add(new GlShader(ShaderType.GEOMETRY, name + ".gsh", code.get(PatchShaderType.GEOMETRY)));
      }

      shaders.add(new GlShader(ShaderType.FRAGMENT, name + ".fsh", code.get(PatchShaderType.FRAGMENT)));

      for (GlShader shader : shaders) {
         GL43C.glAttachShader(this.id, shader.getHandle());
      }

      GL20C.glLinkProgram(this.id);
      boolean linked = GL20C.glGetProgrami(this.id, 35714) == 1;
      String log = linked ? "" : GL20C.glGetProgramInfoLog(this.id);

      for (GlShader shader : shaders) {
         shader.destroy();
      }

      if (!linked) {
         GL43C.glDeleteProgram(this.id);
         throw new IllegalStateException("Distant terrain shader program failed to link: " + log);
      }

      GL20C.glUseProgram(this.id);
      this.blend = (BlendModeOverride)source.getDirectives().getBlendModeOverride().orElse(null);
      List<BufferBlendOverride> overrides = new ArrayList<>();
      source.getDirectives().getBufferBlendOverrides().forEach(information -> {
         int index = Ints.indexOf(source.getDirectives().getDrawBuffers(), information.index());
         if (index > -1) {
            overrides.add(new BufferBlendOverride(index, information.blendMode()));
         }
      });
      this.bufferBlends = overrides.toArray(BufferBlendOverride[]::new);
      this.customUniforms = pipeline.getCustomUniforms();
      Builder uniformBuilder = ProgramUniforms.builder(name, this.id);
      ProgramSamplers.Builder samplerBuilder = ProgramSamplers.builder(this.id, ShaderSamplers.WORLD_RESERVED_TEXTURE_UNITS);
      ProgramImages.Builder imageBuilder = ProgramImages.builder(this.id);
      CommonUniforms.addDynamicUniforms(uniformBuilder, FogMode.PER_VERTEX);
      this.customUniforms.assignTo(uniformBuilder);
      BuiltinReplacementUniforms.addBuiltinReplacementUniforms(uniformBuilder);
      pipeline.addGbufferOrShadowSamplers(
         samplerBuilder, imageBuilder, () -> translucent ? pipeline.getFlippedAfterTranslucent() : pipeline.getFlippedAfterPrepare(), false, false, true, false
      );
      this.customUniforms.mapholderToPass(uniformBuilder, this);
      this.uniforms = uniformBuilder.buildUniforms();
      this.samplers = samplerBuilder.build();
      this.images = imageBuilder.build();
      this.modelOffset = GL20C.glGetUniformLocation(this.id, "modelOffset");
      this.modelView = GL20C.glGetUniformLocation(this.id, "iris_ModelViewMatrix");
      this.modelViewInverse = GL20C.glGetUniformLocation(this.id, "iris_ModelViewMatrixInverse");
      this.projection = GL20C.glGetUniformLocation(this.id, "iris_ProjectionMatrix");
      this.projectionInverse = GL20C.glGetUniformLocation(this.id, "iris_ProjectionMatrixInverse");
      this.normalMatrix = GL20C.glGetUniformLocation(this.id, "iris_NormalMatrix");
      this.microOffset = GL20C.glGetUniformLocation(this.id, "mircoOffset");
      this.worldYOffset = GL20C.glGetUniformLocation(this.id, "worldYOffset");
      this.clipDistance = GL20C.glGetUniformLocation(this.id, "clipDistance");
   }

   static IrisLodProgram create(String name, boolean translucent, ProgramSource source, ShadedWorldRenderingPipeline pipeline) {
      Map<PatchShaderType, String> code = TransformPatcher.patchDHTerrain(
         name,
         (String)source.getVertexSource().orElseThrow(),
         (String)source.getTessControlSource().orElse(null),
         (String)source.getTessEvalSource().orElse(null),
         (String)source.getGeometrySource().orElse(null),
         (String)source.getFragmentSource().orElseThrow(),
         pipeline.getTextureMap()
      );
      return new IrisLodProgram(name, translucent, source, code, pipeline);
   }

   void bind() {
      GL20C.glUseProgram(this.id);
      if (this.blend != null) {
         this.blend.apply();
      }

      for (BufferBlendOverride override : this.bufferBlends) {
         override.apply();
      }
   }

   void unbind() {
      GL20C.glUseProgram(0);
      ProgramUniforms.clearActiveUniforms();
      ProgramSamplers.clearActiveSamplers();
      BlendModeOverride.restore();
   }

   void fill(Matrix4fc projection, Matrix4fc modelView, float nearPlane) {
      matrix(this.modelView, modelView);
      matrix(this.modelViewInverse, modelView.invert(new Matrix4f()));
      matrix(this.projection, projection);
      matrix(this.projectionInverse, projection.invert(new Matrix4f()));
      if (this.normalMatrix != -1) {
         Matrix3f normal = new Matrix4f(modelView).invert().transpose3x3(new Matrix3f());
         MemoryStack stack = MemoryStack.stackPush();

         try {
            FloatBuffer buffer = stack.callocFloat(9);
            normal.get(buffer);
            GL20C.glUniformMatrix3fv(this.normalMatrix, false, buffer);
         } catch (Throwable var9) {
            if (stack != null) {
               try {
                  stack.close();
               } catch (Throwable var8) {
                  var9.addSuppressed(var8);
               }
            }

            throw var9;
         }

         if (stack != null) {
            stack.close();
         }
      }

      if (this.microOffset != -1) {
         GL20C.glUniform1f(this.microOffset, 0.01F);
      }

      if (this.worldYOffset != -1) {
         GL20C.glUniform1f(this.worldYOffset, -2048.0F);
      }

      if (this.clipDistance != -1) {
         GL20C.glUniform1f(this.clipDistance, nearPlane);
      }

      this.samplers.update();
      this.uniforms.update();
      this.customUniforms.push(this);
      this.images.update();
   }

   void modelOffset(float x, float y, float z) {
      if (this.modelOffset != -1) {
         GL20C.glUniform3f(this.modelOffset, x, y, z);
      }
   }

   private static void matrix(int location, Matrix4fc matrix) {
      if (location != -1) {
         MemoryStack stack = MemoryStack.stackPush();

         try {
            FloatBuffer buffer = stack.callocFloat(16);
            matrix.get(buffer);
            GL20C.glUniformMatrix4fv(location, false, buffer);
         } catch (Throwable var6) {
            if (stack != null) {
               try {
                  stack.close();
               } catch (Throwable var5) {
                  var6.addSuppressed(var5);
               }
            }

            throw var6;
         }

         if (stack != null) {
            stack.close();
         }
      }
   }

   void free() {
      GL43C.glDeleteProgram(this.id);
   }
}
