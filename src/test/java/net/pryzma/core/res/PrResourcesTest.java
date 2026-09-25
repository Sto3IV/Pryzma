package net.pryzma.core.res;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.Resource;

class PrResourcesTest {
    @TempDir
    Path tmp;

    private static ResourceLocation mc(String path) {
        return ResourceLocation.withDefaultNamespace(path);
    }

    private PathPackResources pack(String id, Map<String, String> files) throws IOException {
        Path root = tmp.resolve(id);
        for (Map.Entry<String, String> f : files.entrySet()) {
            Path file = root.resolve(f.getKey());
            Files.createDirectories(file.getParent());
            Files.writeString(file, f.getValue(), StandardCharsets.ISO_8859_1);
        }
        Files.createDirectories(root);
        return new PathPackResources(new PackLocationInfo(id, Component.literal(id), PackSource.DEFAULT, Optional.empty()), root);
    }

    private PrResources resources(PackResources... lowToHigh) {
        return new PrResources(new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(lowToHigh)));
    }

    @Test
    void mcpatcherPathIsFoundUnderItsOptifineName() throws IOException {
        PrResources res = resources(pack("legacy", Map.of("assets/minecraft/mcpatcher/color.properties", "lilypad=208030")));
        Optional<PrProperties> props = res.properties(mc("optifine/color.properties"));
        assertTrue(props.isPresent());
        assertEquals("208030", props.get().get("lilypad"));
        assertEquals(mc("optifine/color.properties"), props.get().location(), "callers see the logical location");
    }

    @Test
    void higherPriorityPackWinsAcrossSpellings() throws IOException {
        PackResources low = pack("low", Map.of("assets/minecraft/optifine/color.properties", "lilypad=111111"));
        PackResources high = pack("high", Map.of("assets/minecraft/mcpatcher/color.properties", "lilypad=222222"));
        assertEquals("222222", resources(low, high).properties(mc("optifine/color.properties")).orElseThrow().get("lilypad"));
        assertEquals("111111", resources(high, low).properties(mc("optifine/color.properties")).orElseThrow().get("lilypad"));
        assertEquals(mc("mcpatcher/color.properties"), resources(low, high).physicalLocation(mc("optifine/color.properties")).orElseThrow());
        assertEquals(mc("optifine/color.properties"), resources(high, low).physicalLocation(mc("optifine/color.properties")).orElseThrow());
    }

    @Test
    void optifineWinsInsideOnePack() throws IOException {
        PackResources both = pack("both", Map.of(
                "assets/minecraft/optifine/color.properties", "lilypad=333333",
                "assets/minecraft/mcpatcher/color.properties", "lilypad=444444"));
        assertEquals("333333", resources(both).properties(mc("optifine/color.properties")).orElseThrow().get("lilypad"));
    }

    @Test
    void listingMergesBothTreesAndAllNamespaces() throws IOException {
        PackResources low = pack("low", Map.of(
                "assets/minecraft/optifine/sky/world0/sky1.properties", "source=./a.png",
                "assets/minecraft/optifine/sky/world0/sky2.properties", "source=./b.png"));
        PackResources high = pack("high", Map.of(
                "assets/minecraft/mcpatcher/sky/world0/sky2.properties", "source=./c.png",
                "assets/minecraft/mcpatcher/sky/world0/sky3.properties", "source=./d.png",
                "assets/othermod/optifine/sky/world0/sky9.properties", "source=./e.png",
                "assets/minecraft/mcpatcher/sky/world0/cloud.png", "not a properties file"));
        PrResources res = resources(low, high);
        Map<ResourceLocation, Resource> found = res.list("sky", ".properties");
        assertEquals(List.of(
                ResourceLocation.fromNamespaceAndPath("othermod", "optifine/sky/world0/sky9.properties"),
                mc("optifine/sky/world0/sky1.properties"),
                mc("optifine/sky/world0/sky2.properties"),
                mc("optifine/sky/world0/sky3.properties")).stream().sorted().toList(), List.copyOf(found.keySet()));
        assertEquals("high", found.get(mc("optifine/sky/world0/sky2.properties")).sourcePackId());
        assertEquals("./c.png", res.properties(mc("optifine/sky/world0/sky2.properties")).orElseThrow().get("source"));
    }

    @Test
    void missingResourceIsEmpty() throws IOException {
        PrResources res = resources(pack("empty", Map.of()));
        assertFalse(res.properties(mc("optifine/color.properties")).isPresent());
        assertFalse(res.exists(mc("optifine/lightmap/world0.png")));
    }

    @Test
    void propertiesParseLikeJavaProperties() {
        PrProperties p = PrProperties.parse(mc("optifine/ctm/glass/glass.properties"),
                "# comment\nmatchBlocks = glass  \nmethod:ctm\ntiles=0-11 \\\n  16-27\nname=a\\u0020b\n");
        assertEquals("glass", p.get("matchBlocks"));
        assertEquals("ctm", p.get("method"));
        assertEquals("0-11 16-27", p.get("tiles"));
        assertEquals("a b", p.get("name"));
        assertEquals(List.of("matchBlocks", "method", "tiles", "name"), List.copyOf(p.keys()), "declaration order is kept");
        assertEquals("optifine/ctm/glass", p.basePath());
        assertEquals("glass", p.name());
    }

    @Test
    void propertiesTypedAccessorsFollowOptifine() {
        PrProperties p = PrProperties.parse(mc("optifine/x.properties"),
                "a=12\nb=-3\nc=xyz\nd=TRUE\ne=ff8800\nf=2.5\n");
        assertEquals(12, p.getInt("a", 7));
        assertEquals(7, p.getInt("b", 7), "parseInt rejects negatives");
        assertEquals(-3, p.getIntSigned("b", 7));
        assertEquals(7, p.getInt("c", 7));
        assertTrue(p.getBool("d", false));
        assertEquals(0xFF8800, p.getColor("e", -1));
        assertEquals(-1, p.getColor("c", -1));
        assertEquals(2.5F, p.getFloat("f", 0F));
    }

    @Test
    void pathResolutionMatchesOptifine() {
        String base = "optifine/ctm/glass";
        assertEquals("optifine/ctm/glass/0.png", PrPaths.resolve("./0.png", base));
        assertEquals("optifine/sky/stars.png", PrPaths.resolve("~/sky/stars.png", base));
        assertEquals("optifine/sky/stars.png", PrPaths.resolve("/~/sky/stars.png", base));
        assertEquals("optifine/misc/x.png", PrPaths.resolve("/misc/x.png", base));
        assertEquals("textures/block/stone.png", PrPaths.resolve("assets/minecraft/textures/block/stone.png", base));
        assertEquals("textures/block/stone.png", PrPaths.resolve("textures/block/stone.png", base));
        assertEquals("mod:textures/x.png", PrPaths.resolve("mod:textures/x.png", base));
    }

    @Test
    void locationResolutionFoldsMcpatcherAndKeepsNamespace() {
        ResourceLocation props = ResourceLocation.fromNamespaceAndPath("othermod", "optifine/sky/world0/sky1.properties");
        assertEquals(ResourceLocation.fromNamespaceAndPath("othermod", "optifine/sky/world0/a.png"),
                PrPaths.resolveLocation("./a.png", props));
        assertEquals(mc("textures/b.png"), PrPaths.resolveLocation("minecraft:textures/b.png", props));
        assertEquals(mc("optifine/c.png"), PrPaths.resolveLocation("minecraft:mcpatcher/c.png", props));
        assertNull(PrPaths.resolveLocation("Bad Path.png", props), "invalid characters give no location");
    }
}
