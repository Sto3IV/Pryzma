package net.pryzma.farlands.client.iris;

import com.mojang.renderpearl.backend.opengl.GlStateManager;
import net.pryzma.farlands.Farlands;
import net.pryzma.farlands.client.ShaderPath;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Optional;
import net.pryzma.shader.PryzmaShaders;
import net.pryzma.shader.api.v0.ShaderApi;
import net.pryzma.shader.gl.framebuffer.GlFramebuffer;
import net.pryzma.shader.gl.texture.DepthBufferFormat;
import net.pryzma.shader.pipeline.ShadedWorldRenderingPipeline;
import net.pryzma.shader.shaderpack.programs.ProgramSource;
import net.pryzma.shader.targets.DepthTexture;
import net.pryzma.shader.uniforms.CapturedRenderingState;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL14C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.opengl.GL43C;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

public final class IrisLodPath implements ShaderPath {
   private static final float LOD_NEAR_MAX = 16.0F;
   private static final float LOD_NEAR_SHARE = 0.2F;
   private @Nullable ShadedWorldRenderingPipeline pipeline;
   private @Nullable IrisLodProgram solidProgram;
   private @Nullable IrisLodProgram fluidProgram;
   private @Nullable GlFramebuffer solidTarget;
   private @Nullable GlFramebuffer fluidTarget;
   private @Nullable DepthTexture depth;
   private @Nullable DepthTexture depthNoTranslucent;
   private boolean ownFluidProgram;
   private boolean ready;
   private int width;
   private int height;
   private int indexBuffer;
   private int indexQuads;
   private int previousDraw;
   private int previousRead;
   private final int[] savedViewport = new int[4];
   private final boolean[] savedBlend = new boolean[8];
   private final boolean[][] savedColorMask = new boolean[8][4];
   private boolean savedDepthTest;
   private int savedDepthFunc;
   private boolean savedDepthMask;
   private boolean savedCull;
   private final int[] savedBlendFunc = new int[4];
   private int savedProgram;
   private int savedVao;
   private int savedArrayBuffer;
   private Matrix4f projection = new Matrix4f();
   private Matrix4fc modelView = new Matrix4f();
   private boolean wasEnabled = true;

   @Override
   public boolean active() {
      if (!ShaderApi.getInstance().isShaderPackInUse()) {
         this.release();
         return false;
      }

      if (PryzmaShaders.getPipelineManager().getPipelineNullable() instanceof ShadedWorldRenderingPipeline iris) {
         if (iris != this.pipeline) {
            this.setup(iris);
         }

         return this.ready;
      } else {
         this.release();
         return false;
      }
   }

   @Override
   public void enabledChanged(boolean enabled) {
      if (enabled != this.wasEnabled) {
         this.wasEnabled = enabled;
         if (ShaderApi.getInstance().isShaderPackInUse()) {
            try {
               PryzmaShaders.reload();
            } catch (Exception e) {
               Farlands.LOG.warn("Could not reload the shader pack", e);
            }
         }
      }
   }

   private void setup(ShadedWorldRenderingPipeline iris) {
      this.release();
      this.pipeline = iris;
      Optional<ProgramSource> terrain = iris.getDHTerrainShader();
      if (!terrain.isEmpty()) {
         try {
            this.solidProgram = IrisLodProgram.create("farlands_terrain", false, terrain.get(), iris);
            this.solidTarget = iris.createDHFramebuffer(terrain.get(), false);
            Optional<ProgramSource> water = iris.getDHWaterShader();
            if (water.isPresent()) {
               this.fluidProgram = IrisLodProgram.create("farlands_water", true, water.get(), iris);
               this.fluidTarget = iris.createDHFramebuffer(water.get(), true);
               this.ownFluidProgram = true;
            } else {
               this.fluidProgram = this.solidProgram;
               this.fluidTarget = iris.createDHFramebuffer(terrain.get(), true);
               this.ownFluidProgram = false;
            }

            this.width = -1;
            this.ready = true;
         } catch (RuntimeException e) {
            Farlands.LOG.warn("The shader pack's distant terrain programs could not be used", e);
            this.release();
            this.pipeline = iris;
         }
      }
   }

