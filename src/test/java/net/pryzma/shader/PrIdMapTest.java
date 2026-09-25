package net.pryzma.shader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.minecraft.resources.ResourceLocation;
import net.pryzma.shader.id.PrIdMap;
import net.pryzma.shader.id.PrLegacyBlockIds;

class PrIdMapTest {

    @Test
    void legacyBlockIdsProvideOptiFineIrisParity() {
        assertEquals(1, PrLegacyBlockIds.get(ResourceLocation.parse("minecraft:stone")));
        assertEquals(2, PrLegacyBlockIds.get(ResourceLocation.parse("minecraft:grass_block")));
        assertEquals(3, PrLegacyBlockIds.get(ResourceLocation.parse("minecraft:dirt")));
        assertEquals(18, PrLegacyBlockIds.get(ResourceLocation.parse("minecraft:oak_leaves")));
        assertEquals(31, PrLegacyBlockIds.get(ResourceLocation.parse("minecraft:short_grass")));
        assertEquals(59, PrLegacyBlockIds.get(ResourceLocation.parse("minecraft:wheat")));
        assertEquals(-1, PrLegacyBlockIds.get(ResourceLocation.parse("minecraft:non_existent_block_xyz")));
    }

    @Test
    void parsesCustomBlockItemAndEntityProperties(@TempDir Path tempDir) throws IOException {
        Path shadersDir = tempDir.resolve("shaders");
        Files.createDirectories(shadersDir);

        String blockProps = """
                # Custom block mappings
                block.100 = stone granite diorite
                block.200 = oak_leaves birch_leaves
                block.300 = %minecraft:leaves
                """;
        Files.writeString(shadersDir.resolve("block.properties"), blockProps);

        String itemProps = """
                # Custom item mappings
                item.500 = diamond_sword netherite_sword
                item.600 = bow crossbow
                """;
        Files.writeString(shadersDir.resolve("item.properties"), itemProps);

        String entityProps = """
                # Custom entity mappings
                entity.10 = zombie drowned husk
                entity.20 = skeleton stray wither_skeleton
                """;
        Files.writeString(shadersDir.resolve("entity.properties"), entityProps);

        PrShaderPack pack = PrShaderPack.open(tempDir);
        List<String> warnings = new ArrayList<>();
        PrIdMap idMap = PrIdMap.load(pack, null, warnings::add);

        // Block mappings
        assertEquals(100, idMap.getBlockId(ResourceLocation.parse("minecraft:stone")));
        assertEquals(100, idMap.getBlockId(ResourceLocation.parse("minecraft:granite")));
        assertEquals(200, idMap.getBlockId(ResourceLocation.parse("minecraft:oak_leaves")));
        // Fallback for unmapped blocks
        assertEquals(PrLegacyBlockIds.get(ResourceLocation.parse("minecraft:dirt")),
                idMap.getBlockId(ResourceLocation.parse("minecraft:dirt")));

        // Item mappings
        assertEquals(500, idMap.getItemId(ResourceLocation.parse("minecraft:diamond_sword")));
        assertEquals(500, idMap.getItemId(ResourceLocation.parse("minecraft:netherite_sword")));
        assertEquals(600, idMap.getItemId(ResourceLocation.parse("minecraft:bow")));
        assertEquals(-1, idMap.getItemId(ResourceLocation.parse("minecraft:apple")));

        // Entity mappings
        assertEquals(10, idMap.getEntityId(ResourceLocation.parse("minecraft:zombie")));
        assertEquals(10, idMap.getEntityId(ResourceLocation.parse("minecraft:drowned")));
        assertEquals(20, idMap.getEntityId(ResourceLocation.parse("minecraft:skeleton")));
        assertEquals(-1, idMap.getEntityId(ResourceLocation.parse("minecraft:creeper")));

        assertTrue(warnings.isEmpty(), warnings::toString);
    }

    @Test
    void emptyIdMapFallsBackToLegacyDefaults() {
        PrIdMap empty = PrIdMap.empty();
        assertEquals(1, empty.getBlockId(ResourceLocation.parse("minecraft:stone")));
        assertEquals(-1, empty.getItemId(ResourceLocation.parse("minecraft:iron_sword")));
        assertEquals(-1, empty.getEntityId(ResourceLocation.parse("minecraft:zombie")));
    }

    @Test
    void supportedIrisFeaturesAreAccepted() {
        List<String> warnings = new ArrayList<>();
        String propsText = """
                iris.features.required = CUSTOM_IMAGES SSBO HIGHER_SHADOWCOLOR PER_BUFFER_BLENDING COMPUTE_SHADERS
                """;
        PrShaderProperties props = PrShaderProperties.parse(propsText, Map.of(), warnings::add);
        // Raw list preserves pack declarations
        assertEquals(5, props.requiredIrisFeatures().size());
        assertTrue(props.requiredIrisFeatures().contains("CUSTOM_IMAGES"));
        assertTrue(props.requiredIrisFeatures().contains("SSBO"));

        // Unsupported list is empty because all 5 are natively supported in Pryzma
        assertTrue(props.unsupportedIrisFeatures().isEmpty(), () -> "Expected empty unsupported features, but got: " + props.unsupportedIrisFeatures());
    }

    @Test
    void unknownIrisFeatureIsReportedAsUnsupported() {
        List<String> warnings = new ArrayList<>();
        String propsText = """
                iris.features.required = SSBO UNKNOWN_FUTURE_IRIS_EXTENSION_XYZ
                """;
        PrShaderProperties props = PrShaderProperties.parse(propsText, Map.of(), warnings::add);
        List<String> unsupported = props.unsupportedIrisFeatures();
        assertEquals(1, unsupported.size());
        assertEquals("UNKNOWN_FUTURE_IRIS_EXTENSION_XYZ", unsupported.getFirst());
    }
}
