package net.pryzma.shader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Settings shader packs declare inside their GLSL rather than in {@code shaders.properties}:
 * the draw buffers of a program ({@code DRAWBUFFERS:0123} or {@code RENDERTARGETS: 0,1} in a
 * comment) and the engine constants ({@code const int colortex1Format = RGBA16F;},
 * {@code const bool colortex3Clear = false;}, {@code const int shadowMapResolution = 2048;}...).
 * Constants are collected from every program source; a later declaration wins.
 */
public final class PrShaderConfig {
    public static final int BUFFERS = 16;

    private static final Pattern DRAWBUFFERS = Pattern.compile("(?:/\\*|//)\\s*DRAWBUFFERS\\s*:\\s*([0-9N]+)");
    private static final Pattern RENDERTARGETS = Pattern.compile("(?:/\\*|//)\\s*RENDERTARGETS\\s*:\\s*([0-9,\\s]+)");
    private static final Pattern CONST = Pattern.compile(
            "^\\s*const\\s+(int|float|bool|vec4|ivec3|vec3|vec2)\\s+(\\w+)\\s*=\\s*([^;]+);", Pattern.MULTILINE);
    private static final Pattern LEGACY = Pattern.compile("/\\*\\s*(SHADOWRES|SHADOWFOV|SHADOWHPL|SHADOWDEPTH):\\s*([0-9.]+)\\s*\\*/");
    private static final Pattern BUFFER_NAME = Pattern.compile(
            "^(colortex(?:1[0-5]|[0-9])|gcolor|gdepth|gnormal|composite|gaux[1-4])(?=[A-Z])");
    private static final String[] LEGACY_BUFFER_NAMES = {"gcolor", "gdepth", "gnormal", "composite", "gaux1", "gaux2", "gaux3", "gaux4"};

    /** GL internal formats by the names packs use. */
    static final Map<String, Integer> FORMATS = Map.ofEntries(
            Map.entry("R8", 0x8229), Map.entry("RG8", 0x822B), Map.entry("RGB8", 0x8051), Map.entry("RGBA8", 0x8058),
            Map.entry("R8_SNORM", 0x8F94), Map.entry("RG8_SNORM", 0x8F95), Map.entry("RGB8_SNORM", 0x8F96), Map.entry("RGBA8_SNORM", 0x8F97),
            Map.entry("R16", 0x822A), Map.entry("RG16", 0x822C), Map.entry("RGB16", 0x8054), Map.entry("RGBA16", 0x805B),
            Map.entry("R16_SNORM", 0x8F98), Map.entry("RG16_SNORM", 0x8F99), Map.entry("RGB16_SNORM", 0x8F9A), Map.entry("RGBA16_SNORM", 0x8F9B),
            Map.entry("R16F", 0x822D), Map.entry("RG16F", 0x822F), Map.entry("RGB16F", 0x881B), Map.entry("RGBA16F", 0x881A),
            Map.entry("R32F", 0x822E), Map.entry("RG32F", 0x8230), Map.entry("RGB32F", 0x8815), Map.entry("RGBA32F", 0x8814),
            Map.entry("R8I", 0x8231), Map.entry("RG8I", 0x8237), Map.entry("RGB8I", 0x8D8F), Map.entry("RGBA8I", 0x8D8E),
            Map.entry("R8UI", 0x8232), Map.entry("RG8UI", 0x8238), Map.entry("RGB8UI", 0x8D7D), Map.entry("RGBA8UI", 0x8D7C),
            Map.entry("R16I", 0x8233), Map.entry("RG16I", 0x8239), Map.entry("RGB16I", 0x8D89), Map.entry("RGBA16I", 0x8D88),
            Map.entry("R16UI", 0x8234), Map.entry("RG16UI", 0x823A), Map.entry("RGB16UI", 0x8D77), Map.entry("RGBA16UI", 0x8D76),
            Map.entry("R32I", 0x8235), Map.entry("RG32I", 0x823B), Map.entry("RGB32I", 0x8D83), Map.entry("RGBA32I", 0x8D82),
            Map.entry("R32UI", 0x8236), Map.entry("RG32UI", 0x823C), Map.entry("RGB32UI", 0x8D71), Map.entry("RGBA32UI", 0x8D70),
            Map.entry("R3_G3_B2", 0x2A10), Map.entry("RGB5_A1", 0x8057), Map.entry("RGB10_A2", 0x8059),
            Map.entry("R11F_G11F_B10F", 0x8C3A), Map.entry("RGB9_E5", 0x8C3D), Map.entry("RGBA2", 0x8055),
            Map.entry("RGBA4", 0x8056), Map.entry("RGBA12", 0x805A), Map.entry("RGB4", 0x804F), Map.entry("RGB5", 0x8050),
            Map.entry("RGB10", 0x8052), Map.entry("RGB12", 0x8053), Map.entry("RGB10_A2UI", 0x906F));

