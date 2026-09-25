package net.pryzma.shader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.pryzma.Pryzma;
import net.pryzma.mixin.LevelRendererShadowAccessor;

/**
 * A shader pack running in one dimension. World rendering goes into the pack's G-buffers: every
 * vanilla shader requested while the world renders is swapped for the pack's gbuffers program of
 * the same purpose (compiled on first use for that shader's vertex format), falling back along
 * OptiFine's chain; shaders the pack does not cover draw as vanilla into {@code colortex0}.
 * {@code deferred} passes run before translucent terrain, {@code composite} and {@code final}
 * after the hand, and {@code final} writes the game's main framebuffer.
 */
public final class PrShaderPipeline implements AutoCloseable {
    /** What the world is drawing, which decides the gbuffers program. */
    public enum Phase {
        NONE, SKY, TERRAIN, ENTITIES, BLOCK_ENTITIES, PARTICLES, WEATHER, CLOUDS, HAND, OUTLINE, WORLD_BORDER, SHADOW
    }

    private static final Object FAILED = new Object();
    private static final Pattern BUFFER_SAMPLER = Pattern.compile("\\b(colortex(?:1[0-5]|[0-9])|gcolor|gdepth|gnormal|gaux[1-4])\\b");
    private static final Pattern OUTPUT_LOCATION = Pattern.compile("layout\\s*\\(\\s*location\\s*=\\s*(\\d+)\\s*\\)\\s*out\\b");
    private static final Pattern FRAG_DATA = Pattern.compile("\\bpr_FragData(\\d)\\b");
    private static int generations;

    final PrShaderPack pack;
    final String folder;
    final ResourceLocation dimension;
    final PrShaderConfig config;
    final PrUniforms uniforms;
    final PrRenderTargets targets;
    private final PrShaderProperties properties;
    private final Map<String, PrShaderPrograms.Source> sources;
    private final Map<String, Object> gbuffers = new HashMap<>();
    /**
     * Developer aid: the pass after which the frame's remaining prepare, deferred and composite
     * passes are skipped (final still runs), so a debugging final pass shows the buffers as that
     * pass left them. {@code null} runs everything.
     */
    static volatile String debugStopAfter;
    private boolean debugStopped;
    private final List<PrPassProgram> prepare = new ArrayList<>();
    private final List<PrPassProgram> deferred = new ArrayList<>();
    private final List<PrPassProgram> composite = new ArrayList<>();
    private PrPassProgram finalPass;
    private final String header;
    private final int generation;
    private final Consumer<String> warn;
    private int flatNormals;
    private int noSpecular;
    private int shadowDepth;
    private int shadowColor;
    /** The shadow map, when the pack has a shadow program. */
    private PrShadowTargets shadow;
    private PrCustomTextures customTextures;

    Phase phase = Phase.NONE;
    private boolean inWorld;
    private boolean deferredDone;
    private boolean translucent;

    private PrShaderPipeline(PrShaderPack pack, String folder, ResourceLocation dimension, PrShaderConfig config,
            PrShaderProperties properties, Map<String, PrShaderPrograms.Source> sources, Consumer<String> warn) {
        this.pack = pack;
        this.folder = folder;
        this.dimension = dimension;
        this.config = config;
        this.properties = properties;
        this.sources = sources;
        this.warn = warn;
        this.generation = ++generations;
        this.uniforms = new PrUniforms(config);
        this.uniforms.addCustom(properties.entries(), warn);
        this.targets = new PrRenderTargets(config, buffersUsed(sources));
        this.header = PrShaderMacros.header();
    }

