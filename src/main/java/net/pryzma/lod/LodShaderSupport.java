package net.pryzma.lod;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL43C;
import org.lwjgl.system.MemoryStack;

import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Ints;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.ProgramManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.phys.Vec3;
import net.pryzma.Pryzma;
import net.pryzma.PryzmaConfig;
import net.pryzma.shader.gl.ShaderRenderSystem;
import net.pryzma.shader.gl.blending.BlendModeOverride;
import net.pryzma.shader.gl.blending.BufferBlendOverride;
import net.pryzma.shader.gl.framebuffer.GlFramebuffer;
import net.pryzma.shader.gl.program.ProgramImages;
import net.pryzma.shader.gl.program.ProgramSamplers;
import net.pryzma.shader.gl.program.ProgramUniforms;
import net.pryzma.shader.gl.shader.GlShader;
import net.pryzma.shader.gl.shader.ShaderType;
import net.pryzma.shader.gl.state.FogMode;
import net.pryzma.shader.gl.texture.DepthBufferFormat;
import net.pryzma.shader.gl.texture.DepthCopyStrategy;
import net.pryzma.shader.gl.texture.TextureType;
import net.pryzma.shader.pipeline.ShadedWorldRenderingPipeline;
import net.pryzma.shader.pipeline.WorldRenderingPhase;
import net.pryzma.shader.pipeline.transform.PatchShaderType;
import net.pryzma.shader.pipeline.transform.ShaderPrinter;
import net.pryzma.shader.pipeline.transform.TransformPatcher;
import net.pryzma.shader.samplers.ShaderSamplers;
import net.pryzma.shader.shaderpack.loading.ProgramId;
import net.pryzma.shader.shaderpack.programs.ProgramSource;
import net.pryzma.shader.targets.Blaze3dRenderTargetExt;
import net.pryzma.shader.targets.DepthTexture;
import net.pryzma.shader.uniforms.CapturedRenderingState;
import net.pryzma.shader.uniforms.CommonUniforms;
import net.pryzma.shader.uniforms.builtin.BuiltinReplacementUniforms;
import net.pryzma.shader.uniforms.custom.CustomUniforms;

/**
 * Farlands LOD under a shader pack: the LOD drawn into the pack's G-buffer, so its deferred and composite passes
 * light, shadow, fog and reflect distant terrain as their own.
 *
 * <p>One instance belongs to one {@link ShadedWorldRenderingPipeline}, created with it only while the Farlands option
 * is on ({@link #createFor}); with the option off the pipeline holds none and runs exactly as without Farlands. The
 * pack is reloaded when the option is switched ({@link PryzmaLod#onSettingsChanged}).
 *
 * <h2>Packs with Distant Horizons programs</h2>
 * The LOD speaks the contract such packs are written against, as Iris implements it for Distant Horizons:
 * <ul>
 * <li>the {@code DISTANT_HORIZONS} macro is defined, and {@code dhNearPlane}, {@code dhFarPlane},
 * {@code dhRenderDistance} and {@code dhProjection} describe the LOD pass ({@link #projection()});</li>
 * <li>{@code dh_terrain} draws the opaque LOD and {@code dh_water} the water into the pack's colour targets, with a
 * depth buffer of their own that the pack samples as {@code dhDepthTex0}; {@code dhDepthTex1} is that depth before the
 * water. Both are cleared every frame, so a pack reading them always sees valid depths.</li>
 * </ul>
 * The main depth buffer ({@code depthtex0}, {@code depthtex1}) is never cleared or written: vanilla terrain drawn
 * afterwards overwrites the LOD wherever it exists, and the composite passes tell the two apart by the two depths.
 *
 * <h2>Other packs</h2>
 * Without Distant Horizons programs the LOD is drawn with the pack's {@code gbuffers_terrain} and
 * {@code gbuffers_water} (or what they fall back to) into the G-buffer itself, main depth included, with the game's own
 * projection, so the pack reconstructs positions correctly; depth testing is {@code GL_LEQUAL}, and a polygon offset
 * lets vanilla terrain win where both cover the same block at the rim of the render distance. Such packs end their
 * fog at the render distance, so the LOD mostly lends the horizon silhouettes in their fog.
 *
 * <p>In both cases fragments horizontally inside the vanilla radius are discarded by code injected into the programs
 * ({@code pryzma_LodClip}), as the native pass does. Everything here runs on the render thread.
 */
