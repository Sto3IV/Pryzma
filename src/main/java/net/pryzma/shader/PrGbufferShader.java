package net.pryzma.shader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryStack;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;

/**
 * A shader pack's gbuffers program, compiled for one vanilla shader it stands in for. It is a
 * {@link ShaderInstance}, so every vanilla draw path (chunk layers, entity batches, immediate
 * buffers) sets its matrices and textures as usual; on {@link #apply()} it also routes its outputs
 * to the program's draw buffers and uploads the pack's own uniforms and samplers.
 */
final class PrGbufferShader extends ShaderInstance {
    /** The game's uniforms and their JSON types, set by vanilla code when a translated program declares them. */
    private static final Map<String, String[]> GAME_UNIFORMS = gameUniforms();

    final String program;
    final int[] drawBuffers;
    final float alphaTestRef;
    final PrShaderMacros.Stage stage;
    /** {@code blend.<program>}; {@code null} keeps the blending of the render type. */
    final PrBlend blend;
    private final PrShaderPipeline pipeline;
    /** Samplers the vanilla code binds (units 0-11). */
    private final List<String> samplers;
    /** Samplers past the game's 12 tracked units, bound here on units 12 and up. */
    private final List<String> extraSamplers;
    private final int[] extraLocations;
    private final List<PrUniforms.Binding> bindings;
    private final int normalMatrix;
    private final int modelViewInverse;
    private final int projectionInverse;
    private final int textureMatrix;

    private PrGbufferShader(ResourceProvider provider, ResourceLocation id, VertexFormat format, String program,
            int[] drawBuffers, float alphaTestRef, PrShaderMacros.Stage stage, PrBlend blend, PrShaderPipeline pipeline,
            List<String> samplers, List<String> extraSamplers) throws IOException {
        super(provider, id, format);
        this.program = program;
        this.drawBuffers = drawBuffers;
        this.alphaTestRef = alphaTestRef;
        this.stage = stage;
        this.blend = blend;
        this.pipeline = pipeline;
        this.samplers = samplers;
        this.extraSamplers = extraSamplers;
        this.extraLocations = new int[extraSamplers.size()];
        for (int i = 0; i < extraLocations.length; i++) {
            extraLocations[i] = GL20.glGetUniformLocation(getId(), extraSamplers.get(i));
        }
        this.bindings = pipeline.uniforms.bind(getId());
        this.normalMatrix = GL20.glGetUniformLocation(getId(), "pr_NormalMatrix");
        this.modelViewInverse = GL20.glGetUniformLocation(getId(), "pr_ModelViewMatInverse");
        this.projectionInverse = GL20.glGetUniformLocation(getId(), "pr_ProjMatInverse");
        this.textureMatrix = GL20.glGetUniformLocation(getId(), "pr_TextureMat");
    }

    /** Compiles {@code source} as a stand-in for a vanilla shader with {@code format}. */
    static PrGbufferShader create(PrShaderPipeline pipeline, String id, PrGlslTransformer.Result source, VertexFormat format,
            String program, int[] drawBuffers, float alphaTestRef, PrShaderMacros.Stage stage, PrBlend blend) throws IOException {
        List<String> samplers = new ArrayList<>();
        for (String text : new String[] {source.vertex(), source.fragment()}) {
            for (String sampler : PrGlslTransformer.samplers(text)) {
                if (!samplers.contains(sampler)) {
                    samplers.add(sampler);
                }
            }
        }
        // The texture and the lightmap first: the game binds at most 12 samplers.
        samplers.sort(java.util.Comparator.comparingInt(s -> switch (s) {
            case "gtexture", "texture", "tex", "gcolor", "colortex0" -> 0;
            case "lightmap" -> 1;
            default -> 2;
        }));
        List<String> extra = samplers.size() > PrGl.TRACKED_UNITS
                ? new ArrayList<>(samplers.subList(PrGl.TRACKED_UNITS, samplers.size())) : new ArrayList<>();
        samplers = new ArrayList<>(samplers.subList(0, Math.min(samplers.size(), PrGl.TRACKED_UNITS)));
        JsonObject json = new JsonObject();
        json.addProperty("vertex", id);
        json.addProperty("fragment", id);
        JsonArray samplerArray = new JsonArray();
        for (String s : samplers) {
            JsonObject o = new JsonObject();
            o.addProperty("name", s);
            samplerArray.add(o);
        }
        json.add("samplers", samplerArray);
        JsonArray uniforms = new JsonArray();
        String all = source.vertex() + "\n" + source.fragment();
        GAME_UNIFORMS.forEach((name, spec) -> {
            if (PrGlslTransformer.declared(all, name)) {
                JsonObject u = new JsonObject();
                u.addProperty("name", name);
                u.addProperty("type", spec[0]);
                int count = Integer.parseInt(spec[1]);
                u.addProperty("count", count);
                JsonArray values = new JsonArray();
                if (spec[0].startsWith("matrix")) {
                    for (int k = 0; k < count; k++) {
                        values.add(k % 5 == 0 ? 1.0F : 0.0F);
                    }
                } else {
                    values.add(spec[0].equals("int") ? 0 : 0.0F);
                }
                u.add("values", values);
                uniforms.add(u);
            }
        });
        json.add("uniforms", uniforms);

        ResourceLocation location = ResourceLocation.parse(id);
        Map<ResourceLocation, byte[]> files = new LinkedHashMap<>();
        files.put(location.withPath("shaders/core/" + location.getPath() + ".json"), json.toString().getBytes(StandardCharsets.UTF_8));
        files.put(location.withPath("shaders/core/" + location.getPath() + ".vsh"), source.vertex().getBytes(StandardCharsets.UTF_8));
        files.put(location.withPath("shaders/core/" + location.getPath() + ".fsh"), source.fragment().getBytes(StandardCharsets.UTF_8));
        ResourceProvider provider = loc -> {
            byte[] bytes = files.get(loc);
            return bytes == null ? Optional.empty()
                    : Optional.of(new Resource(Minecraft.getInstance().getVanillaPackResources(), () -> new ByteArrayInputStream(bytes)));
        };
        return new PrGbufferShader(provider, location, format, program, drawBuffers, alphaTestRef, stage, blend, pipeline, samplers, extra);
    }

