package net.pryzma.shader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import net.minecraft.resources.ResourceLocation;
import net.pryzma.core.res.PrProperties;

/**
 * The programs of a shader pack for one dimension: which files make each program, with
 * includes expanded and options applied. A program missing from the pack falls back along
 * OptiFine's chain ({@code gbuffers_terrain} → {@code gbuffers_textured_lit} →
 * {@code gbuffers_textured} → {@code gbuffers_basic}); a dimension folder
 * ({@code world0}, {@code world-1}, {@code world1}, or one named in Iris's
 * {@code dimension.properties}) overrides the root program by program.
 */
public final class PrShaderPrograms {
    /** Gbuffers programs and the program each falls back to ({@code null}: none). */
    public static final Map<String, String> GBUFFERS = gbuffers();

    /** A program's sources, ready for translation. */
    public record Source(String name, String folder, String vertex, String geometry, String fragment, int[] drawBuffers) {
    }

    private PrShaderPrograms() {
    }

    private static Map<String, String> gbuffers() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("gbuffers_basic", null);
        m.put("gbuffers_line", "gbuffers_basic");
        m.put("gbuffers_textured", "gbuffers_basic");
        m.put("gbuffers_textured_lit", "gbuffers_textured");
        m.put("gbuffers_skybasic", "gbuffers_basic");
        m.put("gbuffers_skytextured", "gbuffers_textured");
        m.put("gbuffers_clouds", "gbuffers_textured");
        m.put("gbuffers_terrain", "gbuffers_textured_lit");
        m.put("gbuffers_terrain_solid", "gbuffers_terrain");
        m.put("gbuffers_terrain_cutout_mip", "gbuffers_terrain");
        m.put("gbuffers_terrain_cutout", "gbuffers_terrain");
        m.put("gbuffers_damagedblock", "gbuffers_terrain");
        m.put("gbuffers_block", "gbuffers_terrain");
        m.put("gbuffers_block_translucent", "gbuffers_block");
        m.put("gbuffers_beaconbeam", "gbuffers_textured");
        m.put("gbuffers_item", "gbuffers_textured_lit");
        m.put("gbuffers_entities", "gbuffers_textured_lit");
        m.put("gbuffers_entities_translucent", "gbuffers_entities");
        m.put("gbuffers_entities_glowing", "gbuffers_entities");
        m.put("gbuffers_lightning", "gbuffers_entities");
        m.put("gbuffers_armor_glint", "gbuffers_textured");
        m.put("gbuffers_spidereyes", "gbuffers_textured");
        m.put("gbuffers_hand", "gbuffers_textured_lit");
        m.put("gbuffers_hand_water", "gbuffers_hand");
        m.put("gbuffers_weather", "gbuffers_textured_lit");
        m.put("gbuffers_particles", "gbuffers_textured_lit");
        m.put("gbuffers_particles_translucent", "gbuffers_particles");
        m.put("gbuffers_water", "gbuffers_terrain");
        m.put("shadow", null);
        m.put("shadow_solid", "shadow");
        m.put("shadow_cutout", "shadow");
        // Declaration order is load order: keep it.
        return Collections.unmodifiableMap(m);
    }

    /** Every program name a pack may define, for option discovery. */
    public static List<String> allNames() {
        List<String> out = new ArrayList<>(GBUFFERS.keySet());
        for (String prefix : new String[] {"shadowcomp", "prepare", "deferred", "composite"}) {
            out.addAll(passNames(prefix));
        }
        out.add("final");
        return out;
    }

    /** {@code composite}, {@code composite1} ... {@code composite99}. */
    public static List<String> passNames(String prefix) {
        List<String> out = new ArrayList<>(100);
        out.add(prefix);
        for (int i = 1; i < 100; i++) {
            out.add(prefix + i);
        }
        return out;
    }

    /** The program that draws for {@code name}: itself when present, else the first present fallback. */
    public static String resolve(String name, Map<String, Source> present) {
        for (String n = name; n != null; n = GBUFFERS.get(n)) {
            if (present.containsKey(n)) {
                return n;
            }
        }
        return null;
    }

    /**
     * The folder of the dimension's programs ({@code shaders/world0}...), or {@code null} for the
     * pack root. Iris's {@code dimension.properties} maps dimension ids (or {@code *}) to folders;
     * otherwise OptiFine's numbering applies.
     */
    public static String worldFolder(PrShaderPack pack, ResourceLocation dimension) {
        String text = pack.read("shaders/dimension.properties");
        String folder = null;
        if (text != null) {
            PrProperties props = PrProperties.parse(ResourceLocation.withDefaultNamespace("shaders/dimension.properties"), text);
            String fallback = null;
            for (Map.Entry<String, String> e : props.withPrefix("dimension.").entrySet()) {
                for (String id : e.getValue().trim().split("\\s+")) {
                    if (id.equals("*")) {
                        fallback = e.getKey();
                    } else if (dimension.equals(ResourceLocation.tryParse(id))) {
                        folder = e.getKey();
                    }
                }
            }
            if (folder == null) {
                folder = fallback;
            }
        } else if (dimension.getNamespace().equals("minecraft")) {
            folder = switch (dimension.getPath()) {
                case "overworld" -> "world0";
                case "the_nether" -> "world-1";
                case "the_end" -> "world1";
                default -> null;
            };
        }
        return folder != null && pack.isDirectory("shaders/" + folder) ? folder : null;
    }

    /**
     * Loads every program present for a dimension, with includes expanded and options applied,
     * skipping programs a {@code program.<name>.enabled} condition turns off. Constants go to
     * {@code config}.
     */
    public static Map<String, Source> load(PrShaderPack pack, String folder, PrShaderOptions options,
            PrShaderProperties properties, PrShaderConfig config, Consumer<String> warn) {
        return load(pack, folder, options, properties, config, Map.of(), warn);
    }

    /**
     * As above; {@code macros} are the standard macros, which decide the active {@code #if}
     * branches: like OptiFine, draw buffer directives and constants count only there.
     */
    public static Map<String, Source> load(PrShaderPack pack, String folder, PrShaderOptions options,
            PrShaderProperties properties, PrShaderConfig config, Map<String, String> macros, Consumer<String> warn) {
        Map<String, Source> out = new LinkedHashMap<>();
        for (String name : allNames()) {
            String dir = folder != null && (pack.exists("shaders/" + folder + "/" + name + ".fsh")
                    || pack.exists("shaders/" + folder + "/" + name + ".vsh")) ? "shaders/" + folder : "shaders";
            String vertex = read(pack, dir + "/" + name + ".vsh", options, warn);
            String fragment = read(pack, dir + "/" + name + ".fsh", options, warn);
            if (vertex == null || fragment == null) {
                continue;
            }
            if (!properties.programEnabled(folder, name, options, warn)) {
                continue;
            }
            String geometry = read(pack, dir + "/" + name + ".gsh", options, warn);
            boolean composite = !GBUFFERS.containsKey(name);
            config.scan(active(vertex, macros), composite);
            String activeFragment = active(fragment, macros);
            config.scan(activeFragment, composite);
            if (geometry != null) {
                config.scan(active(geometry, macros), composite);
            }
            out.put(name, new Source(name, dir, vertex, geometry, fragment, PrShaderConfig.drawBuffers(activeFragment)));
        }
        return out;
    }

    /** The lines of a source that its {@code #if} branches keep, with {@code macros} predefined. */
    static String active(String source, Map<String, String> macros) {
        return PrGlslPreprocessor.conditionals(source, new HashMap<>(macros), message -> { });
    }

    private static String read(PrShaderPack pack, String path, PrShaderOptions options, Consumer<String> warn) {
        PrGlslPreprocessor.Source source = PrGlslPreprocessor.expand(pack, path, warn);
        return source == null ? null : options.apply(source.text());
    }
}