public final class LodShaderSupport {
    /** Polygon offset of the fallback pass, in depth units: about a block at the rim of a 12-chunk render distance. */
    private static final float FALLBACK_DEPTH_UNITS = 32.0F;
    /** Generic attribute values for block attributes the LOD vertex lacks (fallback programs read them). */
    private static final float[] ENTITY = {-1.0F, -1.0F, 0.0F, 1.0F};
    private static final float[] MID_TEXCOORD = {0.0F, 0.0F, 0.0F, 1.0F};
    /** A tangent that is not parallel to any LOD face normal, so a pack's TBN never degenerates. */
    private static final float[] TANGENT = {0.57735026F, 0.57735026F, 0.57735026F, 1.0F};
    private static final float[] MID_BLOCK = {0.0F, 0.0F, 0.0F, 0.0F};
    private static final Matrix4f PROJECTION = new Matrix4f();

    private final ShadedWorldRenderingPipeline pipeline;
    /** Whether the pack has Distant Horizons programs: the LOD then has a depth buffer of its own. */
    private final boolean dhPrograms;
    private DepthTexture depth;
    private DepthTexture depthNoWater;
    private final GlFramebuffer depthTarget;
    private final GlFramebuffer depthNoWaterTarget;
    private final DepthCopyStrategy copyStrategy = DepthCopyStrategy.fastest(false);
    private int width;
    private int height;
    private int mainDepthVersion;

    @Nullable
    private LodProgram opaque;
    @Nullable
    private LodProgram water;
    @Nullable
    private GlFramebuffer opaqueTarget;
    @Nullable
    private GlFramebuffer waterTarget;
    private boolean opaqueDrawnThisFrame;
    private final Matrix4f modelView = new Matrix4f();
    private final Matrix4f projectionScratch = new Matrix4f();

    private LodShaderSupport(ShadedWorldRenderingPipeline pipeline) {
        this.pipeline = pipeline;
        this.dhPrograms = pipeline.getDHTerrainShader().isPresent();
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        this.width = main.width;
        this.height = main.height;
        this.mainDepthVersion = ((Blaze3dRenderTargetExt) main).pryzma$getDepthBufferVersion();
        this.depth = new DepthTexture("Farlands LOD depth", width, height, DepthBufferFormat.DEPTH32F);
        this.depthNoWater = new DepthTexture("Farlands LOD depth without water", width, height, DepthBufferFormat.DEPTH32F);
        this.depthTarget = depthOnlyTarget(depth);
        this.depthNoWaterTarget = depthOnlyTarget(depthNoWater);
        try {
            if (dhPrograms) {
                ProgramSource terrain = pipeline.getDHTerrainShader().orElseThrow();
                ProgramSource waterSource = pipeline.getDHWaterShader().orElse(terrain);
                opaque = LodProgram.create("dh_terrain", terrain, false, pipeline);
                water = LodProgram.create("dh_water", waterSource, true, pipeline);
                opaqueTarget = pipeline.createDHFramebuffer(terrain, false);
                waterTarget = pipeline.createDHFramebuffer(waterSource, true);
                attachDepth();
            } else {
                Optional<ProgramSource> terrain = pipeline.resolveProgram(ProgramId.TerrainSolid);
                Optional<ProgramSource> waterSource = pipeline.resolveProgram(ProgramId.Water);
                if (terrain.isPresent()) {
                    opaque = LodProgram.create("gbuffers_terrain", terrain.get(), false, pipeline);
                    opaqueTarget = pipeline.createGbufferFramebuffer(terrain.get(), false);
                }
                if (waterSource.isPresent()) {
                    water = LodProgram.create("gbuffers_water", waterSource.get(), true, pipeline);
                    waterTarget = pipeline.createGbufferFramebuffer(waterSource.get(), true);
                }
            }
            Pryzma.LOGGER.info("Farlands LOD: drawn into the shader pack's G-buffer with its {} programs",
                    dhPrograms ? "Distant Horizons" : "terrain and water");
        } catch (RuntimeException e) {
            freePrograms();
            Pryzma.LOGGER.error("Farlands LOD: the shader pack's {} programs could not be built for the LOD; distant terrain is not drawn under this pack",
                    dhPrograms ? "Distant Horizons" : "terrain", e);
        }
    }

