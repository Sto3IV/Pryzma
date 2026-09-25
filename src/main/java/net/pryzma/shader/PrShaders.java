package net.pryzma.shader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.joml.Matrix4f;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.pryzma.Pryzma;

/**
 * Shader pack selection and the render hooks. The choice is kept in OptiFine's
 * {@code optionsshaders.txt} ({@code shaderPack=<name>}, {@code OFF} for Pryzma's own
 * rendering) and each pack's options in {@code shaderpacks/<name>.txt}, so settings carry over
 * from OptiFine. The pipeline is built for the dimension being rendered, on the render thread.
 */
public final class PrShaders {
    public static final String OFF = "OFF";
    private static final String CONFIG = "optionsshaders.txt";
    private static final int MAX_ERRORS = 50;

    private static String selected = OFF;
    private static PrShaderPack pack;
    private static PrShaderOptions options = PrShaderOptions.empty();
    private static PrShaderProperties properties = PrShaderProperties.empty();
    private static PrShaderPipeline pipeline;
    private static ResourceLocation pipelineDimension;
    private static boolean rebuild = true;
    private static final List<String> errors = new ArrayList<>();

    private PrShaders() {
    }

    private static Path gameDir() {
        return Minecraft.getInstance().gameDirectory.toPath();
    }

    public static Path packsDir() {
        return gameDir().resolve("shaderpacks");
    }

    /** Reads the saved selection; called once the game is up. */
    public static void init() {
        Map<String, String> config = readConfig();
        String saved = config.getOrDefault("shaderPack", OFF);
        if (!saved.equals(OFF) && Files.exists(packsDir().resolve(saved))) {
            select(saved, false);
        }
    }

    /** Pack names in {@code shaderpacks/}: folders and zips that hold a {@code shaders/} directory. */
    public static List<String> available() {
        List<String> out = new ArrayList<>();
        Path dir = packsDir();
        try {
            Files.createDirectories(dir);
            try (Stream<Path> files = Files.list(dir)) {
                files.sorted().forEach(p -> {
                    try (PrShaderPack candidate = PrShaderPack.open(p)) {
                        if (candidate != null) {
                            out.add(p.getFileName().toString());
                        }
                    } catch (IOException | RuntimeException e) {
                        Pryzma.LOGGER.debug("Not a shader pack: {}", p, e);
                    }
                });
            }
        } catch (IOException e) {
            Pryzma.LOGGER.warn("Cannot list {}", dir, e);
        }
        return out;
    }

    public static String selected() {
        return selected;
    }

    public static boolean enabled() {
        return pack != null;
    }

    public static PrShaderPack pack() {
        return pack;
    }

    public static PrShaderOptions options() {
        return options;
    }

    public static PrShaderProperties properties() {
        return properties;
    }

    /** Errors of the last load and compilation, newest last. */
    public static List<String> errors() {
        return List.copyOf(errors);
    }

    /** Switches to {@code name} ({@link #OFF} for none) and saves the choice. */
    public static void select(String name) {
        select(name, true);
    }

    private static void select(String name, boolean save) {
        closePack();
        selected = name;
        errors.clear();
        if (!name.equals(OFF)) {
            try {
                pack = PrShaderPack.open(packsDir().resolve(name));
                if (pack == null) {
                    warn().accept("Not a shader pack: " + name);
                    selected = OFF;
                } else {
                    loadPack();
                    List<String> required = properties.requiredIrisFeatures();
                    if (!required.isEmpty()) {
                        // As Iris does with features it lacks: the choice stays, the world renders without the pack.
                        String list = String.join(", ", required);
                        Pryzma.LOGGER.warn("Shaders: {} needs Iris-only features: {}", name, list);
                        errors.add("Needs Iris-only features: " + list);
                        closePack();
                    }
                }
            } catch (IOException | RuntimeException e) {
                warn().accept("Cannot open " + name + ": " + e);
                closePack();
                selected = OFF;
            }
        }
        if (save) {
            Map<String, String> config = readConfig();
            config.put("shaderPack", selected);
            writeConfig(config);
        }
        changed();
    }

    private static void loadPack() {
        List<String> dirs = new ArrayList<>();
        dirs.add("shaders");
        for (String d : pack.directories("shaders")) {
            if (d.startsWith("world")) {
                dirs.add("shaders/" + d);
            }
        }
        // Options declared twice with different defaults are common and harmless (OptiFine leaves them alone too).
        options = PrShaderOptions.discover(pack, dirs, PrShaderPrograms.allNames(), message -> Pryzma.LOGGER.debug("Shaders: {}", message));
        options.load(readProperties(optionsFile()));
        properties = PrShaderProperties.load(pack, macros(), warn());
    }

    /** The standard macros are only known with a GL context; option macros are enough for shaders.properties. */
    private static Map<String, String> macros() {
        Map<String, String> m = new LinkedHashMap<>(options.macros());
        m.put("MC_VERSION", String.valueOf(PrShaderMacros.MC_VERSION));
        return m;
    }

    /** Re-reads options and properties and rebuilds the programs (after an option change). */
    public static void reload() {
        if (pack == null) {
            return;
        }
        errors.clear();
        properties = PrShaderProperties.load(pack, macros(), warn());
        changed();
    }

