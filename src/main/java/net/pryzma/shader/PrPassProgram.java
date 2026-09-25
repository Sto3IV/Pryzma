package net.pryzma.shader;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;

import net.minecraft.client.renderer.ShaderInstance;

/**
 * A full-screen pass of a shader pack ({@code deferred}, {@code composite}, {@code final}...):
 * a program of its own, drawn over a unit quad with an orthographic 0..1 projection, as OptiFine
 * draws them. Samplers get fixed texture units at link time.
 */
final class PrPassProgram implements AutoCloseable {
    private static final Matrix4f ORTHO = new Matrix4f().setOrtho(0.0F, 1.0F, 0.0F, 1.0F, -1.0F, 1.0F);
    private static int quadVao;
    private static int quadVbo;

    final String name;
    final int[] drawBuffers;
    /** OptiFine stage of the pass: prepare, deferred or composite (final included). */
    final String stage;
    /** {@code blend.<pass>}; {@code null} draws without blending. */
    final PrBlend blend;
    /** Buffers whose mipmaps this pass needs built before it draws, a bit per buffer. */
    int mipmaps;
    private final int program;
    private final List<String> samplers = new ArrayList<>();
    private final List<PrUniforms.Binding> bindings;
    private final int modelView;
    private final int projection;
    /** Derived matrices the translation declares: identity for a screen quad, except the inverse projection. */
    private final int textureMatrix;
    private final int normalMatrix;
    private final int modelViewInverse;
    private final int projectionInverse;

    private PrPassProgram(String name, int program, int[] drawBuffers, List<String> samplers, PrUniforms uniforms,
            PrBlend blend) {
        this.name = name;
        this.stage = name.startsWith("prepare") ? "prepare" : name.startsWith("deferred") ? "deferred"
                : name.startsWith("shadowcomp") ? "shadowcomp" : "composite";
        this.blend = blend;
        this.program = program;
        this.drawBuffers = drawBuffers;
        this.samplers.addAll(samplers);
        this.bindings = uniforms.bind(program);
        this.modelView = GL20.glGetUniformLocation(program, "ModelViewMat");
        this.projection = GL20.glGetUniformLocation(program, "ProjMat");
        this.textureMatrix = GL20.glGetUniformLocation(program, "pr_TextureMat");
        this.normalMatrix = GL20.glGetUniformLocation(program, "pr_NormalMatrix");
        this.modelViewInverse = GL20.glGetUniformLocation(program, "pr_ModelViewMatInverse");
        this.projectionInverse = GL20.glGetUniformLocation(program, "pr_ProjMatInverse");
        GlStateManager._glUseProgram(program);
        for (int unit = 0; unit < this.samplers.size(); unit++) {
            GL20.glUniform1i(GL20.glGetUniformLocation(program, this.samplers.get(unit)), unit);
        }
        GlStateManager._glUseProgram(0);
    }

