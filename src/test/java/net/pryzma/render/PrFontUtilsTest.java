package net.pryzma.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.pryzma.PryzmaConfig;

class PrFontUtilsTest {
    private boolean savedCustomFonts;

    @BeforeEach
    void setUp() {
        savedCustomFonts = PryzmaConfig.prCustomFonts;
    }

    @AfterEach
    void tearDown() {
        PryzmaConfig.prCustomFonts = savedCustomFonts;
    }

    @Test
    void testHdFontLocationResolvesOptifineWhenEnabled() {
        PryzmaConfig.prCustomFonts = true;
        TestResourceManager rm = new TestResourceManager();
        ResourceLocation optifineAscii = ResourceLocation.fromNamespaceAndPath("minecraft", "optifine/font/ascii.png");
        rm.registerResource(optifineAscii);

        ResourceLocation vanillaLoc = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/font/ascii.png");
        ResourceLocation resolved = PrFontUtils.getHdFontLocation(vanillaLoc, rm);

        assertEquals(optifineAscii, resolved);
    }

    @Test
    void testHdFontLocationResolvesMcpatcherWhenOptifineMissing() {
        PryzmaConfig.prCustomFonts = true;
        TestResourceManager rm = new TestResourceManager();
        ResourceLocation mcpatcherAscii = ResourceLocation.fromNamespaceAndPath("minecraft", "mcpatcher/font/ascii.png");
        rm.registerResource(mcpatcherAscii);

        ResourceLocation vanillaLoc = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/font/ascii.png");
        ResourceLocation resolved = PrFontUtils.getHdFontLocation(vanillaLoc, rm);

        assertEquals(mcpatcherAscii, resolved);
    }

    @Test
    void testHdFontLocationBypassedWhenCustomFontsDisabled() {
        PryzmaConfig.prCustomFonts = false;
        TestResourceManager rm = new TestResourceManager();
        rm.registerResource(ResourceLocation.fromNamespaceAndPath("minecraft", "optifine/font/ascii.png"));
        rm.registerResource(ResourceLocation.fromNamespaceAndPath("minecraft", "mcpatcher/font/ascii.png"));

        ResourceLocation vanillaLoc = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/font/ascii.png");
        ResourceLocation resolved = PrFontUtils.getHdFontLocation(vanillaLoc, rm);

        assertEquals(vanillaLoc, resolved);
    }

    @Test
    void testFilterCustomFontResourcesWhenDisabled() {
        PryzmaConfig.prCustomFonts = false;

        Resource vanillaRes = createDummyResource("vanilla");
        Resource modRes = createDummyResource("mod:example_mod");
        Resource customPackRes = createDummyResource("file/Pony Minecraft текстуры(DO NOT DISABLE)");
        Resource otherPackRes = createDummyResource("file/fantasy_pack.zip");

        List<Resource> all = List.of(vanillaRes, modRes, customPackRes, otherPackRes);
        List<Resource> filtered = PrFontUtils.filterCustomFontResources(all);

        assertEquals(2, filtered.size());
        assertTrue(filtered.contains(vanillaRes));
        assertTrue(filtered.contains(modRes));
        assertFalse(filtered.contains(customPackRes));
        assertFalse(filtered.contains(otherPackRes));
    }

    @Test
    void testFilterCustomFontResourcesPreservesAllWhenEnabled() {
        PryzmaConfig.prCustomFonts = true;

        Resource vanillaRes = createDummyResource("vanilla");
        Resource customPackRes = createDummyResource("file/Pony Minecraft текстуры(DO NOT DISABLE)");

        List<Resource> all = List.of(vanillaRes, customPackRes);
        List<Resource> result = PrFontUtils.filterCustomFontResources(all);

        assertEquals(2, result.size());
        assertTrue(result.contains(vanillaRes));
        assertTrue(result.contains(customPackRes));
    }

    @Test
    void testIsVanillaPack() {
        assertTrue(PrFontUtils.isVanillaPack(createDummyResource("vanilla")));
        assertTrue(PrFontUtils.isVanillaPack(createDummyResource("programmer_art")));
        assertTrue(PrFontUtils.isVanillaPack(createDummyResource("high_contrast")));
        assertTrue(PrFontUtils.isVanillaPack(createDummyResource("mod_resources")));
        assertTrue(PrFontUtils.isVanillaPack(createDummyResource("mod:pryzma")));

        assertFalse(PrFontUtils.isVanillaPack(createDummyResource("file/Pony Minecraft текстуры(DO NOT DISABLE)")));
        assertFalse(PrFontUtils.isVanillaPack(createDummyResource("file/custom.zip")));
        assertFalse(PrFontUtils.isVanillaPack(null));
    }

    @Test
    void testReloadFontsDoesNotCrashWhenHeadless() {
        // Minecraft.getInstance() is null in this headless test environment.
        // reloadFonts() must safely handle null and exit without throwing.
        PrFontUtils.reloadFonts();
    }

    private static Resource createDummyResource(String packId) {
        PackResources pack = new DummyPackResources(packId);
        IoSupplier<InputStream> stream = () -> new ByteArrayInputStream(new byte[0]);
        return new Resource(pack, stream);
    }

    private static class DummyPackResources implements PackResources {
        private final String packId;

        DummyPackResources(String packId) {
            this.packId = packId;
        }

        @Override
        public String packId() {
            return packId;
        }

        @Override
        public PackLocationInfo location() {
            return null;
        }

        @Override
        public IoSupplier<InputStream> getRootResource(String... elements) {
            return null;
        }

        @Override
        public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
            return null;
        }

        @Override
        public void listResources(PackType type, String namespace, String path, ResourceOutput output) {}

        @Override
        public Set<String> getNamespaces(PackType type) {
            return Collections.emptySet();
        }

        @Override
        public <T> T getMetadataSection(MetadataSectionSerializer<T> deserializer) {
            return null;
        }

        @Override
        public void close() {}
    }

    private static class TestResourceManager implements ResourceManager {
        private final Map<ResourceLocation, Resource> resources = new HashMap<>();

        void registerResource(ResourceLocation loc) {
            resources.put(loc, createDummyResource("test_pack"));
        }

        @Override
        public Optional<Resource> getResource(ResourceLocation location) {
            return Optional.ofNullable(resources.get(location));
        }

        @Override
        public Set<String> getNamespaces() {
            return Collections.emptySet();
        }

        @Override
        public List<Resource> getResourceStack(ResourceLocation location) {
            Resource r = resources.get(location);
            return r != null ? List.of(r) : Collections.emptyList();
        }

        @Override
        public Map<ResourceLocation, Resource> listResources(String path, Predicate<ResourceLocation> filter) {
            return Collections.emptyMap();
        }

        @Override
        public Map<ResourceLocation, List<Resource>> listResourceStacks(String path, Predicate<ResourceLocation> filter) {
            return Collections.emptyMap();
        }

        @Override
        public Stream<PackResources> listPacks() {
            return Stream.empty();
        }

    }
}