   private void release() {
      if (this.solidProgram != null) {
         this.solidProgram.free();
      }

      if (this.fluidProgram != null && this.ownFluidProgram) {
         this.fluidProgram.free();
      }

      if (this.depth != null) {
         this.depth.destroy();
      }

      if (this.depthNoTranslucent != null) {
         this.depthNoTranslucent.destroy();
      }

      this.solidProgram = null;
      this.fluidProgram = null;
      this.solidTarget = null;
      this.fluidTarget = null;
      this.depth = null;
      this.depthNoTranslucent = null;
      this.pipeline = null;
      this.ready = false;
      IrisState.depthTex = 0;
      IrisState.depthTexNoTranslucent = 0;
   }

   private void ensureDepth() {
      int w = Minecraft.getInstance().gameRenderer.mainRenderTarget().width;
      int h = Minecraft.getInstance().gameRenderer.mainRenderTarget().height;
      if (w != this.width || h != this.height || this.depth == null) {
         if (this.depth != null) {
            this.depth.destroy();
         }

         if (this.depthNoTranslucent != null) {
            this.depthNoTranslucent.destroy();
         }

         this.width = w;
         this.height = h;
         this.depth = new DepthTexture("Farlands depth", w, h, DepthBufferFormat.DEPTH32F);
         this.depthNoTranslucent = new DepthTexture("Farlands depth before water", w, h, DepthBufferFormat.DEPTH32F);
         this.solidTarget.addDepthAttachmentBypass(this.depth.getTextureId());
         this.fluidTarget.addDepthAttachmentBypass(this.depth.getTextureId());
         IrisState.depthTex = this.depth.getTextureId();
         IrisState.depthTexNoTranslucent = this.depthNoTranslucent.getTextureId();
      }
   }

   @Override
   public void frame(float near, float far) {
      Matrix4f g = new Matrix4f(CapturedRenderingState.INSTANCE.getGbufferProjection());
      float n = g.m32() / (g.m22() - 1.0F);
      float f = g.m32() / (g.m22() + 1.0F);
      if (Float.isFinite(n) && Float.isFinite(f) && n > 0.0F && f > n) {
         float lodNear = Math.max(n, Math.min(16.0F, 0.2F * Minecraft.getInstance().options.getEffectiveRenderDistance() * 16.0F));
         boolean clip = lodNear > n && lodNear < f * 0.5F;
         if (clip) {
            g.m22((f + lodNear) / (lodNear - f));
            g.m32(2.0F * f * lodNear / (lodNear - f));
         }

         this.projection = g;
         IrisState.near = clip ? lodNear : n;
         IrisState.far = f;
      } else {
         this.projection = new Matrix4f().setPerspective(g.perspectiveFov(), g.m11() / g.m00(), near, far);
      }

      this.modelView = new Matrix4f(CapturedRenderingState.INSTANCE.getGbufferModelView());
      IrisState.projection = new Matrix4f(this.projection);
   }

   @Override
   public int[] upload(ByteBuffer data, int quads) {
      int vbo = GL15C.glGenBuffers();
      GlStateManager._glBindBuffer(34962, vbo);
      GL15C.glBufferData(34962, data, 35044);
      int vao = GL30C.glGenVertexArrays();
      GlStateManager._glBindVertexArray(vao);
      GlStateManager._glBindBuffer(34962, vbo);
      GL20C.glEnableVertexAttribArray(0);
      GL30C.glVertexAttribIPointer(0, 4, 5123, 16, 0L);
      GL20C.glEnableVertexAttribArray(1);
      GL20C.glVertexAttribPointer(1, 4, 5121, true, 16, 8L);
      GL20C.glEnableVertexAttribArray(2);
      GL30C.glVertexAttribIPointer(2, 4, 5121, 16, 12L);
      GlStateManager._glBindVertexArray(0);
      GlStateManager._glBindBuffer(34962, 0);
      this.ensureIndices(quads);
      return new int[]{vbo, vao};
   }

