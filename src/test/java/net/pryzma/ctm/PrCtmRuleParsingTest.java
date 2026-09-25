package net.pryzma.ctm;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;
import net.pryzma.core.res.PrProperties;

/** CTM properties parsing against OptiFine's ConnectedProperties rules. */
class PrCtmRuleParsingTest {
    private static final PrProperties GLASS = PrProperties.parse(mc("optifine/ctm/glass/glass.properties"), "");

    private static ResourceLocation mc(String path) {
        return ResourceLocation.withDefaultNamespace(path);
    }

    private static ResourceLocation sprite(String tile) {
        return PrCtmRule.tile(tile, GLASS).sprite();
    }

    @Test
    void tileNamesResolveLikeOptifine() {
        PrCtmRule.Tile t = PrCtmRule.tile("0", GLASS);
        assertEquals(mc("optifine/ctm/glass/0"), t.sprite());
        assertEquals(mc("optifine/ctm/glass/0.png"), t.file());
        assertEquals(mc("optifine/ctm/glass/5"), sprite("5.png"));
        assertEquals(mc("optifine/ctm/glass/3"), sprite("./3"));
        assertEquals(mc("block/stone"), sprite("textures/block/stone"));
        assertEquals(mc("block/dirt"), sprite("assets/minecraft/textures/block/dirt.png"));
        // A bare path without textures/ or optifine/ is relative to the properties file.
        assertEquals(mc("optifine/ctm/glass/block/stone"), sprite("block/stone"));
        assertEquals(mc("optifine/ctm/common/1"), sprite("~/ctm/common/1"));
        assertEquals(mc("optifine/ctm/x"), sprite("/ctm/x"));
        assertSame(PrCtmRule.Tile.SKIP, PrCtmRule.tile("<skip>", GLASS));
        assertSame(PrCtmRule.Tile.DEFAULT, PrCtmRule.tile("<default>.png", GLASS));
    }

    @Test
    void mcpatcherAndModNamespacesKeepTheirTiles() {
        PrProperties mcp = PrProperties.parse(mc("mcpatcher/ctm/glass/glass.properties"), "");
        PrCtmRule.Tile t = PrCtmRule.tile("7", mcp);
        assertEquals(mc("optifine/ctm/glass/7"), t.sprite(), "mcpatcher tiles share the optifine sprite names");
        assertEquals(mc("optifine/ctm/glass/7.png"), t.file());
        PrProperties mod = PrProperties.parse(ResourceLocation.fromNamespaceAndPath("mymod", "optifine/ctm/x/x.properties"), "");
        assertEquals(ResourceLocation.fromNamespaceAndPath("mymod", "optifine/ctm/x/0"), PrCtmRule.tile("0", mod).sprite());
    }

    @Test
    void tileListsFollowOptifineTokenizing() {
        List<String> warnings = new ArrayList<>();
        List<PrCtmRule.Tile> tiles = PrCtmRule.parseTiles("0-3 5 7-6 x-y 1--2 9", GLASS, warnings::add);
        List<String> names = tiles.stream().map(t -> t.sprite().getPath().substring("optifine/ctm/glass/".length())).toList();
        // The reversed range is skipped, "x-y" is a name, and "1--2" is a range once empty parts drop.
        assertEquals(List.of("0", "1", "2", "3", "5", "x-y", "1", "2", "9"), names);
        assertEquals(1, warnings.size(), warnings.toString());
        assertNull(PrCtmRule.parseTiles("  ", GLASS, warnings::add));
    }

    @Test
    void intListsFollowOptifine() {
        assertArrayEquals(new int[] {1, 2, 3, 4, 6}, PrCtmRule.parseIntList("1 2-4 x 5-3 +6 -2"));
        assertArrayEquals(new int[] {}, PrCtmRule.parseIntList("abc"));
        assertNull(PrCtmRule.parseIntList(null));
    }

    @Test
    void facesAndMethods() {
        assertEquals(62, PrCtmRule.parseFaces("sides top"));
        assertEquals(33, PrCtmRule.parseFaces("Bottom,EAST"));
        assertEquals(63, PrCtmRule.parseFaces("all"));
        assertEquals(6, PrCtmRule.parseFaces("north up"));
        assertEquals(-1, PrCtmRule.parseFaces("sides bogus"));
        assertEquals(PrCtmMethod.CTM, PrCtmMethod.parse(null));
        assertEquals(PrCtmMethod.CTM, PrCtmMethod.parse("glass"));
        assertEquals(PrCtmMethod.HORIZONTAL, PrCtmMethod.parse(" bookshelf "));
        assertEquals(PrCtmMethod.HORIZONTAL_VERTICAL, PrCtmMethod.parse("h+v"));
        assertEquals(PrCtmMethod.OVERLAY_CTM, PrCtmMethod.parse("overlay_ctm"));
        assertNull(PrCtmMethod.parse("Horizontal"), "OptiFine method names are case sensitive");
    }

    @Test
    void matchTilesAreAtlasNames() {
        assertEquals(mc("block/stone"), PrCtmRule.spriteId("stone", GLASS));
        assertEquals(mc("block/stone"), PrCtmRule.spriteId("block/stone", GLASS), "not relative, unlike tiles");
        assertEquals(mc("block/stone"), PrCtmRule.spriteId("textures/block/stone.png", GLASS));
        assertEquals(mc("block/dirt"), PrCtmRule.spriteId("minecraft:block/dirt", GLASS));
        assertEquals(mc("optifine/ctm/foo/1"), PrCtmRule.spriteId("optifine/ctm/foo/1", GLASS));
        assertEquals(mc("optifine/ctm/glass/1"), PrCtmRule.spriteId("./1", GLASS));
        assertEquals(List.of(mc("block/stone"), mc("block/dirt")), PrCtmRule.parseSpriteList(" stone  block/dirt ", GLASS));
    }

