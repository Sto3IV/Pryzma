package net.pryzma.ctm;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.pryzma.Pryzma;
import net.pryzma.core.res.PrProperties;
import net.pryzma.core.res.PrResources;

/**
 * Collects the CTM rules of the current resource packs and the tile textures they need.
 *
 * <p>Order follows OptiFine: the highest priority pack first, files by path inside a pack, and
 * Pryzma's built-in glass, bookshelf and sandstone rules last, and only for textures that no pack
 * replaces.
 */
public final class PrCtmLoader {
    /** Built-in rules: base texture that must be vanilla, and the rule file below {@code ctm_default/}. */
    private static final String[][] DEFAULTS = defaults();

    /** Loaded rules plus every tile sprite to stitch, with the file it is read from. */
    public record Result(List<PrCtmRule> rules, Map<ResourceLocation, Resource> sprites) {
        public static final Result EMPTY = new Result(List.of(), Map.of());
    }

    private PrCtmLoader() {
    }

    public static Result load(ResourceManager manager) {
        long start = System.nanoTime();
        PrResources res = new PrResources(manager);
        Predicate<ResourceLocation> spriteExists = id -> manager.getResource(
                id.withPath("textures/" + id.getPath() + ".png")).isPresent();
        List<String> warnings = new ArrayList<>();
        List<PrCtmRule> rules = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (PrResources.Entry entry : res.listAll("ctm", ".properties")) {
            if (!seen.add(entry.priority() + "|" + entry.location())) {
                continue;
            }
            res.properties(entry)
                    .flatMap(p -> PrCtmRule.parse(p, spriteExists, warnings::add))
                    .ifPresent(rules::add);
        }
        int packRules = rules.size();
        for (String[] d : DEFAULTS) {
            String variant = defaultVariant(manager, d[0]);
            if (variant == null) {
                continue;
            }
            ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(Pryzma.MODID, "ctm_default/" + variant + d[1]);
            Optional<Resource> r = manager.getResource(loc);
            r.flatMap(resource -> res.properties(loc, resource))
                    .flatMap(p -> PrCtmRule.parse(p, spriteExists, warnings::add))
                    .ifPresent(rules::add);
        }

        Map<ResourceLocation, Resource> sprites = new LinkedHashMap<>();
        List<PrCtmRule> usable = new ArrayList<>(rules.size());
        int usablePack = 0;
        Map<ResourceLocation, Resource> found = new LinkedHashMap<>();
        for (int i = 0; i < rules.size(); i++) {
            PrCtmRule rule = rules.get(i);
            // A rule with a missing tile is dropped whole, and none of its tiles are stitched.
            found.clear();
            boolean missing = false;
            for (PrCtmRule.Tile tile : rule.tiles) {
                if (tile.sprite() == null || sprites.containsKey(tile.sprite()) || found.containsKey(tile.sprite())) {
                    continue;
                }
                Optional<Resource> file = res.find(tile.file());
                if (file.isEmpty()) {
                    warnings.add("Tile not found: " + tile.file() + " in " + rule.source);
                    missing = true;
                    break;
                }
                found.put(tile.sprite(), file.get());
            }
            if (!missing) {
                sprites.putAll(found);
                usable.add(rule);
                if (i < packRules) {
                    usablePack++;
                }
            }
        }
        warnings.forEach(w -> Pryzma.LOGGER.warn("ConnectedTextures: {}", w));
        Pryzma.LOGGER.info("ConnectedTextures: {} rules ({} from packs, {} built-in, {} rejected), {} tiles in {} ms",
                usable.size(), usablePack, usable.size() - usablePack, rules.size() - usable.size(), sprites.size(),
                (System.nanoTime() - start) / 1_000_000);
        return new Result(List.copyOf(usable), sprites);
    }

    /**
     * The built-in rule applies while the base texture comes from the vanilla pack, or its
     * programmer art variant while the Programmer Art pack provides it; any other pack wins.
     */
    private static String defaultVariant(ResourceManager manager, String texture) {
        Optional<Resource> r = manager.getResource(ResourceLocation.withDefaultNamespace(texture));
        if (r.isEmpty()) {
            return null;
        }
        String pack = r.get().sourcePackId();
        if ("vanilla".equals(pack)) {
            return "";
        }
        return "programmer_art".equals(pack) ? "programmer_art/" : null;
    }

    private static String[][] defaults() {
        List<String[]> list = new ArrayList<>();
        list.add(new String[] {"textures/block/glass.png", "20_glass/glass.properties"});
        list.add(new String[] {"textures/block/glass.png", "20_glass/glass_pane.properties"});
        list.add(new String[] {"textures/block/tinted_glass.png", "21_tinted_glass/tinted_glass.properties"});
        list.add(new String[] {"textures/block/bookshelf.png", "30_bookshelf/bookshelf.properties"});
        list.add(new String[] {"textures/block/sandstone.png", "40_sandstone/sandstone.properties"});
        list.add(new String[] {"textures/block/red_sandstone.png", "41_red_sandstone/red_sandstone.properties"});
        String[] colors = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan",
                "purple", "blue", "brown", "green", "red", "black"};
        for (int i = 0; i < colors.length; i++) {
            String c = colors[i];
            String dir = String.format("%02d_glass_%s/", i, c);
            list.add(new String[] {"textures/block/" + c + "_stained_glass.png", dir + "glass_" + c + ".properties"});
            list.add(new String[] {"textures/block/" + c + "_stained_glass.png", dir + "glass_pane_" + c + ".properties"});
        }
        return list.toArray(String[][]::new);
    }

    /** Parses one file outside any pack scan; used by tests. */
    static Optional<PrCtmRule> parse(PrProperties p, Predicate<ResourceLocation> spriteExists, List<String> warnings) {
        return PrCtmRule.parse(p, spriteExists, warnings::add);
    }
}
