package net.pryzma.shader;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.minecraft.resources.ResourceLocation;

class PrShaderPackTest {
    @TempDir
    Path dir;
    private final List<String> warnings = new ArrayList<>();

    private PrShaderPack pack(String... pathsAndContents) throws IOException {
        Path root = dir.resolve("pack");
        for (int i = 0; i < pathsAndContents.length; i += 2) {
            Path file = root.resolve(pathsAndContents[i]);
            Files.createDirectories(file.getParent());
            Files.writeString(file, pathsAndContents[i + 1], StandardCharsets.UTF_8);
        }
        return PrShaderPack.open(root);
    }

    @Test
    void includesExpandRelativeAndAbsoluteWithLineMarkers() throws IOException {
        PrShaderPack pack = pack(
                "shaders/gbuffers_basic.fsh", "#version 120\n#include \"/lib/a.glsl\"\nvoid main() {}",
                "shaders/lib/a.glsl", "float a;\n#include \"b.glsl\"\nfloat c;",
                "shaders/lib/b.glsl", "float b;",
                "shaders/loop.glsl", "#include \"loop.glsl\"\n",
                "shaders/missing.fsh", "#include \"nope.glsl\"\n");
        PrGlslPreprocessor.Source source = PrGlslPreprocessor.expand(pack, "shaders/gbuffers_basic.fsh", warnings::add);
        assertEquals(List.of("shaders/gbuffers_basic.fsh", "shaders/lib/a.glsl", "shaders/lib/b.glsl"), source.files());
        assertTrue(source.text().contains("#line 1 1\nfloat a;\n#line 1 2\nfloat b;\n#line 3 1\nfloat c;\n"), source.text());
        assertTrue(source.text().contains("#line 3 0\nvoid main() {}"), source.text());
        assertTrue(warnings.isEmpty());

        PrGlslPreprocessor.expand(pack, "shaders/loop.glsl", warnings::add);
        PrGlslPreprocessor.expand(pack, "shaders/missing.fsh", warnings::add);
        assertEquals(2, warnings.size(), warnings::toString);
        assertNull(PrGlslPreprocessor.expand(pack, "shaders/absent.fsh", warnings::add));
    }

    @Test
    void conditionalsFollowTheCPreprocessor() {
        Map<String, String> macros = new LinkedHashMap<>(Map.of("A", "", "B", "1"));
        String out = PrGlslPreprocessor.conditionals("""
                #ifdef A
                a=1
                #else
                a=2
                #endif
                #if B >= 2 && defined(A)
                b=1
                #elif B == 1
                b=2
                #else
                b=3
                #endif
                #define C 5
                #if C == 5
                c=1
                #endif
                #ifndef D
                d=1
                #endif
                #if 0
                #if 1
                x=1
                #endif
                #else
                y=1
                #endif
                """, macros, warnings::add);
        assertEquals(List.of("a=1", "b=2", "c=1", "d=1", "y=1"), out.lines().filter(l -> !l.isBlank()).toList());
        assertEquals("5", macros.get("C"));
        assertTrue(warnings.isEmpty(), warnings::toString);
    }

    private static final String OPTIONS_SOURCE = """
            #define SHADOWS // Soft shadows
            #ifdef SHADOWS
            #endif
            //#define BLOOM
            #if defined(BLOOM) && 1
            #endif
            #define UNTESTED_SWITCH
            #define QUALITY 2 // [1 2 3] Quality level
            const int shadowMapResolution = 2048; // [1024 2048 4096]
            const float notAnOption = 1.0; // [1.0 2.0]
            #define MC_SOMETHING 3 // [1 2 3]
            """;

