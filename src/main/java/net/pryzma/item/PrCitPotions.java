package net.pryzma.item;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

import net.minecraft.resources.ResourceLocation;
import net.pryzma.core.res.PrPaths;
import net.pryzma.core.res.PrProperties;

/**
 * Pre-1.9 potion damage values, which CIT rules still use to tell potions apart: the value a
 * potion item had ({@link #damage}) and MCPatcher's automatic rules for images named after a
 * potion in {@code cit/potion/<normal|splash|linger>/} ({@link #imageRule}).
 */
final class PrCitPotions {
    static final int SPLASH = 0x4000;
    private static final int EXTENDED = 0x2000;
    private static final int STRONG = 0x20;
    private static final int LONG = 0x40;
    /** Splash bit and effect id: what an automatic rule compares. */
    private static final int IMAGE_MASK = 0x403F;

    private static final Map<String, Integer> DAMAGE = new HashMap<>();
    private static final Map<String, int[]> IMAGE_DAMAGE = new LinkedHashMap<>();

    static {
        potion("water", 0, false);
        potion("awkward", 16, false);
        potion("thick", 32, false);
        potion("mundane", 64, false);
        potion("regeneration", 1, true);
        potion("swiftness", 2, true);
        potion("fire_resistance", 3, true);
        potion("poison", 4, true);
        potion("healing", 5, true);
        potion("night_vision", 6, true);
        potion("weakness", 8, true);
        potion("strength", 9, true);
        potion("slowness", 10, true);
        potion("leaping", 11, true);
        potion("harming", 12, true);
        potion("water_breathing", 13, true);
        potion("invisibility", 14, true);

        image("water", 0, 0);
        image("awkward", 0, 1);
        image("thick", 0, 2);
        image("potent", 0, 3);
        images("regeneration", 1);
        images("movespeed", 2);
        images("fireresistance", 3);
        images("poison", 4);
        images("heal", 5);
        images("nightvision", 6);
        image("clear", 7, 0);
        image("bungling", 7, 1);
        image("charming", 7, 2);
        image("rank", 7, 3);
        images("weakness", 8);
        images("damageboost", 9);
        images("moveslowdown", 10);
        images("leaping", 11);
        images("harm", 12);
        images("waterbreathing", 13);
        images("invisibility", 14);
        image("thin", 15, 0);
        image("debonair", 15, 1);
        image("sparkling", 15, 2);
        image("stinky", 15, 3);
        image("mundane", 0, 4);
        alias("speed", "movespeed");
        alias("fire_resistance", "fireresistance");
        alias("instant_health", "heal");
        alias("night_vision", "nightvision");
        alias("strength", "damageboost");
        alias("slowness", "moveslowdown");
        alias("instant_damage", "harm");
        alias("water_breathing", "waterbreathing");
    }

    private PrCitPotions() {
    }

    private static void potion(String name, int value, boolean extended) {
        if (extended) {
            value |= EXTENDED;
        }
        DAMAGE.put("minecraft:" + name, value);
        if (extended) {
            DAMAGE.put("minecraft:strong_" + name, value | STRONG);
            DAMAGE.put("minecraft:long_" + name, value | LONG);
        }
    }

    private static void image(String name, int base, int sub) {
        IMAGE_DAMAGE.put(name, new int[] {base + sub * 16});
    }

    private static void images(String name, int base) {
        IMAGE_DAMAGE.put(name, new int[] {base, base + 16, base + 32, base + 48});
    }

    private static void alias(String name, String of) {
        IMAGE_DAMAGE.put(name, IMAGE_DAMAGE.get(of));
    }

    /**
     * OptiFine's damage of a potion item: 0 without a potion, the legacy value (with the splash
     * bit for splash potions) for a vanilla potion, -1 for any other.
     */
    static int damage(String potionId, boolean splash) {
        if (potionId == null || potionId.isEmpty()) {
            return 0;
        }
        Integer value = DAMAGE.get(potionId);
        if (value == null) {
            return -1;
        }
        return splash ? value | SPLASH : value;
    }

    /**
     * The rule MCPatcher derives from {@code cit/potion/<type>/<name>.png}, as the properties it
     * would have been written as, or {@code null} for an image that names no potion. {@code _n}
     * and {@code _s} images are skipped; {@code empty} in {@code normal} is the glass bottle.
     */
    static PrProperties imageRule(ResourceLocation png, String type) {
        String name = PrPaths.baseName(png.getPath());
        if (name.endsWith("_n") || name.endsWith("_s")) {
            return null;
        }
        ResourceLocation location = png.withPath(PrPaths.stripExtension(png.getPath(), ".png") + ".properties");
        Map<String, String> p = new LinkedHashMap<>();
        p.put("type", "item");
        if (name.equals("empty") && type.equals("normal")) {
            p.put("items", "minecraft:glass_bottle");
            return PrProperties.of(location, p);
        }
        int[] damages = IMAGE_DAMAGE.get(name);
        if (damages == null) {
            return null;
        }
        boolean splash = type.equals("splash");
        StringJoiner damage = new StringJoiner(" ");
        for (int d : damages) {
            damage.add(Integer.toString(splash ? d | SPLASH : d));
        }
        p.put("items", switch (type) {
            case "splash" -> "minecraft:splash_potion";
            case "linger" -> "minecraft:lingering_potion";
            default -> "minecraft:potion";
        });
        p.put("damage", damage.toString());
        p.put("damageMask", Integer.toString(name.equals("water") || name.equals("mundane") ? IMAGE_MASK | LONG : IMAGE_MASK));
        p.put(splash ? "texture.potion_bottle_splash" : "texture.potion_bottle_drinkable", name);
        return PrProperties.of(location, p);
    }
}
