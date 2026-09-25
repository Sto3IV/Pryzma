package net.pryzma.entity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Predicate;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;
import net.pryzma.core.match.PrBiomeMatcher;
import net.pryzma.core.match.PrBlockMatcher;
import net.pryzma.core.match.PrNbtMatcher;
import net.pryzma.core.match.PrRangeList;
import net.pryzma.core.res.PrProperties;

/**
 * OptiFine random entity rules ({@code optifine/random/**.properties}, {@code optifine/mob/**},
 * {@code optifine/cem/<entity>.properties}) with the semantics of OptiFine's
 * {@code RandomEntityProperties} / {@code RandomEntityRule}.
 *
 * <p>Either numbered variants with no rules (uniform choice by random id), or rules
 * {@code <key>.N} ({@code textures}/{@code skins} for textures, {@code models} for CEM) where the first
 * rule whose conditions hold picks a variant, weighted or uniform, by random id. Rules are found by
 * scanning indices up to ten past the last one that exists.
 *
 * @param <T> the resource a variant index resolves to: a texture location or a model
 */
public final class PrRandomProperties<T> {
    private final T[] variants;
    private final List<Rule<T>> rules;

    private PrRandomProperties(T[] variants, List<Rule<T>> rules) {
        this.variants = variants;
        this.rules = rules;
    }

    /** Numbered alternatives without a properties file. */
    public static <T> PrRandomProperties<T> ofVariants(T[] variants) {
        return new PrRandomProperties<>(variants, List.of());
    }

    public List<Rule<T>> rules() {
        return rules;
    }

    /** Every resource any rule or variant can pick. */
    public List<T> allResources() {
        List<T> out = new ArrayList<>();
        if (variants != null) {
            out.addAll(Arrays.asList(variants));
        }
        for (Rule<T> rule : rules) {
            out.addAll(Arrays.asList(rule.resources));
        }
        return out;
    }

    /**
     * The resource for {@code entity}: the first matching rule's pick, else a uniform variant, else
     * {@code fallback}. {@code ruleIndex[0]} receives the rule's {@code N}, or 0 when none matched.
     */
    public T select(PrEntityInfo entity, PrWorldInfo world, T fallback, int[] ruleIndex) {
        for (Rule<T> rule : rules) {
            if (rule.matches(entity, world)) {
                if (ruleIndex != null) {
                    ruleIndex[0] = rule.index;
                }
                return rule.resource(entity.randomId(), fallback);
            }
        }
        if (ruleIndex != null) {
            ruleIndex[0] = 0;
        }
        if (variants != null && variants.length > 0) {
            return variants[Math.floorMod(entity.randomId(), variants.length)];
        }
        return fallback;
    }

    /**
     * Parses the rules of one properties file. Returns {@code null} when the file defines no rule
     * or any rule is invalid, which is when OptiFine discards the whole file.
     *
     * @param keys         resource keys, e.g. {@code textures, skins} or {@code models}
     * @param makeResource variant index to resource; {@code null} marks a missing variant
     * @param professionOk validates a profession id (the game checks its registry)
     */
    public static <T> PrRandomProperties<T> parse(PrProperties props, String[] keys, IntFunction<T> makeResource,
            Predicate<ResourceLocation> professionOk, Consumer<String> warn) {
        List<Rule<T>> rules = new ArrayList<>();
        int max = 10;
        for (int i = 0; i < max; i++) {
            int index = i + 1;
            String list = null;
            for (String key : keys) {
                list = props.get(key + "." + index);
                if (list != null) {
                    break;
                }
            }
            if (list == null) {
                continue;
            }
            Rule<T> rule = Rule.parse(props, index, list, makeResource, professionOk, warn);
            if (rule == null) {
                return null;
            }
            rules.add(rule);
            max = index + 10;
        }
        if (rules.isEmpty()) {
            warn.accept("No " + keys[0] + " specified: " + props.location());
            return null;
        }
        return new PrRandomProperties<>(null, List.copyOf(rules));
    }