    public static final int RGBA8 = 0x8058;

    /** Internal format per buffer; 0 when the pack does not say. */
    public final int[] formats = new int[BUFFERS];
    public final boolean[] clear = new boolean[BUFFERS];
    /** Clear colour per buffer; {@code null} for the default (black, colortex1 white, colortex0 fog). */
    public final float[][] clearColors = new float[BUFFERS][];
    public final boolean[] mipmaps = new boolean[BUFFERS];
    public int shadowMapResolution = 1024;
    public float shadowDistance = 160.0F;
    public float shadowDistanceRenderMul = -1.0F;
    public float shadowIntervalSize = 2.0F;
    public float shadowMapFov = -1.0F;
    public float sunPathRotation;
    public float ambientOcclusionLevel = 1.0F;
    public float eyeBrightnessHalflife = 10.0F;
    public float wetnessHalflife = 600.0F;
    public float drynessHalflife = 200.0F;
    public float centerDepthHalflife = 1.0F;
    public int noiseTextureResolution = 256;
    public final boolean[] shadowHardwareFiltering = new boolean[2];
    public final boolean[] shadowNearest = new boolean[2];
    public final boolean[] shadowColorNearest = new boolean[2];
    public boolean generateShadowMipmap;
    /** {@code shadowcolor0Format}, {@code shadowcolor1Format}; 0 when unset. */
    public final int[] shadowColorFormats = new int[2];
    /** {@code size.buffer.<buffer>}: width, height and 1 when relative to the screen; {@code null} for screen size. */
    public final float[][] sizes = new float[BUFFERS][];

    public PrShaderConfig() {
        Arrays.fill(clear, true);
    }

    /**
     * Reads {@code size.buffer.<buffer>=<width> <height>} ({@code settings} without the prefix):
     * whole numbers are pixels, decimals a fraction of the screen.
     */
    public void readSizes(Map<String, String> settings, Consumer<String> warn) {
        settings.forEach((name, value) -> {
            int buffer = PrShaderPipeline.bufferIndex(name);
            float[] size = size(value);
            if (buffer < 0 || buffer >= BUFFERS) {
                warn.accept("Invalid buffer in size.buffer." + name);
            } else if (size == null) {
                warn.accept("Invalid size.buffer." + name + "=" + value);
            } else {
                sizes[buffer] = size;
            }
        });
    }

    static float[] size(String value) {
        String[] parts = value.trim().split("\\s+");
        if (parts.length != 2) {
            return null;
        }
        float[] out = new float[3];
        for (int i = 0; i < 2; i++) {
            if (!parts[i].matches("\\d+")) {
                out[2] = 1;
            }
        }
        try {
            out[0] = Float.parseFloat(parts[0]);
            out[1] = Float.parseFloat(parts[1]);
        } catch (NumberFormatException e) {
            return null;
        }
        return out[0] >= 0 && out[1] >= 0 ? out : null;
    }

    /** The pixel size of {@code buffer} on a {@code width}×{@code height} screen. */
    public int[] bufferSize(int buffer, int width, int height) {
        float[] s = sizes[buffer];
        if (s == null) {
            return new int[] {width, height};
        }
        return s[2] != 0 ? new int[] {Math.max(1, (int) (s[0] * width)), Math.max(1, (int) (s[1] * height))}
                : new int[] {Math.max(1, (int) s[0]), Math.max(1, (int) s[1])};
    }