    /**
     * The pipeline of a pack in a dimension, or {@code null} when the pack has no programs there
     * (the dimension then renders as vanilla).
     */
    static PrShaderPipeline build(PrShaderPack pack, PrShaderOptions options, PrShaderProperties properties,
            ResourceLocation dimension, Consumer<String> warn) {
        String folder = PrShaderPrograms.worldFolder(pack, dimension);
        PrShaderConfig config = new PrShaderConfig();
        config.readSizes(properties.withPrefix("size.buffer."), warn);
        Map<String, PrShaderPrograms.Source> sources = PrShaderPrograms.load(pack, folder, options, properties, config,
                macros(PrShaderMacros.header()), warn);
        if (sources.isEmpty()) {
            return null;
        }
        PrShaderPipeline pipeline = new PrShaderPipeline(pack, folder, dimension, config, properties, sources, warn);
        pipeline.customTextures = PrCustomTextures.load(pack, properties, warn);
        pipeline.compilePasses();
        pipeline.createDefaults();
        if (sources.containsKey("shadow") || sources.containsKey("shadow_solid") || sources.containsKey("shadow_cutout")) {
            pipeline.shadow = new PrShadowTargets(config, formatOr(config.shadowColorFormats[0]), formatOr(config.shadowColorFormats[1]));
        }
        Pryzma.LOGGER.info("Shader pack {} in {}: {} programs from {}, {} deferred, {} composite, final {}", pack.name(), dimension,
                sources.size(), folder == null ? "shaders/" : "shaders/" + folder, pipeline.deferred.size(), pipeline.composite.size(),
                pipeline.finalPass != null);
        return pipeline;
    }

    /** The {@code #define} lines of a macro header as a map. */
    static Map<String, String> macros(String header) {
        Map<String, String> out = new HashMap<>();
        for (String line : header.split("\n")) {
            String[] parts = line.trim().split("\\s+", 3);
            if (parts.length >= 2 && parts[0].equals("#define")) {
                out.put(parts[1], parts.length > 2 ? parts[2] : "");
            }
        }
        return out;
    }

    private static int formatOr(int format) {
        return format != 0 ? format : PrShaderConfig.RGBA8;
    }

    private static boolean[] buffersUsed(Map<String, PrShaderPrograms.Source> sources) {
        boolean[] used = new boolean[PrShaderConfig.BUFFERS];
        for (PrShaderPrograms.Source s : sources.values()) {
            if (s.drawBuffers() != null) {
                for (int b : s.drawBuffers()) {
                    if (b >= 0 && b < used.length) {
                        used[b] = true;
                    }
                }
            }
            for (String text : new String[] {s.vertex(), s.fragment()}) {
                Matcher m = BUFFER_SAMPLER.matcher(text);
                while (m.find()) {
                    int index = bufferIndex(m.group(1));
                    if (index >= 0) {
                        used[index] = true;
                    }
                }
            }
        }
        return used;
    }

    /** A buffer sampler name ({@code colortex4}, {@code gaux1}...) as its index, or -1. */
    static int bufferIndex(String name) {
        return switch (name) {
            case "gcolor" -> 0;
            case "gdepth" -> 1;
            case "gnormal" -> 2;
            case "composite" -> 3;
            case "gaux1" -> 4;
            case "gaux2" -> 5;
            case "gaux3" -> 6;
            case "gaux4" -> 7;
            default -> name.startsWith("colortex") ? parse(name.substring(8)) : -1;
        };
    }