    /** One {@code <key>.N} rule and its {@code *.N} conditions. */
    public static final class Rule<T> {
        final int index;
        final T[] resources;
        final int[] sumWeights;
        final int sumAllWeights;
        final PrBiomeMatcher biomes;
        final PrRangeList heights;
        final PrRangeList health;
        final boolean healthPercent;
        final PrNbtMatcher name;
        final Profession[] professions;
        final DyeColor[] colors;
        final Boolean baby;
        final PrRangeList moonPhases;
        final PrRangeList dayTimes;
        final PrWorldInfo.Weather[] weather;
        final PrRangeList sizes;
        final PrNbtMatcher[] nbt;
        private final String blocksText;
        private PrBlockMatcher blocks;

        /** A villager profession with optional levels ({@code librarian:1,3-4}). */
        record Profession(ResourceLocation id, int[] levels) {
            boolean matches(ResourceLocation profession, int level) {
                if (!id.equals(profession)) {
                    return false;
                }
                if (levels == null) {
                    return true;
                }
                for (int l : levels) {
                    if (l == level) {
                        return true;
                    }
                }
                return false;
            }
        }

        private Rule(int index, T[] resources, int[] sumWeights, int sumAllWeights, PrBiomeMatcher biomes, PrRangeList heights,
                PrRangeList health, boolean healthPercent, PrNbtMatcher name, Profession[] professions, DyeColor[] colors,
                Boolean baby, PrRangeList moonPhases, PrRangeList dayTimes, PrWorldInfo.Weather[] weather, PrRangeList sizes,
                PrNbtMatcher[] nbt, String blocksText) {
            this.index = index;
            this.resources = resources;
            this.sumWeights = sumWeights;
            this.sumAllWeights = sumAllWeights;
            this.biomes = biomes;
            this.heights = heights;
            this.health = health;
            this.healthPercent = healthPercent;
            this.name = name;
            this.professions = professions;
            this.colors = colors;
            this.baby = baby;
            this.moonPhases = moonPhases;
            this.dayTimes = dayTimes;
            this.weather = weather;
            this.sizes = sizes;
            this.nbt = nbt;
            this.blocksText = blocksText;
        }

        public int index() {
            return index;
        }

        @SuppressWarnings("unchecked")
        static <T> Rule<T> parse(PrProperties p, int n, String list, IntFunction<T> makeResource,
                Predicate<ResourceLocation> professionOk, Consumer<String> warn) {
            String where = p.location() + " rule " + n;
            int[] indices = parseIntList(list, warn);
            if (indices.length == 0) {
                warn.accept("Invalid resources for " + where);
                return null;
            }
            T[] resources = (T[]) new Object[indices.length];
            for (int i = 0; i < indices.length; i++) {
                resources[i] = makeResource.apply(indices[i]);
                if (resources[i] == null) {
                    warn.accept("Missing variant " + indices[i] + " for " + where);
                    return null;
                }
            }
            int[] sumWeights = null;
            int sumAll = 1;
            String weightsText = p.get("weights." + n);
            if (weightsText != null) {
                int[] weights = parseIntList(weightsText, warn);
                if (weights.length > resources.length) {
                    weights = Arrays.copyOf(weights, resources.length);
                } else if (weights.length < resources.length) {
                    // OptiFine pads missing weights with the average of the given ones.
                    int average = weights.length == 0 ? 1 : Arrays.stream(weights).sum() / weights.length;
                    int given = weights.length;
                    weights = Arrays.copyOf(weights, resources.length);
                    Arrays.fill(weights, given, weights.length, average);
                }
                sumWeights = new int[weights.length];
                int sum = 0;
                for (int i = 0; i < weights.length; i++) {
                    sum += weights[i];
                    sumWeights[i] = sum;
                }
                sumAll = sum > 0 ? sum : 1;
            }
            PrRangeList heights = PrRangeList.parseSigned(p.get("heights." + n));
            if (heights == null) {
                heights = minMaxHeight(p, n, warn);
            }
            PrRangeList health = null;
            boolean healthPercent = false;
            String healthText = p.get("health." + n);
            if (healthText != null) {
                healthPercent = healthText.contains("%");
                health = PrRangeList.parse(healthText.replace("%", ""));
            }
            String nameText = p.get("name." + n);
            Profession[] professions = parseProfessions(p.get("professions." + n), professionOk);
            if (professions == INVALID_PROFESSIONS) {
                warn.accept("Invalid professions for " + where);
                return null;
            }
            DyeColor[] colors = parseColors(p.get("colors." + n));
            if (colors == null) {
                colors = parseColors(p.get("collarColors." + n));
            }
            if (colors == INVALID_COLORS) {
                warn.accept("Invalid colors for " + where);
                return null;
            }
            List<PrNbtMatcher> nbt = new ArrayList<>();
            p.withPrefix("nbt." + n + ".").forEach((path, value) -> nbt.add(PrNbtMatcher.parse(path, value)));
            return new Rule<>(n, resources, sumWeights, sumAll,
                    PrBiomeMatcher.parse(p.get("biomes." + n)), heights, health, healthPercent,
                    nameText == null ? null : PrNbtMatcher.parse("name", nameText), professions, colors,
                    parseBoolean(p.get("baby." + n)), PrRangeList.parse(p.get("moonPhase." + n)),
                    PrRangeList.parse(p.get("dayTime." + n)), parseWeather(p.get("weather." + n)),
                    PrRangeList.parse(p.get("sizes." + n)), nbt.isEmpty() ? null : nbt.toArray(new PrNbtMatcher[0]),
                    p.get("blocks." + n));
        }