    @Test
    void optionsAreFoundAppliedAndProfiled() {
        Map<String, PrShaderOption> found = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        PrShaderOptions.collect(OPTIONS_SOURCE, "shaders/composite.fsh", found, warnings::add);
        assertEquals(List.of("BLOOM", "QUALITY", "shadowMapResolution", "SHADOWS"), List.copyOf(found.keySet()));
        assertEquals("true", found.get("SHADOWS").defaultValue());
        assertEquals("false", found.get("BLOOM").defaultValue());
        assertEquals(List.of("1", "2", "3"), found.get("QUALITY").values());
        assertEquals("Quality level", found.get("QUALITY").description());
        assertEquals(List.of("1024", "2048", "4096"), found.get("shadowMapResolution").values());

        PrShaderOptions options = PrShaderOptions.of(found);
        assertTrue(options.get("BLOOM").set("true"));
        assertTrue(options.get("QUALITY").set("3"));
        assertFalse(options.get("QUALITY").set("7"), "only listed values");
        String applied = options.apply(OPTIONS_SOURCE);
        assertTrue(applied.contains("#define BLOOM // Shader option"), applied);
        assertTrue(applied.contains("#define QUALITY 3 // Shader option"), applied);
        assertTrue(applied.contains("#define SHADOWS // Soft shadows"), "unchanged options keep their line");
        assertEquals(Map.of("BLOOM", "", "QUALITY", "3", "SHADOWS", ""), new TreeMap<>(options.macros()));

        Map<String, String> profiles = new LinkedHashMap<>();
        profiles.put("LOW", "!SHADOWS QUALITY=1");
        profiles.put("HIGH", "profile.LOW SHADOWS QUALITY=3");
        options.applyProfile("HIGH", profiles, warnings::add);
        assertEquals("true", options.get("SHADOWS").value());
        assertEquals("HIGH", options.currentProfile(profiles));
        options.get("QUALITY").set("2");
        assertNull(options.currentProfile(profiles));
        options.applyProfile("LOW", profiles, warnings::add);
        assertEquals("LOW", options.currentProfile(profiles));
        assertTrue(warnings.isEmpty(), warnings::toString);
    }

    @Test
    void anOptionWithTwoDefaultsIsLeftAlone() {
        Map<String, PrShaderOption> found = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        PrShaderOptions.collect(OPTIONS_SOURCE, "a.fsh", found, warnings::add);
        PrShaderOptions.collect("#define QUALITY 1 // [1 2 3]\n", "b.fsh", found, warnings::add);
        assertFalse(found.get("QUALITY").isEnabled());
        assertEquals(1, warnings.size());
        PrShaderOptions options = PrShaderOptions.of(found);
        options.get("QUALITY").set("3");
        assertTrue(options.apply(OPTIONS_SOURCE).contains("#define QUALITY 2 // [1 2 3] Quality level"));
    }

    @Test
    void translatesCompatibilityPrograms() {
        String vs = """
                #version 120
                varying vec2 uv;
                varying vec2 lm;
                attribute vec4 mc_Entity;
                void main() {
                    gl_Position = ftransform();
                    uv = (gl_TextureMatrix[0] * gl_MultiTexCoord0).st;
                    lm = (gl_TextureMatrix[1] * gl_MultiTexCoord1).st;
                    gl_FrontColor = gl_Color;
                }
                """;
        String fs = """
                #version 120
                uniform sampler2D texture;
                uniform sampler2D lightmap;
                uniform sampler2DShadow shadow;
                varying vec2 uv;
                varying vec2 lm;
                void main() {
                    vec4 c = texture2D(texture, uv) * texture2D(lightmap, lm) * gl_Color;
                    float s = shadow2D(shadow, vec3(uv, 0.5)).r;
                /* DRAWBUFFERS:02 */
                    gl_FragData[0] = c * s;
                    gl_FragData[1] = vec4(1.0);
                }
                """;
        PrGlslTransformer.Result r = PrGlslTransformer.transform(vs, null, fs, PrGlslTransformer.Inputs.ALL, "#define MC_VERSION 12101\n", warnings::add);
        assertTrue(r.vertex().startsWith("#version 330 core\n"), r.vertex());
        for (String expected : List.of("(ProjMat * ModelViewMat * vec4(Position + ChunkOffset, 1.0))", "(pr_TextureMat * vec4(UV0, 0.0, 1.0))",
                "(pr_LightmapMat * vec4(vec2(UV2), 0.0, 1.0))", "pr_FrontColor = (Color * ColorModulator)", "out vec2 uv;",
                "vec4 mc_Entity;", "mc_Entity = vec4(0);", "in vec3 Position;", "in ivec2 UV2;", "uniform vec3 ChunkOffset;",
                "out vec4 pr_FrontColor;", "const mat4 pr_LightmapMat", "#define MC_VERSION 12101")) {
            assertTrue(r.vertex().contains(expected), () -> expected + " missing in\n" + r.vertex());
        }
        assertFalse(r.vertex().contains("in vec4 mc_Entity"), "no vertex format carries block ids");
        assertFalse(r.vertex().replace("gl_Position", "").contains("gl_"), r.vertex());
        for (String expected : List.of("uniform sampler2D gtexture;", "texture(gtexture, uv)", "texture(lightmap, lm)",
                "pr_shadow2D(shadow, vec3(uv, 0.5))", "in vec4 pr_FrontColor;", "layout(location = 0) out vec4 pr_FragData0;",
                "layout(location = 1) out vec4 pr_FragData1;", "void pr_main()", "if (pr_FragData0.a < pr_AlphaTestRef) discard;",
                "in vec2 uv;")) {
            assertTrue(r.fragment().contains(expected), () -> expected + " missing in\n" + r.fragment());
        }
        assertFalse(r.fragment().contains("gl_FragData") || r.fragment().contains("texture2D"), r.fragment());
        List<String> body = r.vertex().substring(r.vertex().indexOf("#line 1 0\n") + 10).lines().toList();
        assertEquals("out vec2 uv;", body.get(1).trim(), "source lines keep their numbers");
        assertTrue(warnings.isEmpty(), warnings::toString);
    }

