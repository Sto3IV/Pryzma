package net.pryzma.item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import net.minecraft.resources.ResourceLocation;
import net.pryzma.core.match.PrNbtMatcher;
import net.pryzma.core.match.PrRangeList;
import net.pryzma.core.res.PrPaths;
import net.pryzma.core.res.PrProperties;

/**
 * One Custom Item Textures rule ({@code optifine/cit/**.properties}, also read from
 * {@code mcpatcher/cit/}), parsed and matched with the semantics of OptiFine's
 * {@code CustomItemProperties}: {@code type}, {@code items}/{@code matchItems} (the file name when
 * absent), {@code texture}/{@code tile}/{@code source} (the file name when neither a texture nor
 * a model is given), {@code texture.<name>} and {@code model.<name>} for override sub-models,
 * {@code damage} (with {@code %} and {@code damageMask}), {@code stackSize},
 * {@code enchantmentIDs}/{@code enchantments} (legacy numbers included), {@code enchantmentLevels},
 * {@code components.<path>} and the legacy {@code nbt.<path>}, {@code hand}, and the layer
 * settings {@code blend}, {@code speed}, {@code rotation}, {@code layer}, {@code duration},
 * {@code weight}.
 *
 * <p>Deviations, each one reviving a rule OptiFine would silently drop: namespaced texture and
 * model names are taken as written, enchantment ids are not limited to vanilla ones,
 * {@code items} also restricts an enchantment rule, and a few more legacy {@code nbt.} paths map
 * to components. CIT Resewn's {@code useGlint} and {@code blur} are read for enchantment layers.
 */
public final class PrCitRule {
    public enum Type {
        ITEM, ENCHANTMENT, ARMOR, ELYTRA
    }

    public enum Hand {
        ANY, MAIN, OFF
    }

    /** OptiFine's order: layer ascending, weight descending, then directory and file name. */
    public static final Comparator<PrCitRule> ORDER = (a, b) -> {
        if (a.layer != b.layer) {
            return Integer.compare(a.layer, b.layer);
        }
        if (a.weight != b.weight) {
            return Integer.compare(b.weight, a.weight);
        }
        int c = a.basePath.compareTo(b.basePath);
        return c != 0 ? c : a.name.compareTo(b.name);
    };

    /** Pre-1.13 numeric enchantment ids. */
    private static final Map<Integer, String> LEGACY_ENCHANTMENTS = Map.ofEntries(
            Map.entry(0, "protection"), Map.entry(1, "fire_protection"), Map.entry(2, "feather_falling"),
            Map.entry(3, "blast_protection"), Map.entry(4, "projectile_protection"), Map.entry(5, "respiration"),
            Map.entry(6, "aqua_affinity"), Map.entry(7, "thorns"), Map.entry(8, "depth_strider"),
            Map.entry(9, "frost_walker"), Map.entry(10, "binding_curse"), Map.entry(16, "sharpness"),
            Map.entry(17, "smite"), Map.entry(18, "bane_of_arthropods"), Map.entry(19, "knockback"),
            Map.entry(20, "fire_aspect"), Map.entry(21, "looting"), Map.entry(32, "efficiency"),
            Map.entry(33, "silk_touch"), Map.entry(34, "unbreaking"), Map.entry(35, "fortune"),
            Map.entry(48, "power"), Map.entry(49, "punch"), Map.entry(50, "flame"), Map.entry(51, "infinity"),
            Map.entry(61, "luck_of_the_sea"), Map.entry(62, "lure"), Map.entry(65, "loyalty"),
            Map.entry(66, "impaling"), Map.entry(67, "riptide"), Map.entry(68, "channeling"),
            Map.entry(70, "mending"), Map.entry(71, "vanishing_curse"));

    /** Legacy {@code nbt.} paths and the component paths that replaced them; OptiFine maps the first two. */
    private static final Map<String, String> NBT_COMPONENTS = Map.of(
            "display.Name", "minecraft:custom_name",
            "display.Lore", "minecraft:lore",
            "display.color", "minecraft:dyed_color.rgb",
            "Potion", "minecraft:potion_contents.potion",
            "CustomModelData", "minecraft:custom_model_data",
            "Damage", "minecraft:damage",
            "SkullOwner", "minecraft:profile.name",
            "SkullOwner.Name", "minecraft:profile.name");