    /**
     * Whether a pass drawing {@code buffers} mixes sizes: sized buffers of different sizes, or
     * sized and screen-sized ones. OptiFine then draws at screen size without the sized buffers.
     */
    public boolean mixesSizes(int[] buffers) {
        float[] first = null;
        boolean unsized = false;
        boolean different = false;
        for (int b : buffers) {
            if (b < 0 || b >= BUFFERS) {
                continue;
            }
            if (sizes[b] == null) {
                unsized = true;
            } else if (first == null) {
                first = sizes[b];
            } else {
                different |= !Arrays.equals(first, sizes[b]);
            }
        }
        return first != null && (unsized || different);
    }

    /**
     * The buffers one program samples at lower levels ({@code const bool colortex0MipmapEnabled = true;}),
     * as a bit per buffer: OptiFine builds their mipmaps right before that program draws.
     */
    static int mipmapMask(String source) {
        int mask = 0;
        Matcher m = CONST.matcher(source);
        while (m.find()) {
            if (m.group(2).endsWith("MipmapEnabled") && m.group(3).trim().equals("true")) {
                int buffer = bufferIndex(m.group(2));
                if (buffer >= 0) {
                    mask |= 1 << buffer;
                }
            }
        }
        return mask;
    }

    /** Collects the constants of one program source (of a composite stage program). */
    public void scan(String source) {
        scan(source, true);
    }

    /**
     * Collects the constants of one program source. Clearing, clear colours and mipmaps are
     * only read from composite stage programs, as OptiFine reads them.
     */
    public void scan(String source, boolean composite) {
        Matcher m = CONST.matcher(source);
        while (m.find()) {
            constant(m.group(1), m.group(2), m.group(3).trim(), composite);
        }
        Matcher legacy = LEGACY.matcher(source);
        while (legacy.find()) {
            float value = number(legacy.group(2), -1);
            switch (legacy.group(1)) {
                case "SHADOWRES" -> shadowMapResolution = (int) value;
                case "SHADOWFOV" -> shadowMapFov = value;
                case "SHADOWHPL" -> shadowDistance = value;
                default -> {
                }
            }
        }
    }

    private void constant(String type, String name, String value, boolean composite) {
        int buffer = bufferIndex(name);
        if (buffer >= 0) {
            String suffix = name.substring(bufferName(name).length());
            switch (suffix) {
                case "Format" -> {
                    Integer format = FORMATS.get(value.toUpperCase(Locale.ROOT));
                    if (format != null) {
                        formats[buffer] = format;
                    }
                }
                case "Clear" -> {
                    if (composite) {
                        clear[buffer] = !value.equals("false");
                    }
                }
                case "ClearColor" -> {
                    if (composite) {
                        clearColors[buffer] = vector(value);
                    }
                }
                case "MipmapEnabled" -> {
                    if (composite) {
                        mipmaps[buffer] = value.equals("true");
                    }
                }
                default -> {
                }
            }
            return;
        }
        switch (name) {
            case "shadowMapResolution" -> shadowMapResolution = (int) number(value, shadowMapResolution);
            case "shadowDistance" -> shadowDistance = number(value, shadowDistance);
            case "shadowDistanceRenderMul" -> shadowDistanceRenderMul = number(value, shadowDistanceRenderMul);
            case "shadowIntervalSize" -> shadowIntervalSize = number(value, shadowIntervalSize);
            case "shadowMapFov" -> shadowMapFov = number(value, shadowMapFov);
            case "sunPathRotation" -> sunPathRotation = number(value, sunPathRotation);
            case "ambientOcclusionLevel" -> ambientOcclusionLevel = number(value, ambientOcclusionLevel);
            case "eyeBrightnessHalflife" -> eyeBrightnessHalflife = number(value, eyeBrightnessHalflife);
            case "wetnessHalflife" -> wetnessHalflife = number(value, wetnessHalflife);
            case "drynessHalflife" -> drynessHalflife = number(value, drynessHalflife);
            case "centerDepthHalflife" -> centerDepthHalflife = number(value, centerDepthHalflife);
            case "noiseTextureResolution" -> noiseTextureResolution = (int) number(value, noiseTextureResolution);
            case "shadowHardwareFiltering" -> Arrays.fill(shadowHardwareFiltering, value.equals("true"));
            case "shadowHardwareFiltering0" -> shadowHardwareFiltering[0] = value.equals("true");
            case "shadowHardwareFiltering1" -> shadowHardwareFiltering[1] = value.equals("true");
            case "shadowtex0Nearest", "shadowtexNearest", "shadow0MinMagNearest" -> shadowNearest[0] = value.equals("true");
            case "shadowtex1Nearest", "shadow1MinMagNearest" -> shadowNearest[1] = value.equals("true");
            case "shadowcolor0Nearest", "shadowColor0Nearest", "shadowColor0MinMagNearest" -> shadowColorNearest[0] = value.equals("true");
            case "shadowcolor1Nearest", "shadowColor1Nearest", "shadowColor1MinMagNearest" -> shadowColorNearest[1] = value.equals("true");
            case "generateShadowMipmap" -> generateShadowMipmap = value.equals("true");
            case "shadowcolor0Format", "shadowColor0Format" -> shadowColorFormats[0] = FORMATS.getOrDefault(value.toUpperCase(Locale.ROOT), 0);
            case "shadowcolor1Format", "shadowColor1Format" -> shadowColorFormats[1] = FORMATS.getOrDefault(value.toUpperCase(Locale.ROOT), 0);
            default -> {
            }
        }
    }