        public boolean matches(PrEntityInfo e, PrWorldInfo w) {
            if (biomes != null && (e.spawnBiome() == null || !biomes.matches(e.spawnBiome()))) {
                return false;
            }
            if (heights != null && e.spawnPos() != null && !heights.contains(e.spawnPos().getY())) {
                return false;
            }
            if (health != null) {
                int value = e.health();
                if (healthPercent && e.maxHealth() > 0) {
                    value = (int) (value * 100.0 / e.maxHealth());
                }
                if (!health.contains(value)) {
                    return false;
                }
            }
            if (name != null && !name.matchesValue(e.name())) {
                return false;
            }
            if (professions != null && e.profession() != null) {
                boolean any = false;
                for (Profession p : professions) {
                    any |= p.matches(e.profession(), e.professionLevel());
                }
                if (!any) {
                    return false;
                }
            }
            if (colors != null && e.color() != null && !Arrays.asList(colors).contains(e.color())) {
                return false;
            }
            if (baby != null && e.baby() != null && e.baby().booleanValue() != baby.booleanValue()) {
                return false;
            }
            if (moonPhases != null && !moonPhases.contains(w.moonPhase())) {
                return false;
            }
            if (dayTimes != null && !dayTimes.contains(w.dayTime())) {
                return false;
            }
            if (weather != null && !Arrays.asList(weather).contains(w.weather())) {
                return false;
            }
            if (sizes != null && e.size() >= 0 && !sizes.contains(e.size())) {
                return false;
            }
            if (nbt != null && e.nbt() != null) {
                for (PrNbtMatcher m : nbt) {
                    if (!m.matches(e.nbt())) {
                        return false;
                    }
                }
            }
            if (blocksText != null) {
                if (blocks == null) {
                    blocks = PrBlockMatcher.parse(blocksText, null, msg -> { });
                }
                BlockState state = e.blockState();
                if (state != null && !blocks.matches(state)) {
                    return false;
                }
            }
            return true;
        }

        T resource(int randomId, T fallback) {
            if (resources.length == 0) {
                return fallback;
            }
            if (sumWeights == null) {
                return resources[Math.floorMod(randomId, resources.length)];
            }
            int r = Math.floorMod(randomId, sumAllWeights);
            for (int i = 0; i < sumWeights.length; i++) {
                if (sumWeights[i] > r) {
                    return resources[i];
                }
            }
            return resources[0];
        }
    }

    // ------------------------------------------------------------------ value parsers

    private static final Rule.Profession[] INVALID_PROFESSIONS = new Rule.Profession[0];
    private static final DyeColor[] INVALID_COLORS = new DyeColor[0];