    final ResourceLocation location;
    final String name;
    final String basePath;
    final Type type;
    /** Item ids as written; empty for an enchantment rule that names no items. */
    final List<ResourceLocation> items;
    /** The main texture file ({@code .png}), or {@code null}. */
    final ResourceLocation texture;
    /** {@code texture.<key>} files by key. */
    final Map<String, ResourceLocation> textures;
    final ResourceLocation model;
    /** {@code model.<key>} models by key. */
    final Map<String, ResourceLocation> models;
    final PrRangeList damage;
    final boolean damagePercent;
    final int damageMask;
    final PrRangeList stackSize;
    /** {@code null} when the rule has no enchantment condition. */
    final Set<ResourceLocation> enchantments;
    final PrRangeList enchantmentLevels;
    /** {@code null} when the rule has no component condition. */
    final PrNbtMatcher[] components;
    final Hand hand;
    final PrCitBlend blend;
    final float speed;
    final float rotation;
    final float duration;
    final int layer;
    final int weight;
    final boolean useGlint;
    final boolean blur;

    private PrCitRule(Builder b) {
        this.location = b.location;
        this.name = b.name;
        this.basePath = b.basePath;
        this.type = b.type;
        this.items = List.copyOf(b.items);
        this.texture = b.texture;
        this.textures = Collections.unmodifiableMap(b.textures);
        this.model = b.model;
        this.models = Collections.unmodifiableMap(b.models);
        this.damage = b.damage;
        this.damagePercent = b.damagePercent;
        this.damageMask = b.damageMask;
        this.stackSize = b.stackSize;
        this.enchantments = b.enchantments == null ? null : Collections.unmodifiableSet(b.enchantments);
        this.enchantmentLevels = b.enchantmentLevels;
        this.components = b.components;
        this.hand = b.hand;
        this.blend = b.blend;
        this.speed = b.speed;
        this.rotation = b.rotation;
        this.duration = b.duration;
        this.layer = b.layer;
        this.weight = b.weight;
        this.useGlint = b.useGlint;
        this.blur = b.blur;
    }

    private static final class Builder {
        ResourceLocation location;
        String name;
        String basePath;
        Type type;
        List<ResourceLocation> items;
        ResourceLocation texture;
        final Map<String, ResourceLocation> textures = new LinkedHashMap<>();
        ResourceLocation model;
        final Map<String, ResourceLocation> models = new LinkedHashMap<>();
        PrRangeList damage;
        boolean damagePercent;
        int damageMask;
        PrRangeList stackSize;
        Set<ResourceLocation> enchantments;
        PrRangeList enchantmentLevels;
        PrNbtMatcher[] components;
        Hand hand;
        PrCitBlend blend;
        float speed;
        float rotation;
        float duration;
        int layer;
        int weight;
        boolean useGlint;
        boolean blur;
    }

