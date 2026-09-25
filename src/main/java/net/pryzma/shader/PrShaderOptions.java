package net.pryzma.shader;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * The options of a shader pack: found in the sources of every program of every world folder
 * (includes expanded), set from the user's saved values or a profile, and applied by rewriting
 * their declaring lines. A switch counts only when the same program source tests it; an option
 * declared with two different defaults is ambiguous and left untouched, as in OptiFine.
 */
public final class PrShaderOptions {
    private static final String[] EXTENSIONS = {".csh", ".vsh", ".gsh", ".fsh"};

    private final Map<String, PrShaderOption> options;

    private PrShaderOptions(Map<String, PrShaderOption> options) {
        this.options = options;
    }

    public static PrShaderOptions discover(PrShaderPack pack, List<String> dirs, List<String> programs, Consumer<String> warn) {
        Map<String, PrShaderOption> found = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String dir : dirs) {
            for (String program : programs) {
                for (String ext : EXTENSIONS) {
                    PrGlslPreprocessor.Source source = PrGlslPreprocessor.expand(pack, dir + "/" + program + ext, warn);
                    if (source != null) {
                        collect(source.text(), dir + "/" + program + ext, found, warn);
                    }
                }
            }
        }
        return new PrShaderOptions(Collections.unmodifiableMap(new LinkedHashMap<>(found)));
    }

    /** Options declared in one expanded source. Exposed for tests. */
    static void collect(String text, String path, Map<String, PrShaderOption> found, Consumer<String> warn) {
        String[] lines = text.split("\r?\n");
        for (String line : lines) {
            PrShaderOption option = PrShaderOption.parse(line, path);
            if (option == null || option.name.startsWith("MC_")) {
                continue;
            }
            PrShaderOption known = found.get(option.name);
            if (known != null) {
                if (!known.defaultValue.equals(option.defaultValue) && known.enabled) {
                    warn.accept("Ambiguous shader option " + option.name + ": " + known.defaultValue + " in "
                            + known.paths + ", " + option.defaultValue + " in " + path);
                    known.enabled = false;
                }
                if (known.description.isEmpty()) {
                    known.description = option.description;
                }
                if (!known.paths.contains(path)) {
                    known.paths.add(path);
                }
            } else if (option.kind != PrShaderOption.Kind.SWITCH || tested(option, lines)) {
                found.put(option.name, option);
            }
        }
    }

    private static boolean tested(PrShaderOption option, String[] lines) {
        for (String line : lines) {
            if (option.testedBy(line)) {
                return true;
            }
        }
        return false;
    }

    public static PrShaderOptions empty() {
        return new PrShaderOptions(Map.of());
    }

    /** Options already collected (see {@link #collect}). */
    static PrShaderOptions of(Map<String, PrShaderOption> options) {
        return new PrShaderOptions(options);
    }

    public PrShaderOption get(String name) {
        return options.get(name);
    }

    public Collection<PrShaderOption> all() {
        return options.values();
    }

    /** Sets saved values; unknown names and values not allowed by an option are ignored. */
    public void load(Map<String, String> saved) {
        saved.forEach((name, value) -> {
            PrShaderOption option = options.get(name);
            if (option != null) {
                option.set(value);
            }
        });
    }

    /** The values that differ from the defaults, as saved to {@code <pack>.txt}. */
    public Map<String, String> changed() {
        Map<String, String> out = new TreeMap<>();
        for (PrShaderOption option : options.values()) {
            if (!option.value.equals(option.defaultValue)) {
                out.put(option.name, option.value);
            }
        }
        return out;
    }

    public void resetAll() {
        options.values().forEach(o -> o.value = o.defaultValue);
    }

    /** Rewrites the declaring lines of changed options. */
    public String apply(String source) {
        if (changed().isEmpty()) {
            return source;
        }
        StringBuilder out = new StringBuilder(source.length() + 64);
        for (String line : source.split("\n", -1)) {
            PrShaderOption option = declaring(line);
            out.append(option != null && option.enabled && !option.value.equals(option.defaultValue) ? option.sourceLine() : line);
            out.append('\n');
        }
        out.setLength(out.length() - 1);
        return out.toString();
    }

    private PrShaderOption declaring(String line) {
        if (line.indexOf("#define") < 0 && line.indexOf("const") < 0) {
            return null;
        }
        PrShaderOption parsed = PrShaderOption.parse(line.endsWith("\r") ? line.substring(0, line.length() - 1) : line, "");
        if (parsed == null) {
            return null;
        }
        PrShaderOption option = options.get(parsed.name);
        return option != null && option.kind == parsed.kind ? option : null;
    }

    /**
     * The options as preprocessor macros (for {@code shaders.properties}): switches that are on
     * are defined, value options are defined to their value.
     */
    public Map<String, String> macros() {
        Map<String, String> out = new LinkedHashMap<>();
        for (PrShaderOption option : options.values()) {
            switch (option.kind) {
                case SWITCH -> {
                    if ("true".equals(option.value)) {
                        out.put(option.name, "");
                    }
                }
                case VALUE -> out.put(option.name, option.value);
                default -> {
                }
            }
        }
        return out;
    }

    /**
     * Applies a {@code profile.<name>} definition as OptiFine does: only the options it lists
     * change. {@code NAME} switches on, {@code !NAME} off, {@code NAME=value} sets, and
     * {@code profile.OTHER} includes another profile.
     */
    public void applyProfile(String name, Map<String, String> profiles, Consumer<String> warn) {
        profile(name, profiles).forEach((option, value) -> {
            PrShaderOption o = options.get(option);
            if (o == null || !o.set(value)) {
                warn.accept("Profile " + name + ": cannot set " + option + "=" + value);
            }
        });
    }

    /** The first profile whose listed options all hold now, or {@code null} (a custom setting). */
    public String currentProfile(Map<String, String> profiles) {
        for (String name : profiles.keySet()) {
            boolean holds = true;
            for (Map.Entry<String, String> e : profile(name, profiles).entrySet()) {
                PrShaderOption o = options.get(e.getKey());
                if (o != null && !o.value.equals(e.getValue())) {
                    holds = false;
                    break;
                }
            }
            if (holds) {
                return name;
            }
        }
        return null;
    }

    /** The settings of a profile, included profiles first so the profile's own entries win. */
    static Map<String, String> profile(String name, Map<String, String> profiles) {
        Map<String, String> out = new LinkedHashMap<>();
        flatten(name, profiles, out, new HashSet<>());
        return out;
    }

    private static void flatten(String name, Map<String, String> profiles, Map<String, String> out, Set<String> seen) {
        String definition = profiles.get(name);
        if (definition == null || !seen.add(name)) {
            return;
        }
        for (String token : definition.trim().split("\\s+")) {
            if (token.isEmpty()) {
                continue;
            }
            if (token.startsWith("profile.")) {
                flatten(token.substring("profile.".length()), profiles, out, seen);
                continue;
            }
            int eq = token.indexOf('=');
            if (eq < 0) {
                boolean off = token.startsWith("!");
                out.put(off ? token.substring(1) : token, String.valueOf(!off));
            } else {
                out.put(token.substring(0, eq), token.substring(eq + 1));
            }
        }
    }

    public List<PrShaderOption> visible() {
        List<PrShaderOption> out = new ArrayList<>();
        for (PrShaderOption o : options.values()) {
            if (o.isVisible()) {
                out.add(o);
            }
        }
        return out;
    }
}
