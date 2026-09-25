package net.pryzma.core.res;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.pryzma.Pryzma;

/**
 * Read access to OptiFine / MCPatcher resources for one resource reload.
 *
 * <p>{@code optifine/} and {@code mcpatcher/} are one logical tree. When both spellings of a
 * path exist, the resource from the higher priority pack wins; inside a single pack
 * {@code optifine/} wins. Callers always see the logical {@code optifine/} location.
 *
 * <p>Parsed properties and images are cached for the lifetime of the instance, which is one
 * reload. Instances are safe to share between the reload worker threads.
 */
public final class PrResources {
    /** A winning resource and the location it was actually read from. */
    private record Found(ResourceLocation physical, Resource resource) {
    }

    private final ResourceManager manager;
    private final Map<String, Integer> packOrder;
    private final Map<ResourceLocation, Optional<Found>> found = new ConcurrentHashMap<>();
    private final Map<ResourceLocation, Optional<PrProperties>> properties = new ConcurrentHashMap<>();
    private final Map<ResourceLocation, Optional<PrImage>> images = new ConcurrentHashMap<>();

    public PrResources(ResourceManager manager) {
        this.manager = manager;
        Map<String, Integer> order = new HashMap<>();
        int index = 0;
        for (PackResources pack : (Iterable<PackResources>) manager.listPacks()::iterator) {
            order.put(pack.packId(), index++);
        }
        this.packOrder = order;
    }

    public ResourceManager manager() {
        return manager;
    }

    /** Pack priority of a resource: larger wins. Unknown packs sort lowest. */
    public int priority(Resource resource) {
        return packOrder.getOrDefault(resource.sourcePackId(), -1);
    }

    /**
     * The winning resource for {@code location}. {@code optifine/} locations also consider the
     * {@code mcpatcher/} spelling; any other location is a plain lookup.
     */
    public Optional<Resource> find(ResourceLocation location) {
        return found(location).map(Found::resource);
    }

    /**
     * Where the winning resource for {@code location} physically lives: the {@code optifine/} or
     * {@code mcpatcher/} spelling. Needed when vanilla code (texture loading) reads it by location.
     */
    public Optional<ResourceLocation> physicalLocation(ResourceLocation location) {
        return found(location).map(Found::physical);
    }

    private Optional<Found> found(ResourceLocation location) {
        return found.computeIfAbsent(PrPaths.logical(location), this::lookup);
    }

    private Optional<Found> lookup(ResourceLocation logical) {
        Optional<Resource> direct = manager.getResource(logical);
        ResourceLocation alias = PrPaths.mcpatcherAlias(logical);
        Optional<Resource> legacy = alias == null ? Optional.empty() : manager.getResource(alias);
        if (legacy.isPresent() && (direct.isEmpty() || priority(legacy.get()) > priority(direct.get()))) {
            return Optional.of(new Found(alias, legacy.get()));
        }
        return direct.map(r -> new Found(logical, r));
    }

    public boolean exists(ResourceLocation location) {
        return find(location).isPresent();
    }

    /**
     * Every logical resource below {@code optifine/<dir>} or {@code mcpatcher/<dir>} whose path
     * ends with {@code suffix}, in any namespace, sorted by path. Values are the winning resources.
     */
    public SortedMap<ResourceLocation, Resource> list(String dir, String suffix) {
        String sub = dir.isEmpty() || dir.endsWith("/") ? dir : dir + "/";
        Map<ResourceLocation, Found> winners = new HashMap<>();
        manager.listResources(PrPaths.OPTIFINE + stripSlash(sub), loc -> loc.getPath().endsWith(suffix))
                .forEach((loc, res) -> winners.put(loc, new Found(loc, res)));
        manager.listResources(PrPaths.MCPATCHER + stripSlash(sub), loc -> loc.getPath().endsWith(suffix))
                .forEach((loc, res) -> {
                    ResourceLocation logical = PrPaths.logical(loc);
                    Found current = winners.get(logical);
                    if (current == null || priority(res) > priority(current.resource())) {
                        winners.put(logical, new Found(loc, res));
                    }
                });
        SortedMap<ResourceLocation, Resource> out = new TreeMap<>();
        for (Map.Entry<ResourceLocation, Found> e : winners.entrySet()) {
            found.putIfAbsent(e.getKey(), Optional.of(e.getValue()));
            out.put(e.getKey(), e.getValue().resource());
        }
        return Collections.unmodifiableSortedMap(out);
    }

