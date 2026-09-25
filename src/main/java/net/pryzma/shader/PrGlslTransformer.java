package net.pryzma.shader;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Translates shader pack GLSL into the core profile GLSL the game can compile. Packs are written
 * for the compatibility profile ({@code #version 120} with {@code gl_Vertex}, {@code ftransform()},
 * {@code gl_FragData}, {@code texture2D}...) or for OptiFine's core profile names
 * ({@code vaPosition}, {@code modelViewMatrix}, {@code chunkOffset}...). Both are rewritten to the
 * game's own vertex attributes and uniforms ({@code Position}, {@code UV2}, {@code ModelViewMat},
 * {@code ChunkOffset}...), so a translated program binds like a vanilla shader.
 *
 * <p>The rewrite works on tokens, so comments and identifier boundaries are exact. Line numbers
 * are kept: the header ends with {@code #line 1 0} and removed directives become blank lines.
 * Attribute semantics follow Iris, which current packs target: {@code gl_MultiTexCoord1} and
 * {@code gl_MultiTexCoord2} are both the lightmap, {@code gl_Color} includes the colour modulator.
 */
public final class PrGlslTransformer {
    public enum Stage {
        VERTEX, GEOMETRY, FRAGMENT
    }

    /** What the vertex data of a program provides; composites draw a textured screen quad. */
    public record Inputs(boolean color, boolean tex, boolean light, boolean overlay, boolean normal, boolean composite) {
        public static final Inputs COMPOSITE = new Inputs(false, true, false, false, false, true);
        public static final Inputs ALL = new Inputs(true, true, true, true, true, false);
    }

    /** The translated stages; {@code geometry} is {@code null} when the program has none. */
    public record Result(String vertex, String geometry, String fragment) {
    }

    private static final Pattern VERSION = Pattern.compile("^\\s*#\\s*version\\s+(\\d+)\\s*(\\w+)?.*$");
    private static final Pattern EXTENSION = Pattern.compile("^\\s*#\\s*extension\\s+(\\w+)\\s*:\\s*(\\w+).*$");
    private static final int TARGET_VERSION = 330;
    /** Terrain attributes OptiFine and Iris add to the vertex data, which the game's vertex formats lack. */
    private static final Pattern EXTENDED_ATTRIBUTE = Pattern.compile(
            "^\\s*(?:attribute|in)\\s+(\\w+)\\s+(at_tangent|mc_midTexCoord|mc_Entity|at_midBlock|at_velocity)\\s*;.*$");
    private static final String STAND_IN_MARKER = "PR_STAND_IN_";
    private static final Pattern SAMPLER_DECLARATION = Pattern.compile(
            "\\buniform\\s+(?:(?:lowp|mediump|highp)\\s+)?[iu]?sampler\\w*\\s+([^;{}()]+);");
    private static final Pattern LEADING_NAME = Pattern.compile("^\\s*([A-Za-z_]\\w*)");

    /** OptiFine core profile names and the game's names for the same data. */
    private static final Map<String, String> CORE_NAMES = Map.ofEntries(
            Map.entry("vaPosition", "Position"), Map.entry("vaColor", "Color"), Map.entry("vaUV0", "UV0"),
            Map.entry("vaUV1", "UV1"), Map.entry("vaUV2", "UV2"), Map.entry("vaNormal", "Normal"),
            Map.entry("modelViewMatrix", "ModelViewMat"), Map.entry("projectionMatrix", "ProjMat"),
            Map.entry("chunkOffset", "ChunkOffset"), Map.entry("colorModulator", "ColorModulator"),
            Map.entry("textureMatrix", "pr_TextureMat"), Map.entry("normalMatrix", "pr_NormalMatrix"),
            Map.entry("modelViewMatrixInverse", "pr_ModelViewMatInverse"),
            Map.entry("projectionMatrixInverse", "pr_ProjMatInverse"));

    private static final Map<String, String> FUNCTIONS = Map.ofEntries(
            Map.entry("texture2D", "texture"), Map.entry("texture2DLod", "textureLod"),
            Map.entry("texture2DGrad", "textureGrad"), Map.entry("texture2DGradARB", "textureGrad"),
            Map.entry("texture2DProj", "textureProj"), Map.entry("texture2DProjLod", "textureProjLod"),
            Map.entry("texture3D", "texture"), Map.entry("texture3DLod", "textureLod"),
            Map.entry("textureCube", "texture"), Map.entry("textureCubeLod", "textureLod"),
            Map.entry("texture1D", "texture"), Map.entry("texelFetch2D", "texelFetch"),
            Map.entry("texelFetch3D", "texelFetch"), Map.entry("textureSize2D", "textureSize"),
            Map.entry("shadow2D", "pr_shadow2D"), Map.entry("shadow2DLod", "pr_shadow2DLod"));

    /** Words that became reserved after GLSL 1.20 and old packs still use as names. */
    private static final Set<String> RESERVED_LATER = Set.of("common", "smooth", "sample", "filter", "input", "output",
            "patch", "buffer", "shared", "precise", "resource", "subroutine", "active", "partition");
    private static final Set<String> QUALIFIERS = Set.of("varying", "in", "out", "centroid", "attribute", "flat", "uniform");

    private static final String LIGHTMAP_MATRIX = "mat4(vec4(0.00390625, 0.0, 0.0, 0.0), vec4(0.0, 0.00390625, 0.0, 0.0), "
            + "vec4(0.0, 0.0, 0.00390625, 0.0), vec4(0.03125, 0.03125, 0.03125, 1.0))";

    private PrGlslTransformer() {
    }

    /**
     * Translates one program. {@code header} is inserted after the version and extensions
     * (the standard {@code MC_} macros).
     */
    public static Result transform(String vertex, String geometry, String fragment, Inputs inputs, String header,
            Consumer<String> warn) {
        Unit fs = new Unit(fragment, Stage.FRAGMENT, inputs);
        Unit gs = geometry == null ? null : new Unit(geometry, Stage.GEOMETRY, inputs);
        Unit vs = new Unit(vertex, Stage.VERTEX, inputs);
        for (Unit unit : gs == null ? List.of(vs, fs) : List.of(vs, gs, fs)) {
            unit.rewrite(warn);
        }
        // The fragment stage reads the legacy varyings; the vertex stage must declare them even if it never writes them.
        vs.varyings.addAll(fs.varyings);
        int version = Math.max(vs.version, Math.max(fs.version, gs == null ? 0 : gs.version));
        return new Result(vs.emit(version, header), gs == null ? null : gs.emit(version, header), fs.emit(version, header));
    }

    // ------------------------------------------------------------------ one stage

    private static final class Unit {
        final Stage stage;
        final Inputs inputs;
        final List<String> lines = new ArrayList<>();
        final List<String> extensions = new ArrayList<>();
        int version = 110;
        String body;
        /** Legacy varyings in use: FrontColor, TexCoord, FogFragCoord. */
        final Set<String> varyings = new TreeSet<>();
        /** Legacy fragment outputs in use, by index. */
        final Set<Integer> fragData = new TreeSet<>();
        boolean usesShadowHelpers;
        /** Extended attributes the vertex stage declares, by name, with their types: computed in main instead. */
        final java.util.Map<String, String> standIns = new java.util.LinkedHashMap<>();

        Unit(String source, Stage stage, Inputs inputs) {
            this.stage = stage;
            this.inputs = inputs;
            boolean versionSeen = false;
            for (String line : source.split("\r?\n", -1)) {
                Matcher v = VERSION.matcher(line);
                if (v.matches() && !versionSeen) {
                    version = Integer.parseInt(v.group(1));
                    versionSeen = true;
                    lines.add("");
                    continue;
                }
                Matcher x = EXTENDED_ATTRIBUTE.matcher(line);
                if (stage == Stage.VERTEX && x.matches()) {
                    // Declared as a plain global up front and filled at the start of main (see standIn).
                    // The line may sit in an #if branch the driver drops, so it leaves a marker in
                    // place and main fills the global only when the marker survives.
                    standIns.put(x.group(2), x.group(1));
                    lines.add("#define " + STAND_IN_MARKER + x.group(2));
                    continue;
                }
                Matcher e = EXTENSION.matcher(line);
                if (e.matches()) {
                    // A required extension the core context lacks would fail the compile; OptiFine enables instead.
                    extensions.add("#extension " + e.group(1) + " : " + ("require".equals(e.group(2)) ? "enable" : e.group(2)));
                    lines.add("");
                    continue;
                }
                lines.add(line);
            }
        }

        void rewrite(Consumer<String> warn) {
            List<Token> tokens = tokenize(String.join("\n", lines));
            boolean textureSampler = declaresTextureSampler(tokens);
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < tokens.size(); i++) {
                Token t = tokens.get(i);
                if (t.kind == Kind.NUMBER) {
                    out.append(number(t.text));
                    continue;
                }
                if (t.kind != Kind.IDENT) {
                    out.append(t.text);
                    continue;
                }
                String name = t.text;
                int next = nextSignificant(tokens, i + 1);
                String nextText = next < tokens.size() ? tokens.get(next).text : "";
                switch (name) {
                    case "attribute" -> out.append(stage == Stage.VERTEX ? "in" : name);
                    case "varying" -> {
                        if (stage == Stage.GEOMETRY && (nextText.equals("in") || nextText.equals("out"))) {
                            continue;
                        }
                        out.append(stage == Stage.VERTEX ? "out" : "in");
                    }
                    case "ftransform" -> {
                        int open = next;
                        int close = nextSignificant(tokens, open + 1);
                        if (nextText.equals("(") && close < tokens.size() && tokens.get(close).text.equals(")")) {
                            out.append("(ProjMat * ModelViewMat * ").append(vertexPosition()).append(')');
                            i = close;
                        } else {
                            out.append(name);
                        }
                    }
                    case "gl_FragData" -> {
                        int index = arrayIndex(tokens, next);
                        if (index >= 0) {
                            fragData.add(index);
                            out.append("pr_FragData").append(index);
                            i = nextSignificant(tokens, nextSignificant(tokens, next + 1) + 1);
                        } else {
                            warn.accept("gl_FragData with a non-constant index is not supported");
                            out.append(name);
                        }
                    }
                    case "gl_TextureMatrix" -> {
                        int index = arrayIndex(tokens, next);
                        if (index >= 0) {
                            out.append(index == 0 ? "pr_TextureMat" : "pr_LightmapMat");
                            i = nextSignificant(tokens, nextSignificant(tokens, next + 1) + 1);
                        } else {
                            out.append(name);
                        }
                    }
                    case "gl_Fog" -> {
                        int member = nextSignificant(tokens, next + 1);
                        String field = nextText.equals(".") && member < tokens.size() ? tokens.get(member).text : "";
                        String replacement = switch (field) {
                            case "color" -> "FogColor";
                            case "start" -> "FogStart";
                            case "end" -> "FogEnd";
                            case "density" -> "pr_FogDensity";
                            case "scale" -> "(1.0 / (FogEnd - FogStart))";
                            default -> null;
                        };
                        if (replacement == null) {
                            out.append(name);
                        } else {
                            out.append(replacement);
                            i = member;
                        }
                    }
                    case "main" -> {
                        boolean definition = i > 0 && previousIs(tokens, i, "void") && nextText.equals("(");
                        out.append(!definition ? name : stage == Stage.FRAGMENT ? "pr_main"
                                : stage == Stage.VERTEX && !standIns.isEmpty() ? "pr_vmain" : name);
                    }
                    default -> out.append(rename(name, nextText, textureSampler));
                }
            }
            body = out.toString();
            if (stage == Stage.VERTEX && !standIns.isEmpty()) {
                StringBuilder main = new StringBuilder("\nvoid main() {\n");
                standIns.forEach((name, type) -> main.append("#ifdef ").append(STAND_IN_MARKER).append(name).append("\n    ")
                        .append(name).append(" = ").append(standIn(name, type)).append(";\n#endif\n"));
                body = body + main.append("    pr_vmain();\n}\n");
            }
            if (stage == Stage.FRAGMENT && fragData.contains(0) && !body.contains("pr_main")) {
                warn.accept("Fragment shader without main()");
            }
        }

        /**
         * The value of an extended attribute the game does not provide: the tangent of the face
         * (exact for block faces, from the normal and the game's texture layout per face), the
         * vertex's own texture coordinate for the sprite centre, and zero for block ids.
         */
        private String standIn(String name, String type) {
            return switch (name) {
                case "at_tangent" -> {
                    String t = inputs.normal ? "pr_tangentOf(Normal)" : "vec4(1.0, 0.0, 0.0, 1.0)";
                    yield type.equals("vec4") ? t : type.equals("vec3") ? t + ".xyz" : type + "(" + t + ")";
                }
                case "mc_midTexCoord" -> {
                    String uv = inputs.tex ? "UV0" : "vec2(0.5)";
                    yield type.equals("vec2") ? uv : type.equals("vec3") ? "vec3(" + uv + ", 0.0)"
                            : type.equals("vec4") ? "vec4(" + uv + ", 0.0, 1.0)" : type + "(0)";
                }
                default -> type + "(0)";
            };
        }

        private String vertexPosition() {
            return inputs.composite ? "vec4(Position, 1.0)" : "vec4(Position + ChunkOffset, 1.0)";
        }

        private String rename(String name, String next, boolean textureSampler) {
            String core = CORE_NAMES.get(name);
            if (core != null) {
                return core;
            }
            String function = FUNCTIONS.get(name);
            if (function != null && next.equals("(")) {
                usesShadowHelpers |= function.startsWith("pr_shadow");
                return function;
            }
            if (name.equals("texture") && textureSampler && !next.equals("(")) {
                return "gtexture";
            }
            if (version <= 120 && RESERVED_LATER.contains(name) && !QUALIFIERS.contains(next)) {
                return name + "_pr";
            }
            if (!name.startsWith("gl_")) {
                return name;
            }
            return switch (name) {
                case "gl_ModelViewMatrix" -> "ModelViewMat";
                case "gl_ProjectionMatrix" -> "ProjMat";
                case "gl_ModelViewProjectionMatrix" -> "(ProjMat * ModelViewMat)";
                case "gl_NormalMatrix" -> "pr_NormalMatrix";
                case "gl_ModelViewMatrixInverse" -> "pr_ModelViewMatInverse";
                case "gl_ProjectionMatrixInverse" -> "pr_ProjMatInverse";
                case "gl_FragColor" -> {
                    fragData.add(0);
                    yield "pr_FragData0";
                }
                case "gl_FogFragCoord" -> varying("pr_FogFragCoord");
                case "gl_TexCoord" -> varying("pr_TexCoord");
                case "gl_FrontColor", "gl_BackColor", "gl_FrontSecondaryColor", "gl_BackSecondaryColor" ->
                        stage == Stage.VERTEX ? varying("pr_FrontColor") : name;
                case "gl_Color" -> stage == Stage.VERTEX ? vertexColor() : varying("pr_FrontColor");
                case "gl_SecondaryColor" -> "vec4(0.0)";
                case "gl_Vertex" -> stage == Stage.VERTEX ? vertexPosition() : name;
                case "gl_Normal" -> inputs.normal ? "Normal" : "vec3(0.0, 0.0, 1.0)";
                case "gl_MultiTexCoord0" -> inputs.tex ? "vec4(UV0, 0.0, 1.0)" : "vec4(0.5, 0.5, 0.0, 1.0)";
                case "gl_MultiTexCoord1", "gl_MultiTexCoord2" ->
                        inputs.light ? "vec4(vec2(UV2), 0.0, 1.0)" : "vec4(240.0, 240.0, 0.0, 1.0)";
                case "gl_MultiTexCoord3", "gl_MultiTexCoord4", "gl_MultiTexCoord5", "gl_MultiTexCoord6",
                        "gl_MultiTexCoord7" -> "vec4(0.0, 0.0, 0.0, 1.0)";
                default -> name;
            };
        }

        private String vertexColor() {
            if (inputs.composite) {
                return "vec4(1.0)";
            }
            return inputs.color ? "(Color * ColorModulator)" : "ColorModulator";
        }

        private String varying(String name) {
            varyings.add(name);
            return name;
        }

        /** The translated source: version, extensions, header, declarations, helpers, then the body at its own line numbers. */
        String emit(int programVersion, String header) {
            int v = Math.max(TARGET_VERSION, programVersion);
            StringBuilder out = new StringBuilder(body.length() + 2048);
            out.append("#version ").append(v).append(" core\n");
            for (String ext : extensions) {
                out.append(ext).append('\n');
            }
            out.append("#define PRYZMA 1\n");
            if (header != null && !header.isEmpty()) {
                out.append(header);
                if (!header.endsWith("\n")) {
                    out.append('\n');
                }
            }
            declare(out);
            out.append("#line 1 0\n");
            out.append(body);
            if (stage == Stage.FRAGMENT && body.contains("pr_main")) {
                out.append("\nvoid main() {\n    pr_main();\n");
                if (fragData.contains(0) && !inputs.composite) {
                    // The fixed-function alpha test legacy packs relied on (OptiFine adds the same).
                    out.append("    if (pr_FragData0.a < pr_AlphaTestRef) discard;\n");
                }
                out.append("}\n");
            }
            return out.toString();
        }

        private void declare(StringBuilder out) {
            String text = body;
            if (stage == Stage.VERTEX) {
                attribute(out, text, "vec3", "Position");
                attribute(out, text, "vec4", "Color");
                attribute(out, text, "vec2", "UV0");
                attribute(out, text, "ivec2", "UV1");
                attribute(out, text, "ivec2", "UV2");
                attribute(out, text, "vec3", "Normal");
                standIns.forEach((name, type) -> out.append(type).append(' ').append(name).append(";\n"));
            }
            uniform(out, text, "mat4", "ModelViewMat");
            uniform(out, text, "mat4", "ProjMat");
            uniform(out, text, "vec3", "ChunkOffset");
            uniform(out, text, "vec4", "ColorModulator");
            uniform(out, text, "vec4", "FogColor");
            uniform(out, text, "float", "FogStart");
            uniform(out, text, "float", "FogEnd");
            uniform(out, text, "float", "pr_FogDensity");
            uniform(out, text, "mat3", "pr_NormalMatrix");
            uniform(out, text, "mat4", "pr_ModelViewMatInverse");
            uniform(out, text, "mat4", "pr_ProjMatInverse");
            uniform(out, text, "mat4", "pr_TextureMat");
            if (uses(text, "pr_LightmapMat") && !declared(text, "pr_LightmapMat")) {
                out.append("const mat4 pr_LightmapMat = ").append(LIGHTMAP_MATRIX).append(";\n");
            }
            String direction = stage == Stage.VERTEX ? "out" : "in";
            if (stage != Stage.GEOMETRY) {
                if (varyings.contains("pr_FrontColor") && !declared(text, "pr_FrontColor")) {
                    out.append(direction).append(" vec4 pr_FrontColor;\n");
                }
                if (varyings.contains("pr_TexCoord") && !declared(text, "pr_TexCoord")) {
                    out.append(direction).append(" vec4 pr_TexCoord[8];\n");
                }
                if (varyings.contains("pr_FogFragCoord") && !declared(text, "pr_FogFragCoord")) {
                    out.append(direction).append(" float pr_FogFragCoord;\n");
                }
            }
            if (stage == Stage.FRAGMENT) {
                for (int index : fragData) {
                    out.append("layout(location = ").append(index).append(") out vec4 pr_FragData").append(index).append(";\n");
                }
                if (fragData.contains(0)) {
                    out.append("uniform float pr_AlphaTestRef;\n");
                }
            }
            if (uses(text, "pr_tangentOf")) {
                // Block faces: up and down run U along +X, north and south along -X and +X, east and west along -Z and +Z.
                out.append("vec4 pr_tangentOf(vec3 n) { vec3 a = abs(n); if (a.y >= a.x && a.y >= a.z) return vec4(1.0, 0.0, 0.0, 1.0); "
                        + "if (a.x >= a.z) return vec4(0.0, 0.0, -sign(n.x), 1.0); return vec4(sign(n.z), 0.0, 0.0, 1.0); }\n");
            }
            if (usesShadowHelpers) {
                out.append("vec4 pr_shadow2D(sampler2DShadow s, vec3 c) { return vec4(texture(s, c)); }\n");
                out.append("vec4 pr_shadow2DLod(sampler2DShadow s, vec3 c, float lod) { return vec4(textureLod(s, c, lod)); }\n");
            }
        }

        private void attribute(StringBuilder out, String text, String type, String name) {
            if (uses(text, name) && !declared(text, name)) {
                out.append("in ").append(type).append(' ').append(name).append(";\n");
            }
        }

        private void uniform(StringBuilder out, String text, String type, String name) {
            if (uses(text, name) && !declared(text, name)) {
                out.append("uniform ").append(type).append(' ').append(name).append(";\n");
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private static final Pattern SUFFIXED_INTEGER = Pattern.compile("^([0-9]+)([fF]|lf|LF)$");

    /**
     * {@code 1f} is not GLSL (a float literal needs a point or an exponent), yet packs use it:
     * drivers that accept it, and Iris, which reprints every literal, let it through. It becomes
     * {@code 1.0}.
     */
    static String number(String literal) {
        Matcher m = SUFFIXED_INTEGER.matcher(literal);
        if (!m.matches()) {
            return literal;
        }
        return m.group(2).equalsIgnoreCase("lf") ? m.group(1) + ".0lf" : m.group(1) + ".0";
    }

    /**
     * The sampler uniforms {@code text} declares, in order and once each:
     * {@code uniform sampler2D colortex0, colortex6;} declares both.
     */
    static List<String> samplers(String text) {
        List<String> out = new ArrayList<>();
        Matcher m = SAMPLER_DECLARATION.matcher(text);
        while (m.find()) {
            for (String part : m.group(1).split(",")) {
                Matcher name = LEADING_NAME.matcher(part);
                if (name.find() && !out.contains(name.group(1))) {
                    out.add(name.group(1));
                }
            }
        }
        return out;
    }

    private static boolean uses(String text, String name) {
        return Pattern.compile("\\b" + name + "\\b").matcher(text).find();
    }

    /** Whether a global declaration ({@code uniform}, {@code in}, {@code out}, {@code const}) names {@code name}. */
    static boolean declared(String text, String name) {
        return Pattern.compile("\\b(?:uniform|in|out|attribute|varying|const)\\b[^;{}()]*?\\b" + name + "\\b\\s*(?:\\[[^\\]]*\\])?\\s*[;=,]")
                .matcher(text).find();
    }

    private static boolean declaresTextureSampler(List<Token> tokens) {
        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (t.kind == Kind.IDENT && t.text.startsWith("sampler")) {
                int n = nextSignificant(tokens, i + 1);
                if (n < tokens.size() && tokens.get(n).text.equals("texture")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean previousIs(List<Token> tokens, int i, String text) {
        for (int j = i - 1; j >= 0; j--) {
            Token t = tokens.get(j);
            if (t.kind == Kind.SPACE || t.kind == Kind.COMMENT) {
                continue;
            }
            return t.text.equals(text);
        }
        return false;
    }

    /** The constant index of {@code [N]} starting at token {@code open}, or -1. */
    private static int arrayIndex(List<Token> tokens, int open) {
        if (open >= tokens.size() || !tokens.get(open).text.equals("[")) {
            return -1;
        }
        int number = nextSignificant(tokens, open + 1);
        int close = nextSignificant(tokens, number + 1);
        if (number >= tokens.size() || close >= tokens.size() || tokens.get(number).kind != Kind.NUMBER
                || !tokens.get(close).text.equals("]")) {
            return -1;
        }
        try {
            return Integer.parseInt(tokens.get(number).text.replaceAll("[uU]$", ""));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static int nextSignificant(List<Token> tokens, int from) {
        int i = from;
        while (i < tokens.size() && (tokens.get(i).kind == Kind.SPACE || tokens.get(i).kind == Kind.COMMENT)) {
            i++;
        }
        return i;
    }

    // ------------------------------------------------------------------ tokens

    enum Kind {
        SPACE, COMMENT, IDENT, NUMBER, PUNCT
    }

    record Token(Kind kind, String text) {
    }

    static List<Token> tokenize(String s) {
        List<Token> out = new ArrayList<>();
        int n = s.length();
        int i = 0;
        while (i < n) {
            char c = s.charAt(i);
            int start = i;
            if (c == '/' && i + 1 < n && s.charAt(i + 1) == '/') {
                while (i < n && s.charAt(i) != '\n') {
                    i++;
                }
                out.add(new Token(Kind.COMMENT, s.substring(start, i)));
            } else if (c == '/' && i + 1 < n && s.charAt(i + 1) == '*') {
                int end = s.indexOf("*/", i + 2);
                i = end < 0 ? n : end + 2;
                out.add(new Token(Kind.COMMENT, s.substring(start, i)));
            } else if (Character.isWhitespace(c)) {
                while (i < n && Character.isWhitespace(s.charAt(i))) {
                    i++;
                }
                out.add(new Token(Kind.SPACE, s.substring(start, i)));
            } else if (Character.isLetter(c) || c == '_') {
                while (i < n && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_')) {
                    i++;
                }
                out.add(new Token(Kind.IDENT, s.substring(start, i)));
            } else if (Character.isDigit(c) || c == '.' && i + 1 < n && Character.isDigit(s.charAt(i + 1))) {
                while (i < n) {
                    char d = s.charAt(i);
                    if (Character.isLetterOrDigit(d) || d == '.' || (d == '+' || d == '-') && (s.charAt(i - 1) == 'e' || s.charAt(i - 1) == 'E')) {
                        i++;
                    } else {
                        break;
                    }
                }
                out.add(new Token(Kind.NUMBER, s.substring(start, i)));
            } else {
                out.add(new Token(Kind.PUNCT, String.valueOf(c)));
                i++;
            }
        }
        return out;
    }
}