    @Test
    void translatesOptiFineCoreNamesWithoutDuplicateDeclarations() throws IOException {
        PrShaderPack pack = pack(
                "shaders/gbuffers_basic.vsh", "#include \"basic_vert.glsl\"\n",
                "shaders/basic_vert.glsl", """
                        #version 460
                        in vec3 vaPosition;
                        in vec2 vaUV0;
                        in ivec2 vaUV2;
                        uniform mat4 modelViewMatrix;
                        uniform mat4 projectionMatrix;
                        uniform vec3 chunkOffset;
                        uniform mat3 normalMatrix;
                        out vec2 uv;
                        void main() { uv = vaUV0; gl_Position = projectionMatrix * modelViewMatrix * vec4(vaPosition + chunkOffset, 1); }
                        """,
                "shaders/gbuffers_skybasic.fsh", """
                        #version 460 compatibility
                        in vec4 starData;
                        /* DRAWBUFFERS:01 */
                        layout(location = 0) out vec4 color;
                        void main() { color = starData * gl_Fog.color; }
                        """);
        String vertex = PrGlslPreprocessor.expand(pack, "shaders/gbuffers_basic.vsh", warnings::add).text();
        String fragment = pack.read("shaders/gbuffers_skybasic.fsh");
        PrGlslTransformer.Result r = PrGlslTransformer.transform(vertex, null, fragment, PrGlslTransformer.Inputs.ALL, "", warnings::add);
        assertTrue(r.vertex().startsWith("#version 460 core\n"), "a #version in an include still comes first");
        assertEquals(1, count(r.vertex(), "#version"));
        assertEquals(1, count(r.vertex(), "in vec3 Position;"), r.vertex());
        assertEquals(1, count(r.vertex(), "uniform mat4 ModelViewMat;"), r.vertex());
        assertEquals(1, count(r.vertex(), "uniform mat3 pr_NormalMatrix;"), r.vertex());
        assertFalse(r.vertex().contains("vaPosition") || r.vertex().contains("chunkOffset"), r.vertex());
        assertTrue(r.fragment().startsWith("#version 460 core\n"), r.fragment());
        assertTrue(r.fragment().contains("color = starData * FogColor;"), r.fragment());
        assertTrue(r.fragment().contains("uniform vec4 FogColor;"), r.fragment());
        assertFalse(r.fragment().contains("pr_AlphaTestRef"), "a pack with its own outputs does its own alpha test");
        assertTrue(warnings.isEmpty(), warnings::toString);
    }