    public static void saveOptions() {
        if (pack == null) {
            return;
        }
        writeProperties(optionsFile(), options.changed());
    }

    private static Path optionsFile() {
        return packsDir().resolve(selected + ".txt");
    }

    /** Drops the pipeline so the next frame rebuilds it, and switches Fabulous transparency. */
    private static void changed() {
        rebuild = true;
        Minecraft mc = Minecraft.getInstance();
        if (mc.levelRenderer != null && mc.level != null) {
            // Fabulous transparency is off while a pack renders; its targets follow the setting.
            mc.levelRenderer.allChanged();
        }
    }

    private static void closePack() {
        closePipeline();
        if (pack != null) {
            try {
                pack.close();
            } catch (IOException e) {
                Pryzma.LOGGER.debug("Closing shader pack", e);
            }
        }
        pack = null;
        options = PrShaderOptions.empty();
        properties = PrShaderProperties.empty();
    }

    private static void closePipeline() {
        if (pipeline != null) {
            pipeline.close();
        }
        pipeline = null;
        pipelineDimension = null;
    }

    static Consumer<String> warn() {
        return message -> {
            Pryzma.LOGGER.warn("Shaders: {}", message);
            if (errors.size() < MAX_ERRORS) {
                errors.add(message);
            }
        };
    }

    // ------------------------------------------------------------------ render hooks

    /** Whether shader hooks have work to do in this frame. */
    public static boolean rendering() {
        return pipeline != null && pipeline.inWorld();
    }

    /** Start of world rendering: builds the pipeline for this dimension if needed and binds the gbuffers. */
    public static void beginLevel(LevelRenderer levelRenderer, Camera camera, Matrix4f modelView, Matrix4f projection, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (pack == null || mc.level == null) {
            return;
        }
        ResourceLocation dimension = mc.level.dimension().location();
        if (rebuild || !dimension.equals(pipelineDimension)) {
            closePipeline();
            rebuild = false;
            pipelineDimension = dimension;
            try {
                pipeline = PrShaderPipeline.build(pack, options, properties, dimension, warn());
            } catch (RuntimeException e) {
                warn().accept("Cannot build the pipeline: " + e);
                Pryzma.LOGGER.error("Shader pipeline failed", e);
                pipeline = null;
            }
        }
        if (pipeline != null) {
            pipeline.beginLevel(levelRenderer, camera, modelView, projection, partialTick);
        }
    }

    public static ShaderInstance substitute(ShaderInstance shader) {
        return rendering() ? pipeline.substitute(shader) : shader;
    }

    public static void phase(PrShaderPipeline.Phase phase) {
        if (rendering()) {
            pipeline.phase = phase;
        }
    }

    public static PrShaderPipeline.Phase phase() {
        return pipeline == null ? PrShaderPipeline.Phase.NONE : pipeline.phase;
    }

    /**
     * World render stages bound the phases batched draws happen in: entity batches end after the
     * cutout layer's stage and before {@code AFTER_ENTITIES}, block entity batches before
     * {@code AFTER_BLOCK_ENTITIES}.
     */
    public static void stage(RenderLevelStageEvent.Stage stage) {
        if (!rendering() || pipeline.phase == PrShaderPipeline.Phase.SHADOW) {
            return;
        }
        if (stage == RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) {
            pipeline.phase = PrShaderPipeline.Phase.ENTITIES;
        } else if (stage == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            pipeline.phase = PrShaderPipeline.Phase.BLOCK_ENTITIES;
        } else if (stage == RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            pipeline.phase = PrShaderPipeline.Phase.NONE;
        }
    }

    public static void beforeTranslucent() {
        if (rendering()) {
            pipeline.beforeTranslucent();
        }
    }

    public static void beginHand() {
        if (rendering()) {
            pipeline.beginHand();
        }
    }

    public static void finishFrame() {
        if (rendering()) {
            pipeline.finishFrame();
        }
    }

    /** Developer aid: skips the passes after {@code pass} each frame ({@code null} runs all). */
    public static void debugStopAfter(String pass) {
        PrShaderPipeline.debugStopAfter = pass;
    }

    /** True when the gbuffers were bound in place of the game's main framebuffer. */
    public static boolean redirectMainTarget() {
        return rendering() && pipeline.redirectMainTarget();
    }

    // ------------------------------------------------------------------ files

    private static Map<String, String> readConfig() {
        return readProperties(gameDir().resolve(CONFIG));
    }

    private static void writeConfig(Map<String, String> config) {
        writeProperties(gameDir().resolve(CONFIG), config);
    }

    private static Map<String, String> readProperties(Path file) {
        Map<String, String> out = new LinkedHashMap<>();
        if (!Files.exists(file)) {
            return out;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                int eq = line.indexOf('=');
                if (eq > 0 && !line.startsWith("#")) {
                    out.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
                }
            }
        } catch (IOException e) {
            Pryzma.LOGGER.warn("Cannot read {}", file, e);
        }
        return out;
    }

    private static void writeProperties(Path file, Map<String, String> values) {
        StringBuilder sb = new StringBuilder();
        values.forEach((k, v) -> sb.append(k).append('=').append(v).append('\n'));
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Pryzma.LOGGER.warn("Cannot write {}", file, e);
        }
    }
}