   @Override
   public void delete(int vbo, int vao) {
      if (vao != 0) {
         GL30C.glDeleteVertexArrays(vao);
      }

      if (vbo != 0) {
         GL15C.glDeleteBuffers(vbo);
      }
   }

   private void ensureIndices(int quads) {
      if (quads > this.indexQuads) {
         int count = Math.max(quads, Math.max(4096, this.indexQuads * 2));
         IntBuffer data = MemoryUtil.memAllocInt(count * 6);

         try {
            for (int q = 0; q < count; q++) {
               int v = q * 4;
               data.put(v).put(v + 1).put(v + 2).put(v + 2).put(v + 3).put(v);
            }

            data.flip();
            if (this.indexBuffer == 0) {
               this.indexBuffer = GL15C.glGenBuffers();
            }

            GlStateManager._glBindBuffer(34963, this.indexBuffer);
            GL15C.glBufferData(34963, data, 35044);
            GlStateManager._glBindBuffer(34963, 0);
         } finally {
            MemoryUtil.memFree(data);
         }

         this.indexQuads = count;
      }
   }

   @Override
   public void begin(boolean fluid, float near) {
      this.previousDraw = GL11C.glGetInteger(36006);
      this.previousRead = GL11C.glGetInteger(36010);
      this.ensureDepth();
      boolean reverse = false;
      this.saveState();
      GlFramebuffer target = fluid ? this.fluidTarget : this.solidTarget;
      target.addDepthAttachmentBypass(this.depth.getTextureId());
      target.bind();
      GL11C.glViewport(0, 0, this.width, this.height);
      GL11C.glEnable(2929);
      GL11C.glDepthFunc(reverse ? 518 : 515);
      GL11C.glDepthMask(true);

      for (int i = 0; i < 8; i++) {
         GL30C.glColorMaski(i, true, true, true, true);
         if (fluid) {
            GL30C.glEnablei(3042, i);
         } else {
            GL30C.glDisablei(3042, i);
         }
      }

      if (fluid) {
         GL14C.glBlendFuncSeparate(770, 771, 1, 771);
      }

      if (!fluid) {
         GL30C.glClearBufferfv(6145, 0, new float[]{reverse ? 0.0F : 1.0F});
         GL11C.glEnable(2884);
      } else {
         GL11C.glDisable(2884);
      }

      IrisLodProgram program = fluid ? this.fluidProgram : this.solidProgram;
      program.bind();
      program.fill(this.projection, this.modelView, near);
   }

   @Override
   public void draw(boolean fluid, int vao, float x, float y, float z, int firstQuad, int quads) {
      if (quads > 0) {
         IrisLodProgram program = fluid ? this.fluidProgram : this.solidProgram;
         program.modelOffset(x, y, z);
         GL30C.glBindVertexArray(vao);
         GL15C.glBindBuffer(34963, this.indexBuffer);
         GL32C.glDrawElementsBaseVertex(4, quads * 6, 5125, 0L, firstQuad * 4);
      }
   }

   @Override
   public void end(boolean fluid) {
      IrisLodProgram program = fluid ? this.fluidProgram : this.solidProgram;
      program.unbind();
      GL30C.glBindVertexArray(0);
      if (!fluid && this.depth != null && this.depthNoTranslucent != null) {
         GL43C.glCopyImageSubData(
            this.depth.getTextureId(), 3553, 0, 0, 0, 0, this.depthNoTranslucent.getTextureId(), 3553, 0, 0, 0, 0, this.width, this.height, 1
         );
      }

      this.restoreState();
      GlStateManager._glBindFramebuffer(36009, this.previousDraw);
      GlStateManager._glBindFramebuffer(36008, this.previousRead);
   }