    /** Compiles and links a pass; {@code null} (with the log in {@code warn}) when it fails. */
    static PrPassProgram create(String name, PrGlslTransformer.Result source, int[] drawBuffers, PrUniforms uniforms,
            PrBlend blend, Consumer<String> warn) {
        int vs = compile(GL20.GL_VERTEX_SHADER, source.vertex(), name + ".vsh", warn);
        int fs = compile(GL20.GL_FRAGMENT_SHADER, source.fragment(), name + ".fsh", warn);
        int gs = source.geometry() == null ? 0 : compile(0x8DD9, source.geometry(), name + ".gsh", warn);
        if (vs == 0 || fs == 0 || source.geometry() != null && gs == 0) {
            for (int s : new int[] {vs, fs, gs}) {
                if (s != 0) {
                    GL20.glDeleteShader(s);
                }
            }
            return null;
        }
        int program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vs);
        GL20.glAttachShader(program, fs);
        if (gs != 0) {
            GL20.glAttachShader(program, gs);
        }
        GL20.glBindAttribLocation(program, 0, "Position");
        GL20.glBindAttribLocation(program, 1, "UV0");
        GL20.glLinkProgram(program);
        GL20.glDeleteShader(vs);
        GL20.glDeleteShader(fs);
        if (gs != 0) {
            GL20.glDeleteShader(gs);
        }
        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            warn.accept("Cannot link " + name + ": " + GL20.glGetProgramInfoLog(program, 32768).trim());
            GL20.glDeleteProgram(program);
            return null;
        }
        List<String> samplers = new ArrayList<>();
        for (String text : new String[] {source.vertex(), source.fragment()}) {
            for (String sampler : PrGlslTransformer.samplers(text)) {
                if (!samplers.contains(sampler) && GL20.glGetUniformLocation(program, sampler) >= 0) {
                    samplers.add(sampler);
                }
            }
        }
        return new PrPassProgram(name, program, drawBuffers, samplers, uniforms, blend);
    }

    static int compile(int type, String source, String name, Consumer<String> warn) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            warn.accept("Cannot compile " + name + ": " + GL20.glGetShaderInfoLog(shader, 32768).trim());
            GL20.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }

    /** Draws the pass with its samplers bound; the caller has bound the target framebuffer. */
    void draw(PrShaderPipeline pipeline) {
        GlStateManager._glUseProgram(program);
        for (int unit = 0; unit < samplers.size(); unit++) {
            PrGl.bindTexture(unit, Math.max(pipeline.samplerTexture(samplers.get(unit), stage), 0));
        }
        GlStateManager._activeTexture(GL20.GL_TEXTURE0);
        PrGl.restoreActiveTexture();
        PrUniforms.upload(bindings);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            if (modelView >= 0) {
                GL20.glUniformMatrix4fv(modelView, false, new Matrix4f().get(stack.mallocFloat(16)));
            }
            if (projection >= 0) {
                GL20.glUniformMatrix4fv(projection, false, ORTHO.get(stack.mallocFloat(16)));
            }
            if (textureMatrix >= 0) {
                GL20.glUniformMatrix4fv(textureMatrix, false, new Matrix4f().get(stack.mallocFloat(16)));
            }
            if (modelViewInverse >= 0) {
                GL20.glUniformMatrix4fv(modelViewInverse, false, new Matrix4f().get(stack.mallocFloat(16)));
            }
            if (projectionInverse >= 0) {
                GL20.glUniformMatrix4fv(projectionInverse, false, new Matrix4f(ORTHO).invert().get(stack.mallocFloat(16)));
            }
            if (normalMatrix >= 0) {
                GL20.glUniformMatrix3fv(normalMatrix, false, new org.joml.Matrix3f().get(stack.mallocFloat(9)));
            }
        }
        drawQuad();
        for (int unit = samplers.size() - 1; unit >= 0; unit--) {
            PrGl.bindTexture(unit, 0);
        }
        GlStateManager._activeTexture(GL20.GL_TEXTURE0);
        PrGl.restoreActiveTexture();
        GlStateManager._glUseProgram(0);
        // The game skips glUseProgram when it believes its shader is still bound.
        ShaderInstance.lastProgramId = -1;
    }

    private static void drawQuad() {
        if (quadVao == 0) {
            quadVao = GL30.glGenVertexArrays();
            quadVbo = GL15.glGenBuffers();
            GL30.glBindVertexArray(quadVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, quadVbo);
            // x, y, z, u, v for a unit quad as two triangles.
            float[] data = {
                0, 0, 0, 0, 0,  1, 0, 0, 1, 0,  1, 1, 0, 1, 1,
                0, 0, 0, 0, 0,  1, 1, 0, 1, 1,  0, 1, 0, 0, 1};
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_STATIC_DRAW);
            GL20.glEnableVertexAttribArray(0);
            GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 20, 0);
            GL20.glEnableVertexAttribArray(1);
            GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 20, 12);
        } else {
            GL30.glBindVertexArray(quadVao);
        }
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
        GL30.glBindVertexArray(0);
        // The game tracks the bound vertex array; it must not assume its own is still bound.
        BufferUploader.invalidate();
        RenderSystem.assertOnRenderThread();
    }

    @Override
    public void close() {
        GL20.glDeleteProgram(program);
    }
}