    /** The rule of one properties file, or {@code null} when OptiFine would reject it (the reason goes to {@code warn}). */
    public static PrCitRule parse(PrProperties p, Consumer<String> warn) {
        Builder b = new Builder();
        b.location = p.location();
        b.name = p.name();
        b.basePath = p.basePath();
        if (b.name.isEmpty()) {
            warn.accept("No name found");
            return null;
        }
        b.type = parseType(p.get("type"), warn);
        if (b.type == null) {
            return null;
        }

        p.withPrefix("model.").forEach((key, value) -> put(b.models, key, modelLocation(value, b.basePath), value, warn));
        String modelText = p.get("model");
        if (modelText != null) {
            b.model = modelLocation(modelText, b.basePath);
            if (b.model == null) {
                warn.accept("Invalid model: " + modelText);
            }
        } else if (b.type != Type.ARMOR) {
            b.model = b.models.get("bow_standby");
        }

        p.withPrefix("texture.").forEach((key, value) -> put(b.textures, key, textureFile(value, b.basePath), value, warn));
        String textureText = first(p, "texture", "tile", "source");
        if (textureText != null) {
            b.texture = textureFile(textureText, b.basePath);
            if (b.texture == null) {
                warn.accept("Invalid texture: " + textureText);
            }
        } else if (b.type != Type.ARMOR) {
            b.texture = b.textures.get("bow_standby");
            if (b.texture == null && b.model == null && b.models.isEmpty()) {
                b.texture = textureFile(b.name, b.basePath);
            }
        }

        String itemsText = first(p, "items", "matchItems");
        if (itemsText != null) {
            b.items = parseIds(itemsText, "item", warn);
        } else if (b.type == Type.ELYTRA) {
            b.items = List.of(ResourceLocation.withDefaultNamespace("elytra"));
        } else if (b.type == Type.ENCHANTMENT) {
            b.items = List.of();
        } else {
            ResourceLocation guess = ResourceLocation.tryParse(b.name);
            if (guess == null) {
                warn.accept("No items defined");
                return null;
            }
            b.items = List.of(guess);
        }

        String damageText = p.get("damage");
        if (damageText != null) {
            b.damagePercent = damageText.contains("%");
            b.damage = parseRanges(damageText.replace("%", ""), warn);
            b.damageMask = parseInt(p.get("damageMask"), 0, warn);
        }
        b.stackSize = parseRanges(p.get("stackSize"), warn);
        b.enchantments = parseEnchantments(first(p, "enchantmentIDs", "enchantments"), warn);
        b.enchantmentLevels = parseRanges(p.get("enchantmentLevels"), warn);
        b.components = parseComponents(p, warn);
        b.hand = parseHand(p.get("hand"), warn);
        b.blend = PrCitBlend.parse(p.get("blend"), warn);
        b.speed = parseFloat(p.get("speed"), 0.0F, warn);
        b.rotation = parseFloat(p.get("rotation"), 0.0F, warn);
        b.layer = parseInt(p.get("layer"), 0, warn);
        b.weight = parseInt(p.get("weight"), 0, warn);
        b.duration = parseFloat(p.get("duration"), 1.0F, warn);
        b.useGlint = p.getBool("useGlint", false);
        b.blur = p.getBool("blur", true);

        if (b.texture == null && b.textures.isEmpty() && b.model == null && b.models.isEmpty()) {
            warn.accept("No texture or model specified");
            return null;
        }
        if (b.type == Type.ENCHANTMENT && b.enchantments == null) {
            warn.accept("No enchantmentIDs specified");
            return null;
        }
        if (b.components != null && b.components.length == 0) {
            warn.accept("Invalid NBT checks specified");
            return null;
        }
        return new PrCitRule(b);
    }

    /** Whether the rule's own conditions hold; the item itself is checked by the index. */
    public boolean matches(PrCitItem item) {
        if (damage != null) {
            int d = item.damage();
            if (d < 0) {
                return false;
            }
            if (damageMask != 0) {
                d &= damageMask;
            }
            if (damagePercent) {
                d = (int) (d * 100.0 / item.maxDamage());
            }
            if (!damage.contains(d)) {
                return false;
            }
        }
        if (stackSize != null && !stackSize.contains(item.count())) {
            return false;
        }
        if (enchantments != null) {
            boolean any = false;
            for (int i = 0; i < item.enchantments() && !any; i++) {
                any = enchantments.contains(item.enchantment(i));
            }
            if (!any) {
                return false;
            }
        }
        if (enchantmentLevels != null) {
            // OptiFine checks the level of every enchantment on the item, not only the named ones.
            boolean any = false;
            for (int i = 0; i < item.enchantments() && !any; i++) {
                any = enchantmentLevels.contains(item.enchantmentLevel(i));
            }
            if (!any) {
                return false;
            }
        }
        if (components != null) {
            for (PrNbtMatcher m : components) {
                if (!m.matches(item.components(m.head()))) {
                    return false;
                }
            }
        }
        return switch (hand) {
            case ANY -> true;
            case MAIN -> !item.offHand();
            case OFF -> item.offHand();
        };
    }

    /** Whether an enchantment rule applies to {@code item}: every item unless {@code items} names some. */
    boolean appliesTo(ResourceLocation item) {
        return items.isEmpty() || items.contains(item);
    }

    // ------------------------------------------------------------------ parsing

    private static Type parseType(String text, Consumer<String> warn) {
        if (text == null) {
            return Type.ITEM;
        }
        return switch (text) {
            case "item" -> Type.ITEM;
            case "enchantment" -> Type.ENCHANTMENT;
            case "armor" -> Type.ARMOR;
            case "elytra" -> Type.ELYTRA;
            default -> {
                warn.accept("Unknown method: " + text);
                yield null;
            }
        };
    }