   @Override
   public void clear() {
      if (this.ready && this.solidTarget != null && this.fluidTarget != null) {
         int draw = GL11C.glGetInteger(36006);
         int read = GL11C.glGetInteger(36010);
         this.ensureDepth();
         boolean mask = GL11C.glGetBoolean(2930);
         this.solidTarget.addDepthAttachmentBypass(this.depth.getTextureId());
         this.solidTarget.bind();
         GL11C.glDepthMask(true);
         GL30C.glClearBufferfv(6145, 0, new float[]{1.0F});
         GL11C.glDepthMask(mask);
         if (this.depthNoTranslucent != null) {
            GL43C.glCopyImageSubData(
               this.depth.getTextureId(), 3553, 0, 0, 0, 0, this.depthNoTranslucent.getTextureId(), 3553, 0, 0, 0, 0, this.width, this.height, 1
            );
         }

         GlStateManager._glBindFramebuffer(36009, draw);
         GlStateManager._glBindFramebuffer(36008, read);
      }
   }

   private void saveState() {
      this.savedProgram = GL11C.glGetInteger(35725);
      this.savedVao = GL11C.glGetInteger(34229);
      this.savedArrayBuffer = GL11C.glGetInteger(34964);
      GL11C.glGetIntegerv(2978, this.savedViewport);
      this.savedDepthTest = GL11C.glIsEnabled(2929);
      this.savedDepthFunc = GL11C.glGetInteger(2932);
      this.savedDepthMask = GL11C.glGetBoolean(2930);
      this.savedCull = GL11C.glIsEnabled(2884);
      this.savedBlendFunc[0] = GL11C.glGetInteger(32969);
      this.savedBlendFunc[1] = GL11C.glGetInteger(32968);
      this.savedBlendFunc[2] = GL11C.glGetInteger(32971);
      this.savedBlendFunc[3] = GL11C.glGetInteger(32970);
      MemoryStack stack = MemoryStack.stackPush();

      try {
         ByteBuffer mask = stack.malloc(4);

         for (int i = 0; i < 8; i++) {
            this.savedBlend[i] = GL30C.glIsEnabledi(3042, i);
            GL30C.glGetBooleani_v(3107, i, mask);

            for (int c = 0; c < 4; c++) {
               this.savedColorMask[i][c] = mask.get(c) != 0;
            }
         }
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

   private void restoreState() {
      GL20C.glUseProgram(this.savedProgram);
      GL30C.glBindVertexArray(this.savedVao);
      GL15C.glBindBuffer(34962, this.savedArrayBuffer);
      GL11C.glViewport(this.savedViewport[0], this.savedViewport[1], this.savedViewport[2], this.savedViewport[3]);
      if (this.savedDepthTest) {
         GL11C.glEnable(2929);
      } else {
         GL11C.glDisable(2929);
      }

      GL11C.glDepthFunc(this.savedDepthFunc);
      GL11C.glDepthMask(this.savedDepthMask);
      if (this.savedCull) {
         GL11C.glEnable(2884);
      } else {
         GL11C.glDisable(2884);
      }

      GL14C.glBlendFuncSeparate(this.savedBlendFunc[0], this.savedBlendFunc[1], this.savedBlendFunc[2], this.savedBlendFunc[3]);

      for (int i = 0; i < 8; i++) {
         if (this.savedBlend[i]) {
            GL30C.glEnablei(3042, i);
         } else {
            GL30C.glDisablei(3042, i);
         }

         GL30C.glColorMaski(i, this.savedColorMask[i][0], this.savedColorMask[i][1], this.savedColorMask[i][2], this.savedColorMask[i][3]);
      }
   }

   @Override
   public void close() {
      this.release();
      if (this.indexBuffer != 0) {
         GL15C.glDeleteBuffers(this.indexBuffer);
      }

      this.indexBuffer = 0;
      this.indexQuads = 0;
   }
}
