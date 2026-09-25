package net.pryzma.core.match;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

class PrMatchParsingTest {
    @Test
    void rangesAcceptOptifineSyntax() {
        PrRangeList r = PrRangeList.parse("0-11 16-27,32");
        assertTrue(r.contains(0));
        assertTrue(r.contains(11));
        assertFalse(r.contains(12));
        assertTrue(r.contains(27));
        assertTrue(r.contains(32));
        assertFalse(r.contains(33));
        assertTrue(PrRangeList.parse("5-").contains(1_000_000), "open upper bound");
        assertNull(PrRangeList.parse("1-a"));
        assertNull(PrRangeList.parse("-3"), "unsigned lists reject negatives");
    }

    @Test
    void signedRangesAcceptNegativeBounds() {
        PrRangeList r = PrRangeList.parseSigned("-64-0 (-10)-(-5) 100");
        assertTrue(r.contains(-64));
        assertTrue(r.contains(0));
        assertFalse(r.contains(1));
        assertTrue(r.contains(-7));
        assertTrue(r.contains(100));
        assertNull(PrRangeList.parseSigned("1=2"));
    }

    @Test
    void reversedRangesAreSwappedLikeOptifineRangeInt() {
        PrRangeList heights = PrRangeList.parseSigned("100-99");
        assertTrue(heights.contains(99));
        assertTrue(heights.contains(100));
        assertFalse(heights.contains(101));
        PrRangeList days = PrRangeList.parse("9-3");
        assertTrue(days.contains(3));
        assertTrue(days.contains(9));
        assertFalse(days.contains(2));
    }

    @Test
    void biomeNamesMatchExactlyAndCompactly() {
        PrBiomeMatcher m = PrBiomeMatcher.parse("plains Birch_Forest minecraft:dark_forest othermod:MistyPeaks");
        assertTrue(m.matches(ResourceLocation.withDefaultNamespace("plains")));
        assertTrue(m.matches(ResourceLocation.withDefaultNamespace("birch_forest")));
        assertTrue(m.matches(ResourceLocation.withDefaultNamespace("dark_forest")));
        assertTrue(m.matches(ResourceLocation.fromNamespaceAndPath("othermod", "misty_peaks")));
        assertFalse(m.matches(ResourceLocation.withDefaultNamespace("desert")));
        assertFalse(m.matches(ResourceLocation.fromNamespaceAndPath("othermod", "plains")));
    }

    @Test
    void biomeNegationAndLegacyNames() {
        PrBiomeMatcher not = PrBiomeMatcher.parse("!ocean deep_ocean");
        assertFalse(not.matches(ResourceLocation.withDefaultNamespace("ocean")));
        assertTrue(not.matches(ResourceLocation.withDefaultNamespace("plains")));
        PrBiomeMatcher legacy = PrBiomeMatcher.parse("IcePlains Swampland extreme_hills Roofed_Forest");
        assertTrue(legacy.matches(ResourceLocation.withDefaultNamespace("snowy_plains")));
        assertTrue(legacy.matches(ResourceLocation.withDefaultNamespace("swamp")));
        assertTrue(legacy.matches(ResourceLocation.withDefaultNamespace("windswept_hills")));
        assertTrue(legacy.matches(ResourceLocation.withDefaultNamespace("dark_forest")));
        assertNull(PrBiomeMatcher.parse(null));
    }

    @Test
    void legacyTableLineParsing() {
        Map<Integer, Map<Integer, List<PrLegacyBlocks.ModernState>>> byId = new HashMap<>();
        Map<String, Integer> names = new HashMap<>();
        PrLegacyBlocks.parseLine("{Name:'minecraft:oak_log',Properties:{axis:'x'}} "
                + "{Name:'minecraft:log',Properties:{axis:'x',variant:'oak'}} 17 4", byId, names);
        PrLegacyBlocks.parseLine("{Name:'minecraft:grass'} {Name:'minecraft:tallgrass',Properties:{type:'tall_grass'}} 31 1",
                byId, names);
        PrLegacyBlocks.parseLine("# header", byId, names);
        PrLegacyBlocks.ModernState log = byId.get(17).get(4).get(0);
        assertEquals("minecraft:oak_log", log.block());
        assertEquals(Map.of("axis", "x"), log.properties());
        assertEquals(17, names.get("minecraft:log"));
        assertEquals("minecraft:short_grass", byId.get(31).get(1).get(0).block(), "1.13 names are updated");
    }

    @Test
    void bundledLegacyTableCoversMcpatcherGlass() {
        PrLegacyBlocks table = PrLegacyBlocks.load();
        assertEquals("minecraft:glass", table.states(20, null).get(0).block());
        assertEquals("minecraft:white_stained_glass", table.states(95, PrRangeList.parse("0")).get(0).block());
        assertEquals("minecraft:light_gray_stained_glass_pane", table.states(160, PrRangeList.parse("8")).get(0).block());
        assertEquals(16, table.states(95, null).size());
        assertEquals(95, table.idOf("minecraft:stained_glass"));
    }
}