    private static int parse(String digits) {
        try {
            int i = Integer.parseInt(digits);
            return i >= 0 && i < PrShaderConfig.BUFFERS ? i : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void compilePasses() {
        for (String name : PrShaderPrograms.passNames("prepare")) {
            pass(name, prepare);
        }
        for (String name : PrShaderPrograms.passNames("deferred")) {
            pass(name, deferred);
        }
        for (String name : PrShaderPrograms.passNames("composite")) {
            pass(name, composite);
        }
        List<PrPassProgram> fin = new ArrayList<>();
        pass("final", fin);
        finalPass = fin.isEmpty() ? null : fin.get(0);
    }

    private void pass(String name, List<PrPassProgram> into) {
        PrShaderPrograms.Source s = sources.get(name);
        if (s == null) {
            return;
        }
        PrGlslTransformer.Result result = PrGlslTransformer.transform(s.vertex(), s.geometry(), s.fragment(),
                PrGlslTransformer.Inputs.COMPOSITE, header, warn);
        int[] drawBuffers = s.drawBuffers() != null ? s.drawBuffers() : defaultDrawBuffers(result.fragment());
        if (config.mixesSizes(drawBuffers)) {
            warn.accept(name + " draws to buffers of different sizes; it skips the ones with a size.buffer");
        }
        PrPassProgram program = PrPassProgram.create(name, result, drawBuffers, uniforms,
                PrBlend.parse(properties.get("blend." + name), warn), warn);
        if (program != null) {
            Map<String, String> active = macros(header);
            program.mipmaps = PrShaderConfig.mipmapMask(PrShaderPrograms.active(s.vertex(), active))
                    | PrShaderConfig.mipmapMask(PrShaderPrograms.active(s.fragment(), active));
            into.add(program);
        } else {
            dump(name, result);
        }
    }

    /** Writes a translated program that failed to {@code logs/pryzma-shaders/}, to read compiler errors against. */
    static void dump(String name, PrGlslTransformer.Result result) {
        try {
            Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve("logs/pryzma-shaders");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(name + ".vsh"), result.vertex());
            Files.writeString(dir.resolve(name + ".fsh"), result.fragment());
            if (result.geometry() != null) {
                Files.writeString(dir.resolve(name + ".gsh"), result.geometry());
            }
        } catch (IOException e) {
            Pryzma.LOGGER.debug("Cannot dump {}", name, e);
        }
    }

    /** Draw buffers of a program without a directive: one per fragment output it declares. */
    static int[] defaultDrawBuffers(String fragment) {
        int max = 0;
        for (Pattern p : new Pattern[] {OUTPUT_LOCATION, FRAG_DATA}) {
            Matcher m = p.matcher(fragment);
            while (m.find()) {
                max = Math.max(max, Integer.parseInt(m.group(1)));
            }
        }
        int[] out = new int[max + 1];
        for (int i = 0; i <= max; i++) {
            out[i] = i;
        }
        return out;
    }

    /** 1x1 stand-ins: flat normals, no specular, an unshadowed depth map and a white shadow colour. */
    private void createDefaults() {
        flatNormals = solid(0x7F, 0x7F, 0xFF, 0xFF);
        noSpecular = solid(0, 0, 0, 0);
        shadowColor = solid(0xFF, 0xFF, 0xFF, 0xFF);
        shadowDepth = PrRenderTargets.depthTexture(1, 1);
        int fbo = GlStateManager.glGenFramebuffers();
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, shadowDepth, 0);
        GL30.glClearBufferfv(GL11.GL_DEPTH, 0, new float[] {1.0F});
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        GlStateManager._glDeleteFramebuffers(fbo);
    }

    private static int solid(int r, int g, int b, int a) {
        int id = GlStateManager._genTexture();
        GlStateManager._bindTexture(id);
        ByteBuffer pixel = MemoryUtil.memAlloc(4);
        pixel.put((byte) r).put((byte) g).put((byte) b).put((byte) a).flip();
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 1, 1, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
        MemoryUtil.memFree(pixel);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        return id;
    }

    boolean inWorld() {
        return inWorld;
    }

    // ------------------------------------------------------------------ programs for vanilla shaders