    /** {@code colortexN...} or the legacy names ({@code gdepth...}) as a buffer index, or -1. */
    static int bufferIndex(String name) {
        String prefix = bufferName(name);
        if (prefix.isEmpty()) {
            return -1;
        }
        if (prefix.startsWith("colortex")) {
            return Integer.parseInt(prefix.substring("colortex".length()));
        }
        for (int i = 0; i < LEGACY_BUFFER_NAMES.length; i++) {
            if (LEGACY_BUFFER_NAMES[i].equals(prefix)) {
                return i;
            }
        }
        return -1;
    }

    private static String bufferName(String name) {
        Matcher m = BUFFER_NAME.matcher(name);
        return m.find() ? m.group(1) : "";
    }

    /**
     * The draw buffers of a fragment source: the last {@code DRAWBUFFERS}/{@code RENDERTARGETS}
     * directive, or {@code null} when it has none.
     */
    public static int[] drawBuffers(String fragment) {
        int[] found = null;
        Matcher d = DRAWBUFFERS.matcher(fragment);
        int at = -1;
        while (d.find()) {
            if (d.start() > at) {
                at = d.start();
                String digits = d.group(1);
                List<Integer> out = new ArrayList<>();
                for (char c : digits.toCharArray()) {
                    // N marks an output that is written nowhere, as in OptiFine.
                    out.add(c == 'N' ? -1 : c - '0');
                }
                found = out.stream().mapToInt(Integer::intValue).toArray();
            }
        }
        Matcher r = RENDERTARGETS.matcher(fragment);
        while (r.find()) {
            if (r.start() > at) {
                at = r.start();
                List<Integer> out = new ArrayList<>();
                for (String part : r.group(1).split(",")) {
                    String p = part.trim();
                    if (!p.isEmpty()) {
                        out.add(Integer.parseInt(p));
                    }
                }
                found = out.stream().mapToInt(Integer::intValue).toArray();
            }
        }
        if (found != null) {
            // Buffers past colortex15 exist only in Iris; they receive nothing here.
            for (int i = 0; i < found.length; i++) {
                if (found[i] >= BUFFERS) {
                    found[i] = -1;
                }
            }
        }
        return found;
    }

    private static float[] vector(String value) {
        // Skip the constructor name: the 4 of "vec4(" is not a component.
        int open = value.indexOf('(');
        Matcher m = Pattern.compile("-?[0-9]*\\.?[0-9]+(?:[eE][-+]?[0-9]+)?").matcher(open < 0 ? value : value.substring(open + 1));
        List<Float> parts = new ArrayList<>();
        while (m.find()) {
            parts.add(Float.parseFloat(m.group()));
        }
        if (parts.isEmpty()) {
            return null;
        }
        float[] out = new float[4];
        for (int i = 0; i < 4; i++) {
            out[i] = parts.get(Math.min(i, parts.size() - 1));
        }
        return out;
    }

    private static float number(String value, float def) {
        try {
            return Float.parseFloat(value.replace("f", "").replace("F", "").trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
