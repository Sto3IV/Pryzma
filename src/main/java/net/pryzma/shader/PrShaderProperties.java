package net.pryzma.shader;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import net.minecraft.resources.ResourceLocation;
import net.pryzma.core.expr.PrExpr;
import net.pryzma.core.expr.PrExprEnv;
import net.pryzma.core.expr.PrExprException;
import net.pryzma.core.expr.PrExprParser;
import net.pryzma.core.res.PrProperties;

/**
 * {@code shaders/shaders.properties}, read as OptiFine reads it: first the conditional directives
 * are evaluated against the standard macros and the options' current values, then the remaining
 * lines are parsed as properties.
 */
public final class PrShaderProperties {
    private static final ResourceLocation LOCATION = ResourceLocation.withDefaultNamespace("shaders/shaders.properties");

    private final PrProperties props;

    private PrShaderProperties(PrProperties props) {
        this.props = props;
    }

    public static PrShaderProperties load(PrShaderPack pack, Map<String, String> macros, Consumer<String> warn) {
        String text = pack.read("shaders/shaders.properties");
        return parse(text == null ? "" : text, macros, warn);
    }

    static PrShaderProperties parse(String text, Map<String, String> macros, Consumer<String> warn) {
        String active = PrGlslPreprocessor.conditionals(text, new LinkedHashMap<>(macros), warn);
        return new PrShaderProperties(PrProperties.parse(LOCATION, active));
    }

    public static PrShaderProperties empty() {
        return new PrShaderProperties(PrProperties.parse(LOCATION, ""));
    }

    public String get(String key) {
        return props.get(key);
    }

    public String get(String key, String def) {
        return props.get(key, def);
    }

    public boolean getBool(String key, boolean def) {
        return props.getBool(key, def);
    }

    public Map<String, String> withPrefix(String prefix) {
        return props.withPrefix(prefix);
    }

    /** Every entry, in declaration order. */
    public Map<String, String> entries() {
        return props.asMap();
    }

    public Map<String, String> profiles() {
        return props.withPrefix("profile.");
    }

    /** {@code screen} (key {@code ""}) and {@code screen.<name>} definitions; {@code .columns} keys excluded. */
    public Map<String, String> screens() {
        Map<String, String> out = new LinkedHashMap<>();
        String main = props.get("screen");
        if (main != null) {
            out.put("", main);
        }
        props.withPrefix("screen.").forEach((k, v) -> {
            if (!k.endsWith("columns")) {
                out.put(k, v);
            }
        });
        return out;
    }

    public int columns(String screen) {
        String key = screen.isEmpty() ? "screen.columns" : "screen." + screen + ".columns";
        return Math.max(1, Math.min(6, props.getInt(key, 2)));
    }

    public List<String> sliders() {
        return words("sliders");
    }

    /**
     * {@code iris.features.required}: Iris extensions the pack cannot run without (custom images,
     * SSBOs, compute shaders...). Pryzma implements none of them, so any entry rules the pack out.
     */
    public List<String> requiredIrisFeatures() {
        return words("iris.features.required");
    }

    private List<String> words(String key) {
        List<String> out = new ArrayList<>();
        for (String s : props.get(key, "").trim().split("\\s+")) {
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }

    /**
     * {@code program.<name>.enabled} (also {@code program.<world>/<name>.enabled}): a boolean
     * expression over the pack's switches, {@code true} when absent or malformed.
     */
    public boolean programEnabled(String world, String program, PrShaderOptions options, Consumer<String> warn) {
        String expression = world == null ? null : props.get("program." + world + "/" + program + ".enabled");
        if (expression == null) {
            expression = props.get("program." + program + ".enabled");
        }
        if (expression == null) {
            return true;
        }
        try {
            PrExprParser parser = new PrExprParser(name -> {
                PrShaderOption o = options.get(name);
                boolean value = o != null && "true".equals(o.value());
                return (PrExpr.B) () -> value;
            }, PrExprEnv.minecraft());
            return parser.parseBool(expression).eval();
        } catch (PrExprException | RuntimeException e) {
            warn.accept("Cannot evaluate program." + program + ".enabled=" + expression + ": " + e.getMessage());
            return true;
        }
    }
}