    /** OptiFine {@code parseIntList}: numbers and ascending {@code a-b} intervals, space or comma separated. */
    static int[] parseIntList(String text, Consumer<String> warn) {
        List<Integer> out = new ArrayList<>();
        for (String token : text.trim().split("[ ,]+")) {
            if (token.isEmpty()) {
                continue;
            }
            int dash = token.indexOf('-');
            if (dash > 0) {
                int lo = nonNegative(token.substring(0, dash));
                int hi = nonNegative(token.substring(dash + 1));
                if (lo < 0 || hi < 0 || lo > hi) {
                    warn.accept("Invalid interval: " + token);
                    continue;
                }
                for (int v = lo; v <= hi; v++) {
                    out.add(v);
                }
            } else {
                int v = nonNegative(token);
                if (v < 0) {
                    warn.accept("Invalid number: " + token);
                } else {
                    out.add(v);
                }
            }
        }
        return out.stream().mapToInt(Integer::intValue).toArray();
    }

    private static int nonNegative(String s) {
        try {
            int v = Integer.parseInt(s.trim());
            return v < 0 ? -1 : v;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static PrRangeList minMaxHeight(PrProperties p, int n, Consumer<String> warn) {
        String min = p.get("minHeight." + n);
        String max = p.get("maxHeight." + n);
        if (min == null && max == null) {
            return null;
        }
        int lo = min == null ? 0 : nonNegative(min);
        int hi = max == null ? 256 : nonNegative(max);
        if (lo < 0 || hi < 0) {
            warn.accept("Invalid minHeight/maxHeight " + min + ", " + max);
            return null;
        }
        return PrRangeList.of(lo, hi);
    }

    private static Rule.Profession[] parseProfessions(String text, Predicate<ResourceLocation> ok) {
        if (text == null) {
            return null;
        }
        List<Rule.Profession> out = new ArrayList<>();
        for (String token : text.trim().split("\\s+")) {
            if (token.isEmpty()) {
                continue;
            }
            String name = token;
            int[] levels = null;
            int colon = token.lastIndexOf(':');
            if (colon >= 0) {
                String after = token.substring(colon + 1);
                if (after.isEmpty() || Character.isDigit(after.charAt(0))) {
                    name = token.substring(0, colon);
                    levels = after.isEmpty() ? null : parseIntList(after, msg -> { });
                }
            }
            ResourceLocation id = ResourceLocation.tryParse(name.toLowerCase(Locale.ROOT));
            if (id == null || !ok.test(id)) {
                return INVALID_PROFESSIONS;
            }
            out.add(new Rule.Profession(id, levels));
        }
        return out.isEmpty() ? null : out.toArray(new Rule.Profession[0]);
    }

    private static DyeColor[] parseColors(String text) {
        if (text == null) {
            return null;
        }
        List<DyeColor> out = new ArrayList<>();
        for (String token : text.trim().split("\\s+")) {
            if (token.isEmpty()) {
                continue;
            }
            DyeColor color = null;
            String key = token.toLowerCase(Locale.ROOT).replace("_", "");
            for (DyeColor c : DyeColor.values()) {
                if (c.getSerializedName().replace("_", "").equals(key)) {
                    color = c;
                }
            }
            if (color == null) {
                return INVALID_COLORS;
            }
            out.add(color);
        }
        return out.isEmpty() ? null : out.toArray(new DyeColor[0]);
    }

    private static PrWorldInfo.Weather[] parseWeather(String text) {
        if (text == null) {
            return null;
        }
        List<PrWorldInfo.Weather> out = new ArrayList<>();
        for (String token : text.trim().split("\\s+")) {
            for (PrWorldInfo.Weather w : PrWorldInfo.Weather.values()) {
                if (w.name().equalsIgnoreCase(token)) {
                    out.add(w);
                }
            }
        }
        return out.isEmpty() ? null : out.toArray(new PrWorldInfo.Weather[0]);
    }

    private static Boolean parseBoolean(String text) {
        if (text == null) {
            return null;
        }
        String s = text.trim().toLowerCase(Locale.ROOT);
        return s.equals("true") ? Boolean.TRUE : s.equals("false") ? Boolean.FALSE : null;
    }
}