    private static Hand parseHand(String text, Consumer<String> warn) {
        if (text == null) {
            return Hand.ANY;
        }
        return switch (text.toLowerCase(Locale.ROOT)) {
            case "any" -> Hand.ANY;
            case "main" -> Hand.MAIN;
            case "off" -> Hand.OFF;
            default -> {
                warn.accept("Invalid hand: " + text);
                yield Hand.ANY;
            }
        };
    }

    private static String first(PrProperties p, String... keys) {
        for (String key : keys) {
            String v = p.get(key);
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    private static void put(Map<String, ResourceLocation> map, String key, ResourceLocation value, String text,
            Consumer<String> warn) {
        if (value == null) {
            warn.accept("Invalid location: " + text);
        } else {
            map.put(key, value);
        }
    }

    private static List<ResourceLocation> parseIds(String text, String what, Consumer<String> warn) {
        List<ResourceLocation> out = new ArrayList<>();
        for (String token : text.trim().split("\\s+")) {
            if (token.isEmpty()) {
                continue;
            }
            ResourceLocation id = ResourceLocation.tryParse(token);
            if (id == null) {
                warn.accept("Invalid " + what + ": " + token);
            } else if (!out.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }

    private static Set<ResourceLocation> parseEnchantments(String text, Consumer<String> warn) {
        if (text == null) {
            return null;
        }
        Set<ResourceLocation> out = new LinkedHashSet<>();
        for (String token : text.trim().split("\\s+")) {
            if (token.isEmpty()) {
                continue;
            }
            String name = token;
            if (token.chars().allMatch(Character::isDigit)) {
                name = LEGACY_ENCHANTMENTS.get(PrProperties.parseInt(token, -1));
                if (name == null) {
                    warn.accept("Invalid value: " + token);
                    continue;
                }
            }
            ResourceLocation id = ResourceLocation.tryParse(name);
            if (id == null) {
                warn.accept("Invalid value: " + token);
            } else {
                out.add(id);
            }
        }
        return out;
    }

    /**
     * OptiFine's CIT range list: space separated {@code n}, {@code a-b}, {@code a-} (up to 65535)
     * and {@code -b} (from 0), non-negative; any malformed token drops the whole condition.
     */
    static PrRangeList parseRanges(String text, Consumer<String> warn) {
        if (text == null) {
            return null;
        }
        List<int[]> ranges = new ArrayList<>();
        for (String token : text.trim().split("\\s+")) {
            if (token.isEmpty()) {
                continue;
            }
            int[] range = parseRange(token);
            if (range == null) {
                warn.accept("Invalid range list: " + text);
                return null;
            }
            ranges.add(range);
        }
        int[] lo = new int[ranges.size()];
        int[] hi = new int[ranges.size()];
        for (int i = 0; i < lo.length; i++) {
            lo[i] = ranges.get(i)[0];
            hi[i] = ranges.get(i)[1];
        }
        return PrRangeList.of(lo, hi);
    }

    private static int[] parseRange(String token) {
        int dash = token.indexOf('-');
        if (dash != token.lastIndexOf('-')) {
            return null;
        }
        if (dash < 0) {
            int v = PrProperties.parseInt(token, -1);
            return v < 0 ? null : new int[] {v, v};
        }
        String a = token.substring(0, dash);
        String b = token.substring(dash + 1);
        if (a.isEmpty() && b.isEmpty()) {
            return null;
        }
        if (a.isEmpty()) {
            int v = PrProperties.parseInt(b, -1);
            return v < 0 ? null : new int[] {0, v};
        }
        if (b.isEmpty()) {
            int v = PrProperties.parseInt(a, -1);
            return v < 0 ? null : new int[] {v, 65535};
        }
        int x = PrProperties.parseInt(a, -1);
        int y = PrProperties.parseInt(b, -1);
        return x < 0 || y < 0 ? null : new int[] {Math.min(x, y), Math.max(x, y)};
    }

    /**
     * {@code components.*} conditions; without any, every {@code nbt.*} key must map to a
     * component or the rule is invalid (an empty array), as in OptiFine.
     */
    private static PrNbtMatcher[] parseComponents(PrProperties p, Consumer<String> warn) {
        Map<String, String> components = p.withPrefix("components.");
        if (components.isEmpty()) {
            Map<String, String> nbt = p.withPrefix("nbt.");
            if (nbt.isEmpty()) {
                return null;
            }
            components = new LinkedHashMap<>();
            for (Map.Entry<String, String> e : nbt.entrySet()) {
                String mapped = legacyComponent(e.getKey());
                if (mapped == null) {
                    warn.accept("Invalid NBT check: nbt." + e.getKey() + "=" + e.getValue());
                    return new PrNbtMatcher[0];
                }
                components.putIfAbsent(mapped, e.getValue());
            }
        }
        List<PrNbtMatcher> out = new ArrayList<>();
        components.forEach((path, value) -> out.add(PrNbtMatcher.parse(fixNamespaces(path), value)));
        return out.toArray(new PrNbtMatcher[0]);
    }

    private static String legacyComponent(String path) {
        String mapped = NBT_COMPONENTS.get(path);
        if (mapped == null && path.startsWith("display.Lore.")) {
            mapped = "minecraft:lore." + path.substring("display.Lore.".length());
        }
        return mapped;
    }

    /** OptiFine {@code fixNamespaces}: {@code ~} stands for {@code minecraft:}, a bare component id gets it. */
    static String fixNamespaces(String path) {
        path = path.replace("~", "minecraft:");
        if (path.startsWith("*")) {
            return path;
        }
        int dot = path.indexOf('.');
        int end = dot >= 0 ? dot : path.length();
        return path.lastIndexOf(':', end - 1) < 0 ? "minecraft:" + path : path;
    }

    private static int parseInt(String text, int def, Consumer<String> warn) {
        if (text == null) {
            return def;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            warn.accept("Invalid integer: " + text);
            return def;
        }
    }

    private static float parseFloat(String text, float def, Consumer<String> warn) {
        if (text == null) {
            return def;
        }
        try {
            return Float.parseFloat(text.trim());
        } catch (NumberFormatException e) {
            warn.accept("Invalid float: " + text);
            return def;
        }
    }

    // ------------------------------------------------------------------ paths

    /**
     * OptiFine {@code fixTextureName} + {@code getTextureLocation}: the {@code .png} a texture
     * name refers to. Names resolve like other OptiFine paths; a bare name not rooted in
     * {@code textures/} or {@code optifine/} is taken relative to the properties file.
     */
    static ResourceLocation textureFile(String text, String basePath) {
        String s = fixName(PrPaths.stripExtension(text.trim(), ".png"), basePath, false);
        ResourceLocation id = ResourceLocation.tryParse(s);
        if (id == null) {
            return null;
        }
        String path = id.getPath();
        if (path.indexOf('/') < 0) {
            path = "textures/item/" + path;
        } else if (s.indexOf(':') >= 0 && !isRooted(path)) {
            path = "textures/" + path;
        }
        ResourceLocation file = ResourceLocation.tryBuild(id.getNamespace(), path + ".png");
        return file == null ? null : PrPaths.logical(file);
    }

    /** OptiFine {@code fixModelName}: {@code item/} and {@code block/} models stay vanilla. */
    static ResourceLocation modelLocation(String text, String basePath) {
        ResourceLocation id = ResourceLocation.tryParse(fixName(PrPaths.stripExtension(text.trim(), ".json"), basePath, true));
        return id == null ? null : PrPaths.logical(id);
    }

    private static String fixName(String text, String basePath, boolean model) {
        String s = PrPaths.resolve(text, basePath);
        boolean vanilla = model ? s.startsWith("block/") || s.startsWith("item/") : s.startsWith("textures/");
        if (s.indexOf(':') < 0 && !s.startsWith(basePath) && !vanilla && !isRooted(s)) {
            s = basePath + "/" + s;
        }
        return s.startsWith("/") ? s.substring(1) : s;
    }

    private static boolean isRooted(String path) {
        return path.startsWith("textures/") || path.startsWith(PrPaths.OPTIFINE) || path.startsWith(PrPaths.MCPATCHER);
    }

    /** Block atlas sprite of a texture file: its path without {@code textures/} and {@code .png}. */
    static ResourceLocation spriteOf(ResourceLocation file) {
        String path = PrPaths.stripExtension(file.getPath(), ".png");
        return file.withPath(path.startsWith("textures/") ? path.substring("textures/".length()) : path);
    }

    @Override
    public String toString() {
        return location.toString();
    }
}