    private static Map<String, String[]> gameUniforms() {
        Map<String, String[]> m = new LinkedHashMap<>();
        m.put("ModelViewMat", new String[] {"matrix4x4", "16"});
        m.put("ProjMat", new String[] {"matrix4x4", "16"});
        m.put("TextureMat", new String[] {"matrix4x4", "16"});
        m.put("ColorModulator", new String[] {"float", "4"});
        m.put("ChunkOffset", new String[] {"float", "3"});
        m.put("FogStart", new String[] {"float", "1"});
        m.put("FogEnd", new String[] {"float", "1"});
        m.put("FogColor", new String[] {"float", "4"});
        m.put("FogShape", new String[] {"int", "1"});
        m.put("GameTime", new String[] {"float", "1"});
        m.put("ScreenSize", new String[] {"float", "2"});
        m.put("Light0_Direction", new String[] {"float", "3"});
        m.put("Light1_Direction", new String[] {"float", "3"});
        m.put("LineWidth", new String[] {"float", "1"});
        m.put("GlintAlpha", new String[] {"float", "1"});
        return m;
    }

    @Override
    public void apply() {
        pipeline.beforeGbufferDraw(this);
        for (String name : samplers) {
            int texture = pipeline.samplerTexture(name, stageName());
            if (texture >= 0) {
                super.setSampler(name, texture);
            }
        }
        super.apply();
        for (int i = 0; i < extraSamplers.size(); i++) {
            if (extraLocations[i] >= 0) {
                int unit = PrGl.TRACKED_UNITS + i;
                GL20.glUniform1i(extraLocations[i], unit);
                PrGl.bindTexture(unit, Math.max(pipeline.samplerTexture(extraSamplers.get(i), stageName()), 0));
            }
        }
        if (!extraSamplers.isEmpty()) {
            PrGl.restoreActiveTexture();
        }
        PrUniforms.upload(bindings);
        if (normalMatrix >= 0 || modelViewInverse >= 0 || projectionInverse >= 0 || textureMatrix >= 0) {
            uploadDerived();
        }
    }

    private String stageName() {
        return program.startsWith("shadow") ? "shadow" : "gbuffers";
    }

    /** Matrices OptiFine derives per draw: the normal matrix and inverses of the current model-view and projection. */
    private void uploadDerived() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Matrix4f modelView = MODEL_VIEW_MATRIX != null ? new Matrix4f().set(MODEL_VIEW_MATRIX.getFloatBuffer())
                    : new Matrix4f(RenderSystem.getModelViewMatrix());
            if (normalMatrix >= 0) {
                GL20.glUniformMatrix3fv(normalMatrix, false, PrUniforms.normalMatrix(modelView, new Matrix3f()).get(stack.mallocFloat(9)));
            }
            if (modelViewInverse >= 0) {
                GL20.glUniformMatrix4fv(modelViewInverse, false, new Matrix4f(modelView).invert().get(stack.mallocFloat(16)));
            }
            if (projectionInverse >= 0) {
                Matrix4f projection = PROJECTION_MATRIX != null ? new Matrix4f().set(PROJECTION_MATRIX.getFloatBuffer())
                        : new Matrix4f(RenderSystem.getProjectionMatrix());
                GL20.glUniformMatrix4fv(projectionInverse, false, projection.invert().get(stack.mallocFloat(16)));
            }
            if (textureMatrix >= 0) {
                GL20.glUniformMatrix4fv(textureMatrix, false, RenderSystem.getTextureMatrix().get(stack.mallocFloat(16)));
            }
        }
    }
}