    // ------------------------------------------------------------------ whole files

    private static Optional<PrCtmRule> rule(String path, String text, Predicate<ResourceLocation> sprites, List<String> warnings) {
        return PrCtmRule.parse(PrProperties.parse(mc(path), text), sprites, warnings::add);
    }

    private static Optional<PrCtmRule> rule(String text, List<String> warnings) {
        return rule("optifine/ctm/test/test.properties", text, id -> false, warnings);
    }

    @Test
    void tilesAreRequiredForEveryMethod() {
        List<String> w = new ArrayList<>();
        assertTrue(rule("method=ctm\nmatchTiles=glass", w).isEmpty());
        assertTrue(w.get(w.size() - 1).startsWith("No tiles specified"), w.toString());
        assertTrue(rule("method=top\nmatchTiles=glass", w).isEmpty());
        assertTrue(rule("method=ctm\nmatchTiles=glass\ntiles=0-46", w).isPresent());
    }

    @Test
    void tileCountsFollowTheMethod() {
        List<String> w = new ArrayList<>();
        assertTrue(rule("method=ctm\nmatchTiles=a\ntiles=0-45", w).isEmpty(), "ctm needs 47");
        assertTrue(rule("method=horizontal\nmatchTiles=a\ntiles=0-4", w).isEmpty(), "horizontal needs exactly 4");
        assertTrue(rule("method=h+v\nmatchTiles=a\ntiles=0-6", w).isPresent());
        assertTrue(rule("method=v+h\nmatchTiles=a\ntiles=0-5", w).isEmpty());
        assertTrue(rule("method=fixed\nmatchTiles=a\ntiles=0 1", w).isEmpty());
        assertTrue(rule("method=repeat\nmatchTiles=a\ntiles=0-3\nwidth=2\nheight=2", w).isPresent());
        assertTrue(rule("method=repeat\nmatchTiles=a\ntiles=0-2\nwidth=2\nheight=2", w).isEmpty());
        assertTrue(rule("method=overlay\nmatchTiles=a\ntiles=0-16", w).isPresent());
        assertTrue(rule("method=overlay\nmatchTiles=a\ntiles=0-16\nlayer=solid", w).isEmpty(), "overlays cannot be solid");
    }

    @Test
    void invalidKeysRejectTheFile() {
        List<String> w = new ArrayList<>();
        assertTrue(rule("method=random\nmatchTiles=a\ntiles=0-3\nsymmetry=diagonal", w).isEmpty());
        assertTrue(rule("method=random\nmatchTiles=a\ntiles=0-3\nconnect=tiles", w).isEmpty());
        assertTrue(rule("method=random\nmatchTiles=a\ntiles=0-3\nfaces=sides roof", w).isEmpty());
        assertTrue(rule("method=random\nmatchTiles=a\ntiles=0-3\nrenderPass=1", w).isEmpty());
        assertTrue(rule("method=random\nmatchTiles=a\ntiles=0-3\nrandomLoops=10", w).isEmpty());
        assertTrue(rule("method=bogus\nmatchTiles=a\ntiles=0", w).isEmpty());
        assertTrue(rule("method=random\nmatchTiles=a\ntiles=0-3\nmetadata=1", w).isEmpty(), "metadata needs legacy ids");
    }

    @Test
    void randomWeightsExpandWithTheAverage() {
        PrCtmRule r = rule("method=random\nmatchTiles=a\ntiles=0-3\nweights=2 4", new ArrayList<>()).orElseThrow();
        // 2, 4, then the average 3 for the rest: cumulative 2, 6, 9, 12.
        assertArrayEquals(new int[] {2, 6, 9, 12}, r.sumWeights);
        assertEquals(12, r.sumAllWeights);
        PrCtmRule trimmed = rule("method=random\nmatchTiles=a\ntiles=0-1\nweights=1 1 5", new ArrayList<>()).orElseThrow();
        assertArrayEquals(new int[] {1, 2}, trimmed.sumWeights);
        PrCtmRule zero = rule("method=random\nmatchTiles=a\ntiles=0-1\nweights=0 0", new ArrayList<>()).orElseThrow();
        assertEquals(1, zero.sumAllWeights, "a zero sum falls back to 1");
    }

    @Test
    void matchTilesComeFromTheFileNameWhenNothingIsGiven() {
        List<String> w = new ArrayList<>();
        Optional<PrCtmRule> r = rule("optifine/ctm/glass/glass.properties", "method=ctm\ntiles=0-46",
                id -> id.equals(mc("block/glass")), w);
        assertEquals(List.of(mc("block/glass")), r.orElseThrow().matchTiles());
        assertEquals(PrCtmRule.CONNECT_TILE, r.orElseThrow().connect);
        assertTrue(rule("optifine/ctm/glass/glass.properties", "method=ctm\ntiles=0-46", id -> false, w).isEmpty());
    }

    @Test
    void ctmIndexOverridesAreBoundedByTheTileList() {
        List<String> w = new ArrayList<>();
        PrCtmRule r = rule("method=ctm\nmatchTiles=a\ntiles=0-46\nctm.5=2\nctm.47=1\nctm.3=99", w).orElseThrow();
        assertEquals(2, r.ctmTileIndexes[5]);
        assertEquals(-1, r.ctmTileIndexes[3]);
        assertEquals(2, w.size(), w.toString());
    }
}
