package net.pryzma.render;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringReader;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.pryzma.PryzmaConfig;
import org.junit.jupiter.api.Test;

class PrNaturalTexturesTest {

    @Test
    void parseRuleTypes() {
        PrNaturalProperties p4 = PrNaturalProperties.parse("4");
        assertNotNull(p4);
        assertEquals(4, p4.rotation);
        assertFalse(p4.flip);
        assertTrue(p4.isValid());

        PrNaturalProperties p2 = PrNaturalProperties.parse("2");
        assertNotNull(p2);
        assertEquals(2, p2.rotation);
        assertFalse(p2.flip);
        assertTrue(p2.isValid());

        PrNaturalProperties pf = PrNaturalProperties.parse("F");
        assertNotNull(pf);
        assertEquals(1, pf.rotation);
        assertTrue(pf.flip);
        assertTrue(pf.isValid());

        PrNaturalProperties p4f = PrNaturalProperties.parse("4F");
        assertNotNull(p4f);
        assertEquals(4, p4f.rotation);
        assertTrue(p4f.flip);
        assertTrue(p4f.isValid());

        PrNaturalProperties p2f = PrNaturalProperties.parse("2F");
        assertNotNull(p2f);
        assertEquals(2, p2f.rotation);
        assertTrue(p2f.flip);
        assertTrue(p2f.isValid());

        assertNull(PrNaturalProperties.parse(null));
        assertNull(PrNaturalProperties.parse("invalid"));
        assertNull(PrNaturalProperties.parse("3"));
        assertNull(PrNaturalProperties.parse(""));
    }

    @Test
    void parsePropertiesFile() {
        String testConfig = """
            # Comments
            granite = 2F
            obsidian = 4F
            clay = 4
            grass_block_side = F
            """;
        Map<String, PrNaturalProperties> rules = PrNaturalTextures.parseRules(new StringReader(testConfig));
        assertEquals(16, rules.size()); // 4 keys * 4 aliases each (raw, block/, minecraft:, minecraft:block/)

        assertTrue(rules.containsKey("granite"));
        assertTrue(rules.containsKey("block/granite"));
        assertTrue(rules.containsKey("minecraft:granite"));
        assertTrue(rules.containsKey("minecraft:block/granite"));

        PrNaturalProperties granite = rules.get("minecraft:block/granite");
        assertEquals(2, granite.rotation);
        assertTrue(granite.flip);

        PrNaturalProperties obsidian = rules.get("obsidian");
        assertEquals(4, obsidian.rotation);
        assertTrue(obsidian.flip);
    }

    @Test
    void defaultPropertiesLoad() {
        Map<String, PrNaturalProperties> defaults = PrNaturalTextures.loadDefaultRules();
        assertFalse(defaults.isEmpty());

        // Verify key textures from OptiFine defaults
        assertNotNull(defaults.get("minecraft:block/grass_block_side"));
        assertNotNull(defaults.get("minecraft:block/dirt_path_top"));
        assertNotNull(defaults.get("minecraft:block/snow"));
        assertNotNull(defaults.get("minecraft:block/granite"));
        assertNotNull(defaults.get("minecraft:block/oak_log"));
        assertNotNull(defaults.get("minecraft:block/oak_leaves"));
        assertNotNull(defaults.get("minecraft:block/diamond_ore"));
        assertNotNull(defaults.get("minecraft:block/netherrack"));
        assertNotNull(defaults.get("minecraft:block/obsidian"));
    }

    @Test
    void hashDeterministicAndConsistent() {
        BlockPos pos1 = new BlockPos(100, 64, -200);
        int side1 = 1; // UP
        int hash1 = PrNaturalTextures.getRandom(pos1, side1);
        int hash2 = PrNaturalTextures.getRandom(pos1, side1);
        assertEquals(hash1, hash2, "Identical position and face must yield identical hash");

        BlockPos pos2 = new BlockPos(101, 64, -200);
        int hashDiffPos = PrNaturalTextures.getRandom(pos2, side1);
        assertNotEquals(hash1, hashDiffPos, "Adjacent position must yield different hash");

        int hashDiffSide = PrNaturalTextures.getRandom(pos1, 0); // DOWN
        assertNotEquals(hash1, hashDiffSide, "Different face must yield different hash");
    }

    @Test
    void optifineIntHashMatches() {
        int h0 = PrNaturalTextures.intHash(0);
        int h1 = PrNaturalTextures.intHash(1);
        int h37 = PrNaturalTextures.intHash(37);
        assertEquals(1062685034, h0);
        assertEquals(663891101, h1);
        assertNotEquals(h0, h1);
        assertNotEquals(h1, h37);
    }

    @Test
    void disabledReturnsOriginalQuads() {
        PryzmaConfig.prNaturalTextures = false;
        List<net.minecraft.client.renderer.block.model.BakedQuad> quads = Collections.emptyList();
        assertSame(quads, PrNaturalTextures.processQuads(null, new BlockPos(0, 0, 0), Direction.UP, quads));
    }
}