    /** The support for {@code pipeline}, or null while the Farlands option is off. Never throws for a pack. */
    @Nullable
    public static LodShaderSupport createFor(ShadedWorldRenderingPipeline pipeline) {
        if (!isEnabled()) {
            return null;
        }
        return new LodShaderSupport(pipeline);
    }

    /** Whether the Farlands option is on: shader packs are then built with the Distant Horizons contract. */
    public static boolean isEnabled() {
        return PryzmaConfig.prFarlandsDistance > 0;
    }

    // ------------------------------------------------------------------ the pass as packs see it

    /** {@code dhRenderDistance}: the LOD radius in blocks. */
    public static int renderDistance() {
        return PryzmaConfig.prFarlandsDistance << 4;
    }

    /** {@code dhNearPlane}: the near plane of the LOD pass. */
    public static float nearPlane() {
        return LodMath.nearPlane(LodMath.clipRadius(Minecraft.getInstance().options.getEffectiveRenderDistance()));
    }

    /** {@code dhFarPlane}: the far plane of the LOD pass. */
    public static float farPlane() {
        return LodMath.farPlane(renderDistance());
    }

    /**
     * {@code dhProjection}: this frame's gbuffer projection with the LOD's depth range ({@link LodMath#withDepthRange}),
     * the projection the Distant Horizons programs draw with. A shared matrix, valid until the next call.
     */
    public static Matrix4fc projection() {
        LodMath.withDepthRange(CapturedRenderingState.INSTANCE.getGbufferProjection(), nearPlane(), farPlane(), PROJECTION);
        return PROJECTION;
    }

    /** {@code dhDepthTex0}, or {@code dhDepthTex1} ({@code noWater}): cleared to the far plane every frame. */
    public int depthTexture(boolean noWater) {
        return noWater ? depthNoWater.getTextureId() : depth.getTextureId();
    }

    /** Whether programs exist to draw with. */
    public boolean canDraw() {
        return opaque != null && opaqueTarget != null;
    }

    /** Whether the pack has Distant Horizons programs. */
    public boolean usesDistantHorizonsPrograms() {
        return dhPrograms;
    }

    // ------------------------------------------------------------------ frame

    /**
     * Start of a frame, after the pipeline sized its targets: follows the window size and the main depth buffer's
     * recreation, and clears both LOD depths.
     */
    public void beginFrame() {
        opaqueDrawnThisFrame = false;
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        boolean reattach = false;
        if (main.width != width || main.height != height) {
            width = main.width;
            height = main.height;
            depth.destroy();
            depthNoWater.destroy();
            depth = new DepthTexture("Farlands LOD depth", width, height, DepthBufferFormat.DEPTH32F);
            depthNoWater = new DepthTexture("Farlands LOD depth without water", width, height, DepthBufferFormat.DEPTH32F);
            depthTarget.addDepthAttachment(depth.getTextureId());
            depthNoWaterTarget.addDepthAttachment(depthNoWater.getTextureId());
            reattach = true;
        }
        int version = ((Blaze3dRenderTargetExt) main).pryzma$getDepthBufferVersion();
        if (version != mainDepthVersion) {
            // The pipeline points every depth-bearing framebuffer it owns at the new main depth, ours included.
            mainDepthVersion = version;
            reattach = true;
        }
        if (reattach && dhPrograms) {
            attachDepth();
        }
        RenderSystem.depthMask(true);
        RenderSystem.clearDepth(1.0);
        depthTarget.bind();
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        depthNoWaterTarget.bind();
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
    }