    @Test
    void oldNamesThatBecameKeywordsAreRenamed() {
        String fs = "#version 120\nfloat common = 1.0;\nvoid main() { gl_FragColor = vec4(common); }\n";
        PrGlslTransformer.Result r = PrGlslTransformer.transform("#version 120\nvoid main() { gl_Position = ftransform(); }",
                null, fs, PrGlslTransformer.Inputs.ALL, "", warnings::add);
        assertTrue(r.fragment().contains("float common_pr = 1.0;") && r.fragment().contains("vec4(common_pr)"), r.fragment());
    }

    @Test
    void floatLiteralsWithoutAPointBecomeValid() {
        String fs = "#version 460\nout vec4 c;\nvoid main() { c = vec4(1f / 96F, 0x1F, 2.5f, 1e3); }\n";
        PrGlslTransformer.Result r = PrGlslTransformer.transform("#version 460\nvoid main() {}", null, fs,
                PrGlslTransformer.Inputs.ALL, "", warnings::add);
        assertTrue(r.fragment().contains("vec4(1.0 / 96.0, 0x1F, 2.5f, 1e3)"), r.fragment());
    }

    @Test
    void extendedAttributesAreComputedAtTheStartOfMain() {
        String vs = """
                #version 330 compatibility
                in vec4 at_tangent;
                in vec2 mc_midTexCoord;
                #ifdef MID_BLOCK
                attribute vec3 at_midBlock;
                #endif
                out vec4 t;
                void main() {
                    t = at_tangent + vec4(mc_midTexCoord, 0.0, 0.0);
                    gl_Position = ftransform();
                }
                """;
        String fs = "#version 330\nin vec4 t;\nout vec4 c;\nvoid main() { c = t; }\n";
        PrGlslTransformer.Result r = PrGlslTransformer.transform(vs, null, fs, PrGlslTransformer.Inputs.ALL, "", warnings::add);
        for (String expected : List.of("vec4 at_tangent;", "vec2 mc_midTexCoord;", "vec3 at_midBlock;", "void pr_vmain()",
                "at_tangent = pr_tangentOf(Normal);", "mc_midTexCoord = UV0;", "at_midBlock = vec3(0);", "pr_vmain();\n}",
                "vec4 pr_tangentOf(vec3 n)", "in vec3 Normal;")) {
            assertTrue(r.vertex().contains(expected), () -> expected + " missing in\n" + r.vertex());
        }
        assertFalse(r.vertex().contains("in vec4 at_tangent") || r.vertex().contains("in vec2 mc_midTexCoord"), r.vertex());
        assertEquals(1, count(r.vertex(), "void main()"), r.vertex());
        List<String> body = r.vertex().substring(r.vertex().indexOf("#line 1 0\n") + 10).lines().toList();
        assertEquals("out vec4 t;", body.get(6).trim(), "source lines keep their numbers");

        // The driver's preprocessor decides: a declaration in a dropped branch is never filled.
        String without = PrGlslPreprocessor.conditionals(r.vertex(), new HashMap<>(), warnings::add);
        assertTrue(without.contains("at_tangent = pr_tangentOf(Normal);"), without);
        assertFalse(without.contains("at_midBlock ="), without);
        String with = PrGlslPreprocessor.conditionals(r.vertex(), new HashMap<>(Map.of("MID_BLOCK", "")), warnings::add);
        assertTrue(with.contains("at_midBlock = vec3(0);"), with);

        PrGlslTransformer.Result pass = PrGlslTransformer.transform(vs, null, fs, PrGlslTransformer.Inputs.COMPOSITE, "", warnings::add);
        assertTrue(pass.vertex().contains("at_tangent = vec4(1.0, 0.0, 0.0, 1.0);"), "no normal to derive it from");
        assertFalse(pass.vertex().contains("pr_tangentOf"), pass.vertex());
        assertTrue(warnings.isEmpty(), warnings::toString);
    }