    /** The shader to draw with instead of {@code vanilla} while the world renders. */
    ShaderInstance substitute(ShaderInstance vanilla) {
        if (!inWorld || vanilla == null || vanilla instanceof PrGbufferShader) {
            return vanilla;
        }
        String name = vanilla.getName();
        String program = programFor(name);
        String resolved = program == null ? null : PrShaderPrograms.resolve(program, sources);
        if (resolved != null) {
            String key = resolved + "|" + name;
            Object cached = gbuffers.get(key);
            if (cached == null) {
                cached = compile(resolved, name, vanilla.getVertexFormat());
                gbuffers.put(key, cached == null ? FAILED : cached);
            }
            if (cached instanceof PrGbufferShader shader) {
                return shader;
            }
        }
        // Drawn by the vanilla shader: into colortex0 only (shadowcolor0 in the shadow pass).
        if (phase == Phase.SHADOW && shadow != null) {
            shadow.bind(new int[] {0});
        } else {
            targets.bindGbuffers(new int[] {0});
        }
        return vanilla;
    }

    private Object compile(String program, String vanillaName, VertexFormat format) {
        PrShaderPrograms.Source s = sources.get(program);
        PrGlslTransformer.Inputs inputs = new PrGlslTransformer.Inputs(format.contains(VertexFormatElement.COLOR),
                format.contains(VertexFormatElement.UV0), format.contains(VertexFormatElement.UV2),
                format.contains(VertexFormatElement.UV1), format.contains(VertexFormatElement.NORMAL), false);
        PrGlslTransformer.Result result = PrGlslTransformer.transform(s.vertex(), null, s.fragment(), inputs, header, warn);
        if (s.geometry() != null) {
            warn.accept(program + ": geometry shaders are not supported in gbuffers programs, drawing without it");
        }
        int[] drawBuffers = s.drawBuffers() != null ? s.drawBuffers() : defaultDrawBuffers(result.fragment());
        String id = "pryzma:sp" + generation + "/" + program + "/" + vanillaName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_./-]", "_");
        try {
            return PrGbufferShader.create(this, id, result, format, program, drawBuffers, alphaTest(program, vanillaName),
                    stageOf(vanillaName), PrBlend.parse(properties.get("blend." + program), warn));
        } catch (Exception e) {
            warn.accept("Cannot compile " + program + " for " + vanillaName + ": " + e.getMessage());
            dump(program + "." + vanillaName, result);
            return null;
        }
    }

    /** OptiFine's gbuffers program for a vanilla shader in the current phase, or {@code null} to keep vanilla. */
    String programFor(String vanilla) {
        if (vanilla.equals("rendertype_water_mask") || vanilla.startsWith("rendertype_end_") || vanilla.equals("rendertype_outline")
                || vanilla.startsWith("rendertype_gui") || vanilla.equals("blit_screen")) {
            return null;
        }
        boolean translucentType = vanilla.contains("translucent") || vanilla.equals("rendertype_tripwire");
        if (vanilla.contains("glint")) {
            return "gbuffers_armor_glint";
        }
        switch (phase) {
            case SHADOW -> {
                return vanilla.equals("rendertype_solid") ? "shadow_solid" : vanilla.contains("cutout") ? "shadow_cutout" : "shadow";
            }
            case SKY -> {
                return vanilla.contains("tex") ? "gbuffers_skytextured" : "gbuffers_skybasic";
            }
            case CLOUDS -> {
                return "gbuffers_clouds";
            }
            case WEATHER -> {
                return "gbuffers_weather";
            }
            case HAND -> {
                return translucentType ? "gbuffers_hand_water" : "gbuffers_hand";
            }
            case PARTICLES -> {
                return translucentType || translucent ? "gbuffers_particles_translucent" : "gbuffers_particles";
            }
            case OUTLINE -> {
                return "gbuffers_line";
            }
            case WORLD_BORDER -> {
                return "gbuffers_textured";
            }
            default -> {
            }
        }
        return switch (vanilla) {
            case "rendertype_solid" -> "gbuffers_terrain_solid";
            case "rendertype_cutout_mipped" -> "gbuffers_terrain_cutout_mip";
            case "rendertype_cutout" -> "gbuffers_terrain_cutout";
            case "rendertype_translucent", "rendertype_translucent_moving_block" -> "gbuffers_water";
            case "rendertype_tripwire" -> "gbuffers_terrain";
            case "rendertype_crumbling" -> "gbuffers_damagedblock";
            case "rendertype_beacon_beam" -> "gbuffers_beaconbeam";
            case "rendertype_eyes", "rendertype_breeze_wind" -> "gbuffers_spidereyes";
            case "rendertype_lightning" -> "gbuffers_lightning";
            case "rendertype_lines" -> "gbuffers_line";
            case "rendertype_leash" -> "gbuffers_basic";
            case "particle" -> "gbuffers_particles";
            case "position", "position_color" -> "gbuffers_basic";
            case "position_tex", "position_tex_color", "position_color_tex_lightmap", "position_color_lightmap" -> "gbuffers_textured";
            default -> {
                if (vanilla.startsWith("rendertype_text")) {
                    yield "gbuffers_textured";
                }
                if (vanilla.startsWith("rendertype_entity") || vanilla.startsWith("rendertype_armor") || vanilla.startsWith("rendertype_item")
                        || vanilla.equals("rendertype_energy_swirl")) {
                    if (phase == Phase.BLOCK_ENTITIES) {
                        yield translucentType ? "gbuffers_block_translucent" : "gbuffers_block";
                    }
                    yield translucentType ? "gbuffers_entities_translucent" : "gbuffers_entities";
                }
                yield null;
            }
        };
    }

    /** The alpha test of a program: {@code alphaTest.<program>}, else what the vanilla shader discards at. */
    private float alphaTest(String program, String vanilla) {
        String configured = properties.get("alphaTest." + program);
        if (configured != null) {
            String[] parts = configured.trim().split("\\s+");
            if (parts[0].equalsIgnoreCase("off")) {
                return -1.0F;
            }
            try {
                return parts.length > 1 ? Float.parseFloat(parts[1]) : 0.1F;
            } catch (NumberFormatException e) {
                warn.accept("Invalid alphaTest." + program + "=" + configured);
            }
        }
        if (vanilla.equals("rendertype_cutout_mipped")) {
            return 0.5F;
        }
        if (vanilla.contains("cutout") || vanilla.contains("particle") || vanilla.contains("text") || vanilla.contains("eyes")
                || vanilla.startsWith("rendertype_entity_translucent") || vanilla.startsWith("rendertype_item_entity")) {
            return 0.1F;
        }
        return -1.0F;
    }

    private PrShaderMacros.Stage stageOf(String vanilla) {
        return switch (phase) {
            case SKY -> PrShaderMacros.Stage.SKY;
            case CLOUDS -> PrShaderMacros.Stage.CLOUDS;
            case WEATHER -> PrShaderMacros.Stage.RAIN_SNOW;
            case HAND -> vanilla.contains("translucent") ? PrShaderMacros.Stage.HAND_TRANSLUCENT : PrShaderMacros.Stage.HAND_SOLID;
            case PARTICLES -> PrShaderMacros.Stage.PARTICLES;
            case ENTITIES -> PrShaderMacros.Stage.ENTITIES;
            case BLOCK_ENTITIES -> PrShaderMacros.Stage.BLOCK_ENTITIES;
            case OUTLINE -> PrShaderMacros.Stage.OUTLINE;
            case WORLD_BORDER -> PrShaderMacros.Stage.WORLD_BORDER;
            default -> switch (vanilla) {
                case "rendertype_solid" -> PrShaderMacros.Stage.TERRAIN_SOLID;
                case "rendertype_cutout_mipped" -> PrShaderMacros.Stage.TERRAIN_CUTOUT_MIPPED;
                case "rendertype_cutout" -> PrShaderMacros.Stage.TERRAIN_CUTOUT;
                case "rendertype_translucent" -> PrShaderMacros.Stage.TERRAIN_TRANSLUCENT;
                case "rendertype_tripwire" -> PrShaderMacros.Stage.TRIPWIRE;
                default -> PrShaderMacros.Stage.NONE;
            };
        };
    }

    /** Called by a gbuffers program before it draws: its outputs, its render stage and alpha test. */
    void beforeGbufferDraw(PrGbufferShader shader) {
        if (shader.blend != null) {
            PrBlend.apply(shader.blend);
        }
        if (shader.program.startsWith("shadow") && shadow != null) {
            shadow.bind(shader.drawBuffers);
        } else {
            targets.bindGbuffers(shader.drawBuffers);
        }
        uniforms.renderStage = shader.stage.ordinal();
        uniforms.alphaTestRef = shader.alphaTestRef;
    }

    /** The texture a sampler name reads, or -1 when the name means nothing to the pipeline. */
    int samplerTexture(String name, String stage) {
        boolean gbuffers = stage.equals("gbuffers") || stage.equals("shadow");
        int custom = customTextures == null ? -1 : customTextures.lookup(stage, name);
        if (custom > 0) {
            return custom;
        }
        switch (name) {
            // OptiFine leaves gcolor/colortex0 unassigned in gbuffers and shadow programs, so they
            // read unit 0, the draw's own texture; packs use them for albedo there (Iris aliases them too).
            case "gtexture", "texture", "tex", "gcolor", "colortex0" -> {
                return gbuffers ? RenderSystem.getShaderTexture(0) : targets.read(0);
            }
            case "lightmap" -> {
                return RenderSystem.getShaderTexture(2);
            }
            case "normals" -> {
                return flatNormals;
            }
            case "specular" -> {
                return noSpecular;
            }
            case "depthtex0", "gdepthtex" -> {
                return targets.depth0;
            }
            case "depthtex1" -> {
                return targets.depth1;
            }
            case "depthtex2" -> {
                return targets.depth2;
            }
            case "noisetex" -> {
                return targets.noise;
            }
            case "shadow", "watershadow", "shadowtex0" -> {
                return shadow != null ? shadow.depth0 : shadowDepth;
            }
            case "shadowtex1" -> {
                return shadow != null ? shadow.depth1 : shadowDepth;
            }
            case "shadowcolor", "shadowcolor0" -> {
                return shadow != null ? shadow.color0 : shadowColor;
            }
            case "shadowcolor1" -> {
                return shadow != null ? shadow.color1 : shadowColor;
            }
            default -> {
                int buffer = bufferIndex(name);
                return buffer >= 0 && targets.isAllocated(buffer) ? targets.read(buffer) : -1;
            }
        }
    }

    // ------------------------------------------------------------------ frame

    /** Starts the world: the frame's uniforms, cleared buffers, and the gbuffers bound for drawing. */
    void beginLevel(LevelRenderer levelRenderer, Camera camera, Matrix4f modelView, Matrix4f projection, float partialTick) {
        debugStopped = false;
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        targets.ensure(main.width, main.height);
        uniforms.beginFrame(camera, modelView, projection, partialTick, main.width, main.height);
        if (shadow != null) {
            inWorld = true;
            renderShadows(levelRenderer, camera);
        }
        float[] fog = RenderSystem.getShaderFogColor();
        targets.beginFrame(new float[] {fog[0], fog[1], fog[2], 1.0F});
        // Prepare passes: after the shadow pass, before the world.
        runPasses(prepare);
        targets.bindGbuffers(new int[] {0});
        inWorld = true;
        deferredDone = false;
        translucent = false;
        phase = Phase.NONE;
    }

    /**
     * The shadow pass: the terrain of the sections in view, drawn again from the light through
     * the pack's shadow programs; {@code shadowtex1} keeps the depth before translucent terrain.
     */
    private void renderShadows(LevelRenderer levelRenderer, Camera camera) {
        LevelRendererShadowAccessor renderer = (LevelRendererShadowAccessor) levelRenderer;
        Vec3 cam = camera.getPosition();
        Matrix4f modelView = new Matrix4f(uniforms.shadowModelView);
        Matrix4f projection = new Matrix4f(uniforms.shadowProjection);
        phase = Phase.SHADOW;
        try {
            shadow.clear();
            renderer.prRenderSectionLayer(RenderType.solid(), cam.x, cam.y, cam.z, modelView, projection);
            renderer.prRenderSectionLayer(RenderType.cutoutMipped(), cam.x, cam.y, cam.z, modelView, projection);
            renderer.prRenderSectionLayer(RenderType.cutout(), cam.x, cam.y, cam.z, modelView, projection);
            shadow.copyOpaqueDepth();
            renderer.prRenderSectionLayer(RenderType.translucent(), cam.x, cam.y, cam.z, modelView, projection);
        } finally {
            phase = Phase.NONE;
        }
    }

    /** Before translucent terrain: the depth without translucents, then the deferred passes. */
    void beforeTranslucent() {
        if (!inWorld || deferredDone || phase == Phase.SHADOW) {
            return;
        }
        deferredDone = true;
        translucent = true;
        targets.copyDepth(targets.depth1);
        if (!deferred.isEmpty()) {
            runPasses(deferred);
            targets.bindGbuffers(new int[] {0});
        }
    }

    /** Before the hand: the depth without it. */
    void beginHand() {
        if (!inWorld) {
            return;
        }
        beforeTranslucent();
        targets.copyDepth(targets.depth2);
        targets.bindGbuffers(new int[] {0});
        phase = Phase.HAND;
    }

    /** After the hand: composite passes and the final pass into the game's framebuffer. */
    void finishFrame() {
        if (!inWorld) {
            return;
        }
        beforeTranslucent();
        phase = Phase.NONE;
        runPasses(composite);
        inWorld = false;
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (finalPass != null) {
            targets.generateMipmaps(finalPass.mipmaps);
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, main.frameBufferId);
            GlStateManager._viewport(0, 0, main.width, main.height);
            passState();
            finalPass.draw(this);
            worldState();
        } else {
            targets.blitColor0(main.frameBufferId, main.width, main.height);
        }
        main.bindWrite(true);
    }

    /** Binds the gbuffers where vanilla code binds the main framebuffer while the world renders. */
    boolean redirectMainTarget() {
        if (!inWorld) {
            return false;
        }
        targets.bindGbuffers(new int[] {0});
        return true;
    }

    private void runPasses(List<PrPassProgram> passes) {
        if (passes.isEmpty()) {
            return;
        }
        passState();
        for (PrPassProgram pass : passes) {
            if (debugStopped) {
                break;
            }
            debugStopped = pass.name.equals(debugStopAfter);
            targets.generateMipmaps(pass.mipmaps);
            targets.bindPass(pass.drawBuffers);
            PrBlend.apply(pass.blend);
            pass.draw(this);
            PrBlend.apply(null);
            for (int buffer : pass.drawBuffers) {
                if (buffer >= 0 && targets.isAllocated(buffer)) {
                    targets.swap(buffer);
                }
            }
        }
        worldState();
    }

    private static void passState() {
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
    }

    private static void worldState() {
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
    }

    @Override
    public void close() {
        for (Object o : gbuffers.values()) {
            if (o instanceof PrGbufferShader shader) {
                shader.close();
            }
        }
        gbuffers.clear();
        prepare.forEach(PrPassProgram::close);
        deferred.forEach(PrPassProgram::close);
        composite.forEach(PrPassProgram::close);
        if (finalPass != null) {
            finalPass.close();
        }
        for (int t : new int[] {flatNormals, noSpecular, shadowDepth, shadowColor}) {
            if (t != 0) {
                GlStateManager._deleteTexture(t);
            }
        }
        targets.release();
        if (customTextures != null) {
            customTextures.release();
        }
        if (shadow != null) {
            shadow.release();
        }
        uniforms.close();
        inWorld = false;
    }
}
