package net.pryzma.shader;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One option of a shader pack, declared by a line of its sources as OptiFine reads them:
 * <ul>
 *   <li>switch: {@code #define NAME} (on) or {@code //#define NAME} (off), counted only when some
 *   {@code #ifdef}/{@code #ifndef} (or {@code defined}) tests it;</li>
 *   <li>value: {@code #define NAME value // [v1 v2 v3] description};</li>
 *   <li>const: {@code const bool|int|float NAME = value;} for the engine constants OptiFine lets
 *   packs expose ({@link #CONST_NAMES}).</li>
 * </ul>
 * Applying an option rewrites its declaring line with the chosen value.
 */
public final class PrShaderOption {
    public enum Kind {
        SWITCH, VALUE, CONST_BOOL, CONST_VALUE
    }

    private static final Pattern SWITCH = Pattern.compile("^\\s*(//)?\\s*#define\\s+([A-Za-z0-9_]+)\\s*(//.*)?$");
    private static final Pattern VALUE = Pattern.compile("^\\s*#define\\s+(\\w+)\\s+(-?[0-9.Ff]+|\\w+)\\s*(//.*)?$");
    private static final Pattern CONST_BOOL = Pattern.compile("^\\s*const\\s*bool\\s*([A-Za-z0-9_]+)\\s*=\\s*(true|false)\\s*;\\s*(//.*)?$");
    private static final Pattern CONST_VALUE = Pattern.compile("^\\s*const\\s*(float|int)\\s*([A-Za-z0-9_]+)\\s*=\\s*(-?[0-9.]+f?F?)\\s*;\\s*(//.*)?$");
    private static final Pattern IFDEF = Pattern.compile("^\\s*#\\s*if(n)?def\\s+([A-Za-z0-9_]+)\\s*$");
    private static final Pattern IF_DEFINED = Pattern.compile("^\\s*#\\s*(?:el)?if\\b.*\\bdefined\\s*\\(?\\s*([A-Za-z0-9_]+)");

    /** Engine constants a pack may expose as options (OptiFine's list). */
    static final Set<String> CONST_NAMES = Set.of("shadowMapResolution", "shadowMapFov", "shadowDistance",
            "shadowDistanceRenderMul", "shadowIntervalSize", "generateShadowMipmap", "generateShadowColorMipmap",
            "shadowHardwareFiltering", "shadowHardwareFiltering0", "shadowHardwareFiltering1", "shadowtex0Mipmap",
            "shadowtexMipmap", "shadowtex1Mipmap", "shadowcolor0Mipmap", "shadowColor0Mipmap", "shadowcolor1Mipmap",
            "shadowColor1Mipmap", "shadowtex0Nearest", "shadowtexNearest", "shadow0MinMagNearest", "shadowtex1Nearest",
            "shadow1MinMagNearest", "shadowcolor0Nearest", "shadowColor0Nearest", "shadowColor0MinMagNearest",
            "shadowcolor1Nearest", "shadowColor1Nearest", "shadowColor1MinMagNearest", "wetnessHalflife",
            "drynessHalflife", "eyeBrightnessHalflife", "centerDepthHalflife", "sunPathRotation",
            "ambientOcclusionLevel", "superSamplingLevel", "noiseTextureResolution");

    final String name;
    final Kind kind;
    final String defaultValue;
    final List<String> values;
    final List<String> paths = new ArrayList<>();
    /** {@code float} or {@code int} for const values. */
    private final String constType;
    String description;
    /** False when two files declare the option with different defaults: it is then left alone. */
    boolean enabled = true;
    String value;

    private PrShaderOption(String name, Kind kind, String defaultValue, List<String> values, String description,
            String constType, String path) {
        this.name = name;
        this.kind = kind;
        this.defaultValue = defaultValue;
        this.values = values;
        this.description = description;
        this.constType = constType;
        this.value = defaultValue;
        this.paths.add(path);
    }

    public String name() {
        return name;
    }

    public Kind kind() {
        return kind;
    }

    public String defaultValue() {
        return defaultValue;
    }

    public List<String> values() {
        return values;
    }

    public String value() {
        return value;
    }

    public String description() {
        return description;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Whether the option has a choice to offer (a value option with a single value is fixed). */
    public boolean isVisible() {
        return enabled && values.size() > 1;
    }

    public boolean isBoolean() {
        return kind == Kind.SWITCH || kind == Kind.CONST_BOOL;
    }

    /** Sets the value if it is one of the allowed ones (any number for a value option without a list). */
    public boolean set(String v) {
        if (values.contains(v) || (kind == Kind.VALUE || kind == Kind.CONST_VALUE) && values.size() == 1 && isNumber(v)) {
            value = v;
            return true;
        }
        return false;
    }

    /** The next (or previous) allowed value, wrapping around. */
    public String cycle(boolean forward) {
        int i = values.indexOf(value);
        int n = values.size();
        return values.get(((i < 0 ? 0 : i) + (forward ? 1 : n - 1)) % n);
    }

    /** Parses an option declaration; {@code null} for any other line. */
    static PrShaderOption parse(String line, String path) {
        Matcher m = SWITCH.matcher(line);
        if (m.matches()) {
            return new PrShaderOption(m.group(2), Kind.SWITCH, m.group(1) == null ? "true" : "false",
                    List.of("false", "true"), comment(m.group(3)), null, path);
        }
        m = VALUE.matcher(line);
        if (m.matches()) {
            String comment = m.group(3);
            return new PrShaderOption(m.group(1), Kind.VALUE, m.group(2), values(m.group(2), comment),
                    comment(stripList(comment)), null, path);
        }
        m = CONST_BOOL.matcher(line);
        if (m.matches() && CONST_NAMES.contains(m.group(1))) {
            return new PrShaderOption(m.group(1), Kind.CONST_BOOL, m.group(2), List.of("false", "true"),
                    comment(m.group(3)), null, path);
        }
        m = CONST_VALUE.matcher(line);
        if (m.matches() && CONST_NAMES.contains(m.group(2))) {
            String comment = m.group(4);
            return new PrShaderOption(m.group(2), Kind.CONST_VALUE, m.group(3), values(m.group(3), comment),
                    comment(stripList(comment)), m.group(1), path);
        }
        return null;
    }

    /** Whether {@code line} declares this option (the line an applied value replaces). */
    boolean declaredBy(String line) {
        Matcher m = switch (kind) {
            case SWITCH -> SWITCH.matcher(line);
            case VALUE -> VALUE.matcher(line);
            case CONST_BOOL -> CONST_BOOL.matcher(line);
            case CONST_VALUE -> CONST_VALUE.matcher(line);
        };
        if (!m.matches()) {
            return false;
        }
        String declared = switch (kind) {
            case SWITCH, CONST_VALUE -> m.group(2);
            case VALUE, CONST_BOOL -> m.group(1);
        };
        return declared.equals(name);
    }

    /** OptiFine counts a switch only when a conditional tests it. */
    boolean testedBy(String line) {
        Matcher m = IFDEF.matcher(line);
        if (m.matches() && m.group(2).equals(name)) {
            return true;
        }
        m = IF_DEFINED.matcher(line);
        while (m.find()) {
            if (m.group(1).equals(name)) {
                return true;
            }
        }
        return false;
    }

    /** The declaration with the current value. */
    String sourceLine() {
        return switch (kind) {
            case SWITCH -> ("true".equals(value) ? "#define " : "//#define ") + name + " // Shader option";
            case VALUE -> "#define " + name + " " + value + " // Shader option";
            case CONST_BOOL -> "const bool " + name + " = " + value + "; // Shader option";
            case CONST_VALUE -> "const " + constType + " " + name + " = " + value + "; // Shader option";
        };
    }

    private static String comment(String c) {
        if (c == null) {
            return "";
        }
        String s = c.trim();
        return s.startsWith("//") ? s.substring(2).trim() : s;
    }

    private static String stripList(String comment) {
        if (comment == null) {
            return null;
        }
        int open = comment.indexOf('[');
        int close = comment.indexOf(']', open + 1);
        return open < 0 || close < 0 ? comment : comment.substring(0, open) + comment.substring(close + 1);
    }

    /** OptiFine {@code parseValues}: the bracketed list, with the default added in front when missing. */
    static List<String> values(String def, String comment) {
        List<String> out = new ArrayList<>();
        if (comment != null) {
            int open = comment.indexOf('[');
            int close = comment.indexOf(']', open + 1);
            if (open >= 0 && close > open) {
                for (String v : comment.substring(open + 1, close).trim().split("\\s+")) {
                    if (!v.isEmpty()) {
                        out.add(v);
                    }
                }
            }
        }
        if (!out.contains(def)) {
            out.add(0, def);
        }
        return List.copyOf(out);
    }

    private static boolean isNumber(String v) {
        try {
            Double.parseDouble(v.replace("f", "").replace("F", ""));
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    public String toString() {
        return name + "=" + value;
    }
}