    /**
     * The opaque LOD, after the sky and before vanilla terrain.
     *
     * @param camera     camera position
     * @param clipRadius radius left to vanilla terrain ({@link LodMath#clipRadius})
     */
    public void renderOpaque(Vec3 camera, float clipRadius) {
        LodProgram program = opaque;
        GlFramebuffer target = opaqueTarget;
        if (program == null || target == null || !NativeLodRenderer.hasMeshes()) {
            return;
        }
        Matrix4fc projection = passProjection();
        modelView.set(CapturedRenderingState.INSTANCE.getGbufferModelView());
        List<NativeLodRenderer.SectorMesh> visible = NativeLodRenderer.collectVisible(modelView, projection, camera, clipRadius);
        if (visible.isEmpty()) {
            return;
        }
        pipeline.setPhase(WorldRenderingPhase.TERRAIN_SOLID);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        draw(program, target, visible, camera, clipRadius, projection, false);
        pipeline.setPhase(WorldRenderingPhase.NONE);
        opaqueDrawnThisFrame = true;
    }

    /** The LOD's water, after the vanilla translucent terrain (and so after the pack's deferred passes). */
    public void renderWater(Vec3 camera, float clipRadius) {
        if (dhPrograms) {
            // dhDepthTex1: the LOD depth before its water, as depthtex1 is the main depth before translucents.
            copyStrategy.copy(depthTarget, depth.getTextureId(), depthNoWaterTarget, depthNoWater.getTextureId(), width, height);
        }
        LodProgram program = water;
        GlFramebuffer target = waterTarget;
        if (!opaqueDrawnThisFrame || program == null || target == null) {
            return;
        }
        Matrix4fc projection = passProjection();
        modelView.set(CapturedRenderingState.INSTANCE.getGbufferModelView());
        List<NativeLodRenderer.SectorMesh> visible = NativeLodRenderer.collectVisible(modelView, projection, camera, clipRadius);
        boolean anyWater = false;
        for (NativeLodRenderer.SectorMesh mesh : visible) {
            anyWater |= mesh.waterQuads() > 0;
        }
        if (!anyWater) {
            return;
        }
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        draw(program, target, visible, camera, clipRadius, projection, true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    }

    /** The projection of the pass: the LOD's depth range with Distant Horizons programs, the game's own otherwise. */
    private Matrix4fc passProjection() {
        if (dhPrograms) {
            return projectionScratch.set(projection());
        }
        return projectionScratch.set(CapturedRenderingState.INSTANCE.getGbufferProjection());
    }

    private void draw(LodProgram program, GlFramebuffer target, List<NativeLodRenderer.SectorMesh> visible, Vec3 camera, float clipRadius,
            Matrix4fc projection, boolean waterPass) {
        Minecraft mc = Minecraft.getInstance();
        LightTexture lightTexture = mc.gameRenderer.lightTexture();
        lightTexture.turnOnLightLayer();
        if (!dhPrograms) {
            // The pack samples normals and specular at the LOD's texture coordinate: give it the neutral maps.
            pipeline.useNeutralPbrTextures();
            GlStateManager._enablePolygonOffset();
            GlStateManager._polygonOffset(0.0F, FALLBACK_DEPTH_UNITS);
        }
        program.bind();
        ShaderRenderSystem.bindTextureToUnit(TextureType.TEXTURE_2D.getGlType(), ShaderSamplers.LIGHTMAP_TEXTURE_UNIT, RenderSystem.getShaderTexture(2));
        program.update(modelView, projection, clipRadius, width, height);
        program.setGenericAttributes(true);
        target.bind();
        RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        float drop = waterPass ? NativeLodRenderer.WATER_DROP : 0.0F;
        int count = visible.size();
        for (int k = 0; k < count; k++) {
            // Opaque near to far (early depth rejection), water far to near (blending).
            NativeLodRenderer.SectorMesh mesh = visible.get(waterPass ? count - 1 - k : k);
            if ((waterPass ? mesh.waterQuads() : mesh.opaqueQuads()) == 0) {
                continue;
            }
            program.setModelOffset((float) (mesh.originX() - camera.x), (float) (-NativeLodMesher.Y_BIAS - drop - camera.y),
                    (float) (mesh.originZ() - camera.z));
            NativeLodRenderer.draw(mesh, waterPass, indices);
        }
        program.setGenericAttributes(false);
        program.unbind();
        VertexBuffer.unbind();
        if (!dhPrograms) {
            GlStateManager._polygonOffset(0.0F, 0.0F);
            GlStateManager._disablePolygonOffset();
        }
        lightTexture.turnOffLightLayer();
        Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
    }

    /** Frees the programs and depth buffers; the colour framebuffers belong to the pipeline's render targets. */
    public void destroy() {
        freePrograms();
        depthTarget.destroy();
        depthNoWaterTarget.destroy();
        depth.destroy();
        depthNoWater.destroy();
    }

    private void freePrograms() {
        if (opaque != null) {
            opaque.free();
            opaque = null;
        }
        if (water != null) {
            water.free();
            water = null;
        }
        opaqueTarget = null;
        waterTarget = null;
    }

    private void attachDepth() {
        if (opaqueTarget != null) {
            opaqueTarget.addDepthAttachment(depth.getTextureId());
        }
        if (waterTarget != null) {
            waterTarget.addDepthAttachment(depth.getTextureId());
        }
    }

    private static GlFramebuffer depthOnlyTarget(DepthTexture texture) {
        GlFramebuffer framebuffer = new GlFramebuffer();
        framebuffer.addDepthAttachment(texture.getTextureId());
        framebuffer.noDrawBuffers();
        return framebuffer;
    }

    /** A pack program transformed for the LOD vertex, with the uniforms and samplers of a G-buffer program. */
    private static final class LodProgram {
        private final int id;
        private final ProgramUniforms uniforms;
        private final ProgramSamplers samplers;
        private final ProgramImages images;
        private final CustomUniforms customUniforms;
        @Nullable
        private final BlendModeOverride blend;
        private final BufferBlendOverride[] bufferBlends;
        private final int modelOffset;
        private final int microOffset;
        private final int worldYOffset;
        private final int modelViewMatrix;
        private final int modelViewInverse;
        private final int projectionMatrix;
        private final int projectionInverse;
        private final int normalMatrix;
        private final int clipDistance;
        private final int lodClip;
        private final int lodInvViewport;
        private final int entityAttribute;
        private final int midTexCoordAttribute;
        private final int tangentAttribute;
        private final int midBlockAttribute;
        private final Matrix4f scratch = new Matrix4f();
        private final Matrix3f normalScratch = new Matrix3f();

        private LodProgram(String name, Map<PatchShaderType, String> stages, ProgramSource source, boolean translucent,
                ShadedWorldRenderingPipeline pipeline) {
            List<GlShader> shaders = new ArrayList<>();
            id = GL43C.glCreateProgram();
            try {
                // The LOD vertex: the locations of LodVertexFormat, which is what Iris binds for Distant Horizons.
                GL20C.glBindAttribLocation(id, 0, LodVertexFormat.ATTRIBUTE_POSITION);
                GL20C.glBindAttribLocation(id, 1, LodVertexFormat.ATTRIBUTE_COLOR);
                GL20C.glBindAttribLocation(id, 2, LodVertexFormat.ATTRIBUTE_EXTRA);
                attach(shaders, ShaderType.VERTEX, name + ".vsh", stages.get(PatchShaderType.VERTEX));
                attach(shaders, ShaderType.TESSELATION_CONTROL, name + ".tcs", stages.get(PatchShaderType.TESS_CONTROL));
                attach(shaders, ShaderType.TESSELATION_EVAL, name + ".tes", stages.get(PatchShaderType.TESS_EVAL));
                attach(shaders, ShaderType.GEOMETRY, name + ".gsh", stages.get(PatchShaderType.GEOMETRY));
                attach(shaders, ShaderType.FRAGMENT, name + ".fsh", stages.get(PatchShaderType.FRAGMENT));
                GL20C.glLinkProgram(id);
                if (GL20C.glGetProgrami(id, GL20C.GL_LINK_STATUS) != GL11.GL_TRUE) {
                    throw new IllegalStateException("Farlands LOD program " + name + " failed to link: " + GL20C.glGetProgramInfoLog(id));
                }
            } catch (RuntimeException e) {
                GL43C.glDeleteProgram(id);
                throw e;
            } finally {
                for (GlShader shader : shaders) {
                    shader.destroy();
                }
            }

            ProgramManager.glUseProgram(id);
            customUniforms = pipeline.getCustomUniforms();
            ProgramUniforms.Builder uniformBuilder = ProgramUniforms.builder(name, id);
            ProgramSamplers.Builder samplerBuilder = ProgramSamplers.builder(id, ShaderSamplers.WORLD_RESERVED_TEXTURE_UNITS);
            ProgramImages.Builder imageBuilder = ProgramImages.builder(id);
            CommonUniforms.addDynamicUniforms(uniformBuilder, FogMode.PER_VERTEX);
            customUniforms.assignTo(uniformBuilder);
            BuiltinReplacementUniforms.addBuiltinReplacementUniforms(uniformBuilder);
            Supplier<ImmutableSet<Integer>> flipped = translucent ? pipeline::getFlippedAfterTranslucent : pipeline::getFlippedAfterPrepare;
            pipeline.addGbufferOrShadowSamplers(samplerBuilder, imageBuilder, flipped, false, false, true, false);
            customUniforms.mapholderToPass(uniformBuilder, this);
            uniforms = uniformBuilder.buildUniforms();
            samplers = samplerBuilder.build();
            images = imageBuilder.build();

            blend = source.getDirectives().getBlendModeOverride().orElse(null);
            List<BufferBlendOverride> overrides = new ArrayList<>();
            source.getDirectives().getBufferBlendOverrides().forEach(information -> {
                int index = Ints.indexOf(source.getDirectives().getDrawBuffers(), information.index());
                if (index > -1) {
                    overrides.add(new BufferBlendOverride(index, information.blendMode()));
                }
            });
            bufferBlends = overrides.toArray(BufferBlendOverride[]::new);

            modelOffset = GL20C.glGetUniformLocation(id, "modelOffset");
            microOffset = GL20C.glGetUniformLocation(id, "mircoOffset");
            worldYOffset = GL20C.glGetUniformLocation(id, "worldYOffset");
            modelViewMatrix = GL20C.glGetUniformLocation(id, "iris_ModelViewMatrix");
            modelViewInverse = GL20C.glGetUniformLocation(id, "iris_ModelViewMatrixInverse");
            projectionMatrix = GL20C.glGetUniformLocation(id, "iris_ProjectionMatrix");
            projectionInverse = GL20C.glGetUniformLocation(id, "iris_ProjectionMatrixInverse");
            normalMatrix = GL20C.glGetUniformLocation(id, "iris_NormalMatrix");
            clipDistance = GL20C.glGetUniformLocation(id, "clipDistance");
            lodClip = GL20C.glGetUniformLocation(id, "pryzma_LodClip");
            lodInvViewport = GL20C.glGetUniformLocation(id, "pryzma_LodInvViewport");
            entityAttribute = GL20C.glGetAttribLocation(id, "mc_Entity");
            midTexCoordAttribute = GL20C.glGetAttribLocation(id, "mc_midTexCoord");
            tangentAttribute = GL20C.glGetAttribLocation(id, "at_tangent");
            midBlockAttribute = GL20C.glGetAttribLocation(id, "at_midBlock");
            ProgramManager.glUseProgram(0);
        }

        static LodProgram create(String name, ProgramSource source, boolean translucent, ShadedWorldRenderingPipeline pipeline) {
            String programName = "farlands_" + name;
            Map<PatchShaderType, String> transformed = TransformPatcher.patchDHTerrain(programName,
                    source.getVertexSource().orElseThrow(() -> new IllegalStateException(source.getName() + " has no vertex stage")),
                    source.getTessControlSource().orElse(null),
                    source.getTessEvalSource().orElse(null),
                    source.getGeometrySource().orElse(null),
                    source.getFragmentSource().orElseThrow(() -> new IllegalStateException(source.getName() + " has no fragment stage")),
                    pipeline.getTextureMap());
            ShaderPrinter.printProgram(programName).addSources(transformed).print();
            return new LodProgram(programName, transformed, source, translucent, pipeline);
        }

        private void attach(List<GlShader> shaders, ShaderType type, String name, @Nullable String source) {
            if (source == null) {
                return;
            }
            GlShader shader = new GlShader(type, name, source);
            shaders.add(shader);
            GL20C.glAttachShader(id, shader.getHandle());
        }

        void bind() {
            ProgramManager.glUseProgram(id);
            if (blend != null) {
                blend.apply();
            }
            for (BufferBlendOverride override : bufferBlends) {
                override.apply();
            }
        }

        /** Per-pass uniforms: matrices, clip, the pack's dynamic and custom uniforms, samplers and images. */
        void update(Matrix4fc modelView, Matrix4fc projection, float clipRadius, int width, int height) {
            setMatrix(modelViewMatrix, modelView);
            setMatrix(modelViewInverse, scratch.set(modelView).invert());
            setMatrix(projectionMatrix, projection);
            setMatrix(projectionInverse, scratch.set(projection).invert());
            if (normalMatrix >= 0) {
                scratch.set(modelView).invert().transpose3x3(normalScratch);
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    FloatBuffer buffer = stack.mallocFloat(9);
                    normalScratch.get(buffer);
                    GL20C.glUniformMatrix3fv(normalMatrix, false, buffer);
                }
            }
            if (microOffset >= 0) {
                GL20C.glUniform1f(microOffset, 0.0F);
            }
            if (worldYOffset >= 0) {
                // Distant Horizons' meaning: absolute y = vPosition.y + worldYOffset.
                GL20C.glUniform1f(worldYOffset, -NativeLodMesher.Y_BIAS);
            }
            if (clipDistance >= 0) {
                GL20C.glUniform1f(clipDistance, clipRadius);
            }
            if (lodClip >= 0) {
                GL20C.glUniform1f(lodClip, clipRadius);
            }
            if (lodInvViewport >= 0) {
                GL20C.glUniform2f(lodInvViewport, 1.0F / Math.max(1, width), 1.0F / Math.max(1, height));
            }
            samplers.update();
            uniforms.update();
            customUniforms.push(this);
            images.update();
        }

        /** Sets (or resets to GL's default) the generic values of block attributes the LOD vertex does not carry. */
        void setGenericAttributes(boolean set) {
            setAttribute(entityAttribute, set ? ENTITY : null);
            setAttribute(midTexCoordAttribute, set ? MID_TEXCOORD : null);
            setAttribute(tangentAttribute, set ? TANGENT : null);
            setAttribute(midBlockAttribute, set ? MID_BLOCK : null);
        }

        void setModelOffset(float x, float y, float z) {
            if (modelOffset >= 0) {
                GL20C.glUniform3f(modelOffset, x, y, z);
            }
        }

        void unbind() {
            ProgramManager.glUseProgram(0);
            ProgramUniforms.clearActiveUniforms();
            ProgramSamplers.clearActiveSamplers();
            BlendModeOverride.restore();
        }

        void free() {
            GL43C.glDeleteProgram(id);
        }

        private static void setAttribute(int location, @Nullable float[] value) {
            if (location < 0 || location <= 2) {
                // Not active, or one of the LOD vertex's own attributes.
                return;
            }
            if (value == null) {
                GL20C.glVertexAttrib4f(location, 0.0F, 0.0F, 0.0F, 1.0F);
            } else {
                GL20C.glVertexAttrib4f(location, value[0], value[1], value[2], value[3]);
            }
        }

        private static void setMatrix(int location, Matrix4fc matrix) {
            if (location < 0) {
                return;
            }
            try (MemoryStack stack = MemoryStack.stackPush()) {
                FloatBuffer buffer = stack.mallocFloat(16);
                matrix.get(buffer);
                GL20C.glUniformMatrix4fv(location, false, buffer);
            }
        }
    }
}