    @Test
    void blendSettingsTakeTwoOrFourFactors() {
        assertEquals(PrBlend.OFF, PrBlend.parse("off", warnings::add));
        assertEquals(new PrBlend(true, 0x302, 0x303, 0x302, 0x303), PrBlend.parse("SRC_ALPHA ONE_MINUS_SRC_ALPHA", warnings::add));
        assertEquals(new PrBlend(true, 1, 1, 0, 1), PrBlend.parse(" one one  zero one ", warnings::add));
        assertNull(PrBlend.parse(null, warnings::add), "absent keeps the draw's own blending");
        assertTrue(warnings.isEmpty(), warnings::toString);
        assertNull(PrBlend.parse("ONE", warnings::add));
        assertNull(PrBlend.parse("ONE ONE ONE", warnings::add));
        assertNull(PrBlend.parse("ONE TWO", warnings::add));
        assertEquals(3, warnings.size(), warnings::toString);
    }

    @Test
    void samplerDeclarationsMayListSeveralNames() {
        String fs = """
                uniform sampler2D colortex0, colortex6;
                uniform highp usampler2D ids ;
                uniform sampler2DShadow shadowtex0, shadowtex1[2];
                uniform sampler2D colortex0;
                uniform float notASampler;
                vec4 f(sampler2D s) { return texture(s, vec2(0.0)); }
                """;
        assertEquals(List.of("colortex0", "colortex6", "ids", "shadowtex0", "shadowtex1"), PrGlslTransformer.samplers(fs));
    }

    @Test
    void bufferSizesAreFixedOrRelative() {
        PrShaderConfig config = new PrShaderConfig();
        config.readSizes(Map.of("colortex4", "256 256", "gaux2", "0.5 0.25", "colortex9", "12", "shadowcolor0", "1 1"), warnings::add);
        assertArrayEquals(new int[] {256, 256}, config.bufferSize(4, 1280, 720));
        assertArrayEquals(new int[] {640, 180}, config.bufferSize(5, 1280, 720), "gaux2 is colortex5, sized relative to the screen");
        assertArrayEquals(new int[] {1280, 720}, config.bufferSize(9, 1280, 720), "a malformed size is ignored");
        assertEquals(2, warnings.size(), warnings::toString);
        assertFalse(config.mixesSizes(new int[] {4}));
        assertFalse(config.mixesSizes(new int[] {0, 1, -1}));
        assertTrue(config.mixesSizes(new int[] {0, 4}), "a sized buffer with a screen-sized one");
        assertTrue(config.mixesSizes(new int[] {4, 5}), "two different sizes");
    }

    @Test
    void customTexturesFillSlotsByCanonicalBufferName() {
        assertEquals("composite:colortex4", PrCustomTextures.slot("composite.gaux1"));
        assertEquals("composite:colortex3", PrCustomTextures.slot("composite.composite"), "the legacy name of colortex3");
        assertEquals("gbuffers:colortex11", PrCustomTextures.slot("gbuffers.colortex11"));
        assertEquals("deferred:depthtex0", PrCustomTextures.slot("deferred.depthtex0"));
        assertEquals("*:noisetex", PrCustomTextures.slot("noise"));
        assertNull(PrCustomTextures.slot("composite"));
        assertNull(PrCustomTextures.slot(".gaux1"));
        assertNull(PrCustomTextures.slot("composite."));

        PrCustomTextures textures = new PrCustomTextures();
        textures.bind(PrCustomTextures.slot("composite.colortex4"), 7);
        textures.bind(PrCustomTextures.slot("noise"), 9);
        assertEquals(7, textures.lookup("composite", "gaux1"), "a legacy name finds the numbered slot");
        assertEquals(-1, textures.lookup("deferred", "colortex4"), "slots are per stage");
        assertEquals(9, textures.lookup("gbuffers", "noisetex"));
        assertEquals(9, textures.lookup("composite", "noisetex"));
    }