    /** One copy of a logical resource, as provided by one pack. */
    public record Entry(ResourceLocation location, Resource resource, int priority) {
    }

    /**
     * Every copy of every logical resource below {@code optifine/<dir>} and {@code mcpatcher/<dir>}:
     * one entry per pack that provides it, highest priority pack first, then by path. This is
     * OptiFine's per-pack loading order, where a lower pack's file still applies wherever the
     * higher pack's file of the same name does not.
     */
    public List<Entry> listAll(String dir, String suffix) {
        String sub = stripSlash(dir.isEmpty() || dir.endsWith("/") ? dir : dir + "/");
        List<Entry> entries = new ArrayList<>();
        for (String root : new String[] {PrPaths.OPTIFINE, PrPaths.MCPATCHER}) {
            manager.listResourceStacks(root + sub, loc -> loc.getPath().endsWith(suffix)).forEach((loc, stack) -> {
                ResourceLocation logical = PrPaths.logical(loc);
                for (Resource r : stack) {
                    entries.add(new Entry(logical, r, priority(r)));
                }
            });
        }
        entries.sort(Comparator.comparingInt(Entry::priority).reversed().thenComparing(Entry::location));
        return entries;
    }

    /** The parsed properties file at {@code location}, cached. */
    public Optional<PrProperties> properties(ResourceLocation location) {
        ResourceLocation logical = PrPaths.logical(location);
        return properties.computeIfAbsent(logical, loc -> find(loc).flatMap(res -> readProperties(loc, res)));
    }

    /** Parses {@code resource} as the properties file at logical {@code location}, cached. */
    public Optional<PrProperties> properties(ResourceLocation location, Resource resource) {
        ResourceLocation logical = PrPaths.logical(location);
        return properties.computeIfAbsent(logical, loc -> readProperties(loc, resource));
    }

    /** Parses one specific copy of a file (see {@link #listAll}); not cached. */
    public Optional<PrProperties> properties(Entry entry) {
        return readProperties(entry.location(), entry.resource());
    }

    /** The decoded image at {@code location}, cached. */
    public Optional<PrImage> image(ResourceLocation location) {
        ResourceLocation logical = PrPaths.logical(location);
        return images.computeIfAbsent(logical, loc -> find(loc).flatMap(res -> readImage(loc, res)));
    }

    private static Optional<PrProperties> readProperties(ResourceLocation logical, Resource resource) {
        try (InputStream in = resource.open()) {
            return Optional.of(PrProperties.parse(logical, decode(in.readAllBytes())));
        } catch (IOException | IllegalArgumentException e) {
            Pryzma.LOGGER.warn("Cannot read {} from {}: {}", logical, resource.sourcePackId(), e.toString());
            return Optional.empty();
        }
    }

    private static Optional<PrImage> readImage(ResourceLocation logical, Resource resource) {
        try (InputStream in = resource.open()) {
            return Optional.of(PrImage.read(in));
        } catch (IOException | IllegalArgumentException e) {
            Pryzma.LOGGER.warn("Cannot read image {} from {}: {}", logical, resource.sourcePackId(), e.toString());
            return Optional.empty();
        }
    }

    /**
     * Text of a properties file: UTF-8 when the bytes are valid UTF-8 (every ASCII file is),
     * otherwise ISO-8859-1, the encoding {@code Properties.load(InputStream)} and OptiFine assume.
     * Read as ISO-8859-1, a name written in UTF-8 would never match what the game displays.
     */
    public static String decode(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, StandardCharsets.ISO_8859_1);
        }
    }

    private static String stripSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
