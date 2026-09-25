package net.pryzma.core.match;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;

/**
 * An OptiFine biome list ({@code biomes=}). Names match exactly or in OptiFine's compact form
 * (lower case, spaces and underscores removed), so {@code IcePlains}, {@code ice_plains} and
 * {@code minecraft:ice_plains} are one name. A leading {@code !} inverts the whole list.
 *
 * <p>Biomes are data driven, so nothing is resolved at parse time: matching works on the biome's
 * registry id. Pre-1.18 names that MCPatcher and old OptiFine packs use are mapped to the biomes
 * that replaced them.
 */
public final class PrBiomeMatcher {
    private static final Map<String, String[]> LEGACY = legacyNames();

    private final Set<String> names;
    private final boolean negated;
    private final Map<ResourceLocation, Boolean> cache = new ConcurrentHashMap<>();

    private PrBiomeMatcher(Set<String> names, boolean negated) {
        this.names = names;
        this.negated = negated;
    }

    /** Returns {@code null} for a missing or empty list, meaning "any biome". */
    public static PrBiomeMatcher parse(String text) {
        if (text == null) {
            return null;
        }
        String s = text.trim();
        boolean negated = s.startsWith("!");
        if (negated) {
            s = s.substring(1).trim();
        }
        Set<String> names = new HashSet<>();
        for (String token : s.split("\\s+")) {
            if (token.isEmpty()) {
                continue;
            }
            String key = compact(token);
            names.add(key);
            String[] aliases = LEGACY.get(key);
            if (aliases != null) {
                for (String alias : aliases) {
                    names.add("minecraft:" + alias);
                }
            }
        }
        return names.isEmpty() && !negated ? null : new PrBiomeMatcher(names, negated);
    }

    public boolean matches(Holder<Biome> biome) {
        return biome.unwrapKey().map(this::matches).orElse(negated);
    }

    public boolean matches(ResourceKey<Biome> key) {
        return matches(key.location());
    }

    public boolean matches(ResourceLocation id) {
        return cache.computeIfAbsent(id, i -> names.contains(compact(i.toString())) != negated);
    }

    /** OptiFine compact name: lower case, namespace defaulted, spaces and underscores removed. */
    static String compact(String name) {
        String s = name.toLowerCase(Locale.ROOT);
        int colon = s.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : s.substring(0, colon);
        String path = colon < 0 ? s : s.substring(colon + 1);
        return namespace + ":" + path.replace(" ", "").replace("_", "");
    }

    private static Map<String, String[]> legacyNames() {
        String[][] table = {
                // 1.12 ids and MCPatcher display names
                {"extremehills", "windswepthills"}, {"extremehills+", "windsweptforest"},
                {"extremehillswithtrees", "windsweptforest"}, {"smallerextremehills", "windsweptforest"},
                {"extremehillsedge", "windswepthills"}, {"iceflats", "snowyplains"}, {"iceplains", "snowyplains"},
                {"icemountains", "snowyplains"}, {"mushroomisland", "mushroomfields"},
                {"mushroomislandshore", "mushroomfields"}, {"beaches", "beach"}, {"coldbeach", "snowybeach"},
                {"stonebeach", "stonyshore"}, {"swampland", "swamp"}, {"roofedforest", "darkforest"},
                {"mesa", "badlands"}, {"mesarock", "woodedbadlands"}, {"mesaplateauf", "woodedbadlands"},
                {"mesaclearrock", "badlands"}, {"mesaplateau", "badlands"}, {"savannarock", "savannaplateau"},
                {"redwoodtaiga", "oldgrowthpinetaiga"}, {"megataiga", "oldgrowthpinetaiga"},
                {"megataigahills", "oldgrowthpinetaiga"}, {"redwoodtaigahills", "oldgrowthpinetaiga"},
                {"taigacold", "snowytaiga"}, {"coldtaiga", "snowytaiga"}, {"taigacoldhills", "snowytaiga"},
                {"coldtaigahills", "snowytaiga"}, {"foresthills", "forest"}, {"deserthills", "desert"},
                {"taigahills", "taiga"}, {"junglehills", "jungle"}, {"jungleedge", "sparsejungle"},
                {"birchforesthills", "birchforest"}, {"hell", "netherwastes"}, {"nether", "netherwastes"},
                {"sky", "theend"}, {"mutatedplains", "sunflowerplains"}, {"mutatedforest", "flowerforest"},
                {"mutatediceflats", "icespikes"}, {"mutatedbirchforest", "oldgrowthbirchforest"},
                {"mutatedbirchforesthills", "oldgrowthbirchforest"},
                {"mutatedredwoodtaiga", "oldgrowthsprucetaiga"}, {"mutatedredwoodtaigahills", "oldgrowthsprucetaiga"},
                {"mutatedmesa", "erodedbadlands"}, {"mutatedmesarock", "woodedbadlands"},
                {"mutatedmesaclearrock", "badlands"}, {"mutatedsavanna", "windsweptsavanna"},
                {"mutatedsavannarock", "windsweptsavanna"}, {"mutatedextremehills", "windsweptgravellyhills"},
                {"mutatedextremehillswithtrees", "windsweptgravellyhills"}, {"mutatedjungle", "jungle"},
                {"mutatedjungleedge", "sparsejungle"}, {"mutatedswampland", "swamp"}, {"mutateddesert", "desert"},
                {"mutatedtaiga", "taiga"}, {"mutatedtaigacold", "snowytaiga"}, {"mutatedroofedforest", "darkforest"},
                // 1.13 - 1.17 ids renamed in 1.18
                {"badlandsplateau", "badlands"}, {"bamboojunglehills", "bamboojungle"},
                {"darkforesthills", "darkforest"}, {"desertlakes", "desert"},
                {"giantsprucetaiga", "oldgrowthsprucetaiga"}, {"giantsprucetaigahills", "oldgrowthsprucetaiga"},
                {"gianttreetaiga", "oldgrowthpinetaiga"}, {"gianttreetaigahills", "oldgrowthpinetaiga"},
                {"gravellymountains", "windsweptgravellyhills"}, {"modifiedbadlandsplateau", "badlands"},
                {"modifiedgravellymountains", "windsweptgravellyhills"}, {"modifiedjungle", "jungle"},
                {"modifiedjungleedge", "sparsejungle"}, {"modifiedwoodedbadlandsplateau", "woodedbadlands"},
                {"mountainedge", "windswepthills"}, {"mountains", "windswepthills"},
                {"mushroomfieldshore", "mushroomfields"}, {"shatteredsavanna", "windsweptsavanna"},
                {"shatteredsavannaplateau", "windsweptsavanna"}, {"snowymountains", "snowyplains"},
                {"snowytaigahills", "snowytaiga"}, {"snowytaigamountains", "snowytaiga"},
                {"snowytundra", "snowyplains"}, {"stoneshore", "stonyshore"}, {"swamphills", "swamp"},
                {"taigamountains", "taiga"}, {"tallbirchforest", "oldgrowthbirchforest"},
                {"tallbirchhills", "oldgrowthbirchforest"}, {"woodedbadlandsplateau", "woodedbadlands"},
                {"woodedhills", "forest"}, {"woodedmountains", "windsweptforest"}, {"deepwarmocean", "warmocean"},
        };
        Map<String, String[]> map = new HashMap<>();
        for (String[] row : table) {
            map.put("minecraft:" + row[0], new String[] {row[1]});
        }
        return map;
    }
}