    @Test
    void onlyActiveBranchesDeclareTargetsAndConstants() throws IOException {
        String vsh = "#version 120\nvoid main() { gl_Position = ftransform(); }\n";
        PrShaderPack pack = pack(
                "shaders/composite.vsh", vsh,
                "shaders/composite.fsh", """
                        #version 120
                        #if MC_VERSION >= 11700
                        /* RENDERTARGETS: 0,3 */
                        #else
                        /* RENDERTARGETS: 0,19 */
                        const bool colortex3Clear = false;
                        #endif
                        void main() { gl_FragData[0] = vec4(1.0); }
                        """,
                "shaders/gbuffers_basic.vsh", vsh,
                "shaders/gbuffers_basic.fsh", """
                        const bool colortex5Clear = false;
                        const vec4 colortex5ClearColor = vec4(1.0);
                        const bool colortex5MipmapEnabled = true;
                        const int colortex6Format = RGBA16F;
                        void main() { gl_FragData[0] = vec4(1.0); }
                        """);
        PrShaderConfig config = new PrShaderConfig();
        Map<String, PrShaderPrograms.Source> present = PrShaderPrograms.load(pack, null, PrShaderOptions.empty(),
                PrShaderProperties.empty(), config, Map.of("MC_VERSION", "12101"), warnings::add);
        assertArrayEquals(new int[] {0, 3}, present.get("composite").drawBuffers());
        assertTrue(config.clear[3], "a constant in an inactive branch does not count");
        assertTrue(config.clear[5], "gbuffers programs cannot turn clearing off");
        assertNull(config.clearColors[5]);
        assertFalse(config.mipmaps[5]);
        assertEquals(0x881A, config.formats[6], "formats count in every program");
        assertArrayEquals(new int[] {0, -1}, PrShaderConfig.drawBuffers("/* RENDERTARGETS: 0,19 */"), "Iris-only buffers draw nowhere");
        assertTrue(warnings.isEmpty(), warnings::toString);
    }

    private static int count(String text, String needle) {
        int n = 0;
        for (int i = text.indexOf(needle); i >= 0; i = text.indexOf(needle, i + 1)) {
            n++;
        }
        return n;
    }

    @Test
    void configReadsFormatsClearsConstantsAndDrawBuffers() {
        PrShaderConfig config = new PrShaderConfig();
        config.scan("""
                const int colortex1Format = RGBA16F;
                const int gaux2Format = R11F_G11F_B10F;
                const bool colortex3Clear = false;
                const vec4 colortex4ClearColor = vec4(1.0, 0.5, 0.25, 1.0);
                const int shadowMapResolution = 2048;
                const float sunPathRotation = -30.0;
                /* SHADOWHPL:128.0 */
                """);
        assertEquals(0x881A, config.formats[1]);
        assertEquals(0x8C3A, config.formats[5], "gaux2 is colortex5");
        assertFalse(config.clear[3]);
        assertTrue(config.clear[2]);
        assertArrayEquals(new float[] {1.0F, 0.5F, 0.25F, 1.0F}, config.clearColors[4]);
        assertEquals(2048, config.shadowMapResolution);
        assertEquals(-30.0F, config.sunPathRotation);
        assertEquals(128.0F, config.shadowDistance);

        assertEquals(1 | 1 << 4 | 1 << 12, PrShaderConfig.mipmapMask("""
                const bool colortex0MipmapEnabled = true;
                const bool gaux1MipmapEnabled = true;
                const bool colortex12MipmapEnabled=true;
                const bool colortex3MipmapEnabled = false;
                """), "gaux1 is colortex4");

        assertArrayEquals(new int[] {0, -1, 2}, PrShaderConfig.drawBuffers("/* DRAWBUFFERS:0N2 */"));
        assertArrayEquals(new int[] {3, 7, 12}, PrShaderConfig.drawBuffers("/* RENDERTARGETS: 3,7,12 */"));
        assertArrayEquals(new int[] {1}, PrShaderConfig.drawBuffers("/* RENDERTARGETS: 0,2 */\n/* DRAWBUFFERS:1 */"), "the last one wins");
        assertNull(PrShaderConfig.drawBuffers("void main() {}"));
    }

