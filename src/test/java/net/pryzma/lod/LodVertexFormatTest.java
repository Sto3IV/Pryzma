package net.pryzma.lod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;

/**
 * The descriptions of an LOD vertex must agree: the bytes {@link NativeLodMesher} writes, the GL format both passes
 * bind them with (the Distant Horizons vertex ABI that {@code DHTerrainTransformer} decodes for shader packs), and the
 * {@code pryzma:lod_terrain} core shader (its JSON and its GLSL stages).
 */
class LodVertexFormatTest {
    private static final String SHADER_ROOT = "/assets/pryzma/shaders/core/lod_terrain";

    @Test
    void glFormatMatchesTheMesherLayout() {
        VertexFormat format = LodVertexFormat.FORMAT;
        assertEquals(NativeLodMesher.VERTEX_BYTES, format.getVertexSize());
        assertEquals(NativeLodMesher.OFFSET_POSITION, format.getOffset(LodVertexFormat.POSITION_META));
        assertEquals(NativeLodMesher.OFFSET_COLOR, format.getOffset(VertexFormatElement.COLOR));
        assertEquals(NativeLodMesher.OFFSET_MATERIAL, format.getOffset(LodVertexFormat.EXTRA));
        assertEquals(NativeLodMesher.OFFSET_META, NativeLodMesher.OFFSET_POSITION + 3 * Short.BYTES, "meta is the fourth short");
        assertEquals(NativeLodMesher.OFFSET_NORMAL, NativeLodMesher.OFFSET_MATERIAL + 1, "the normal index follows the material");
        // Attribute locations follow the element order; the shader-pack programs bind the same names to 0, 1 and 2.
        assertEquals(List.of(LodVertexFormat.ATTRIBUTE_POSITION, LodVertexFormat.ATTRIBUTE_COLOR, LodVertexFormat.ATTRIBUTE_EXTRA),
                format.getElementAttributeNames());
        assertEquals(List.of("vPosition", "iris_color", "irisExtra"), format.getElementAttributeNames(),
                "the attribute names DHTerrainTransformer declares");
    }

    @Test
    void integerElementsAreBoundAsIntegers() {
        // UV usage with a non-float type is set up with glVertexAttribIPointer: uvec4 in GLSL.
        VertexFormatElement position = LodVertexFormat.POSITION_META;
        assertEquals(VertexFormatElement.Usage.UV, position.usage());
        assertEquals(VertexFormatElement.Type.USHORT, position.type());
        assertEquals(4, position.count());
        assertEquals(8, position.byteSize());
        VertexFormatElement extra = LodVertexFormat.EXTRA;
        assertEquals(VertexFormatElement.Usage.UV, extra.usage());
        assertEquals(VertexFormatElement.Type.UBYTE, extra.type());
        assertEquals(4, extra.count());
        assertEquals(VertexFormatElement.Usage.COLOR, VertexFormatElement.COLOR.usage(), "normalized colour");
        assertNotEquals(position.id(), extra.id());
        assertEquals(position, VertexFormatElement.byId(position.id()), "registered");
        assertEquals(extra, VertexFormatElement.byId(extra.id()), "registered");
    }

    @Test
    void shaderDeclaresWhatItsJsonAndTheFormatPromise() throws IOException {
        JsonObject json = JsonParser.parseString(read(SHADER_ROOT + ".json")).getAsJsonObject();
        assertEquals("pryzma:lod_terrain", json.get("vertex").getAsString());
        assertEquals("pryzma:lod_terrain", json.get("fragment").getAsString());
        String vsh = read(SHADER_ROOT + ".vsh");
        String fsh = read(SHADER_ROOT + ".fsh");

        // Attributes are bound by name, in the format's order; the integer ones are read with glVertexAttribIPointer.
        assertTrue(vsh.contains("in uvec4 vPosition;"), "position and meta are unsigned integers");
        assertTrue(vsh.contains("in vec4 iris_color;"), "colour is normalized");
        assertTrue(vsh.contains("in uvec4 irisExtra;"), "material and normal index are unsigned integers");

        // ShaderInstance rejects a uniform whose value count differs from its count, and warns for undeclared ones.
        Set<String> names = new HashSet<>();
        String stages = vsh + "\n" + fsh;
        for (JsonElement element : json.getAsJsonArray("uniforms")) {
            JsonObject uniform = element.getAsJsonObject();
            String name = uniform.get("name").getAsString();
            assertTrue(names.add(name), "declared once: " + name);
            assertEquals(uniform.get("count").getAsInt(), uniform.getAsJsonArray("values").size(), name);
            assertTrue(Pattern.compile("uniform\\s+\\w+\\s+" + name + "\\s*;").matcher(stages).find(), name + " is declared in GLSL");
        }
        assertTrue(names.containsAll(Set.of("ModelViewMat", "ProjMat", "ChunkOffset", "ColorModulator", "FogStart", "FogEnd",
                "FogColor", "ClipRadius", "SectorOrigin")), "uniforms the renderer sets: " + names);

        JsonArray samplers = json.getAsJsonArray("samplers");
        assertEquals(1, samplers.size());
        assertEquals("Sampler2", samplers.get(0).getAsJsonObject().get("name").getAsString(), "the light map");
        assertTrue(vsh.contains("uniform sampler2D Sampler2;"));

        for (String varying : new String[] {"vec3 relativePos", "vec3 localPos", "vec4 vertexColor"}) {
            assertTrue(vsh.contains("out " + varying + ";"), varying);
            assertTrue(fsh.contains("in " + varying + ";"), varying);
        }
        for (String varying : new String[] {"uint material", "uint normalIndex"}) {
            assertTrue(vsh.contains("flat out " + varying + ";"), "integer varyings are flat: " + varying);
            assertTrue(fsh.contains("flat in " + varying + ";"), "integer varyings are flat: " + varying);
        }
        assertTrue(fsh.contains("discard;"), "the vanilla radius is cut out");
    }

    @Test
    void shaderMaterialConstantsAreTheMeshersMaterials() throws IOException {
        String fsh = read(SHADER_ROOT + ".fsh");
        assertTrue(fsh.contains("const uint MATERIAL_LEAVES = " + LodMaterial.LEAVES + "u;"));
        assertTrue(fsh.contains("const uint MATERIAL_WATER = " + LodMaterial.WATER + "u;"));
    }

    private static String read(String path) throws IOException {
        try (InputStream in = LodVertexFormatTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path + " is packaged");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