    @Test
    void propertiesEvaluateConditionalsAndProgramSwitches() {
        Map<String, PrShaderOption> found = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        PrShaderOptions.collect(OPTIONS_SOURCE, "a.fsh", found, warnings::add);
        PrShaderOptions options = PrShaderOptions.of(found);
        PrShaderProperties props = PrShaderProperties.parse("""
                #ifdef BLOOM
                program.composite1.enabled=true
                #else
                program.composite1.enabled=false
                #endif
                program.world0/composite2.enabled=SHADOWS && !BLOOM
                sliders=QUALITY SHADOWS
                profile.LOW=!SHADOWS
                screen=QUALITY [MORE]
                screen.MORE=SHADOWS
                screen.MORE.columns=1
                """, options.macros(), warnings::add);
        assertFalse(props.programEnabled(null, "composite1", options, warnings::add));
        assertTrue(props.programEnabled("world0", "composite2", options, warnings::add));
        options.get("BLOOM").set("true");
        assertFalse(props.programEnabled("world0", "composite2", options, warnings::add), "conditions follow the live options");
        assertTrue(props.programEnabled(null, "composite2", options, warnings::add));
        assertEquals(List.of("QUALITY", "SHADOWS"), props.sliders());
        assertEquals(List.of("", "MORE"), List.copyOf(props.screens().keySet()));
        assertEquals(1, props.columns("MORE"));
        assertEquals(2, props.columns(""));
        assertEquals(Map.of("LOW", "!SHADOWS"), props.profiles());
        assertEquals(List.of(), props.requiredIrisFeatures());
        assertEquals(List.of("CUSTOM_IMAGES", "SSBO"), PrShaderProperties.parse("iris.features.required = CUSTOM_IMAGES  SSBO\n",
                Map.of(), warnings::add).requiredIrisFeatures());
        assertTrue(warnings.isEmpty(), warnings::toString);
    }

    @Test
    void programsFallBackAndDimensionFoldersOverride() throws IOException {
        String vsh = "#version 120\nvoid main() { gl_Position = ftransform(); }\n";
        String fsh = "#version 120\nvoid main() { gl_FragColor = vec4(1.0); }\n";
        PrShaderPack pack = pack(
                "shaders/gbuffers_textured.vsh", vsh, "shaders/gbuffers_textured.fsh", fsh,
                "shaders/world0/gbuffers_terrain.vsh", vsh, "shaders/world0/gbuffers_terrain.fsh", fsh,
                "shaders/composite.vsh", vsh, "shaders/composite.fsh", "/* DRAWBUFFERS:3 */\n" + fsh,
                "shaders/world2/final.vsh", vsh, "shaders/world2/final.fsh", fsh,
                "shaders/dimension.properties", "dimension.world0=*\ndimension.world2=twilightforest:twilight_forest\n");
        assertEquals("world0", PrShaderPrograms.worldFolder(pack, ResourceLocation.parse("minecraft:overworld")));
        assertEquals("world2", PrShaderPrograms.worldFolder(pack, ResourceLocation.parse("twilightforest:twilight_forest")),
                "an explicit dimension wins over *");

        PrShaderConfig config = new PrShaderConfig();
        Map<String, PrShaderPrograms.Source> present = PrShaderPrograms.load(pack, "world0", PrShaderOptions.empty(),
                PrShaderProperties.empty(), config, warnings::add);
        assertEquals(List.of("gbuffers_textured", "gbuffers_terrain", "composite"), List.copyOf(present.keySet()));
        assertEquals("shaders/world0", present.get("gbuffers_terrain").folder());
        assertArrayEquals(new int[] {3}, present.get("composite").drawBuffers());
        assertEquals("gbuffers_terrain", PrShaderPrograms.resolve("gbuffers_water", present));
        assertEquals("gbuffers_textured", PrShaderPrograms.resolve("gbuffers_entities", present));
        assertNull(PrShaderPrograms.resolve("gbuffers_skybasic", present), "basic is missing too");

        PrShaderPack plain = PrShaderPack.open(Files.createDirectories(dir.resolve("plain/shaders/world-1")).getParent().getParent());
        assertNotNull(plain);
        assertEquals("world-1", PrShaderPrograms.worldFolder(plain, ResourceLocation.parse("minecraft:the_nether")));
        assertNull(PrShaderPrograms.worldFolder(plain, ResourceLocation.parse("minecraft:overworld")), "no world0 folder");
        assertNull(PrShaderPrograms.worldFolder(plain, ResourceLocation.parse("twilightforest:twilight_forest")));
        assertTrue(warnings.isEmpty(), warnings::toString);
    }
}
