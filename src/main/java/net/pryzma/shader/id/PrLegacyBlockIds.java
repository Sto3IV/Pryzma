package net.pryzma.shader.id;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;

/**
 * OptiFine / Iris legacy 1.12-era block ID fallbacks.
 * Used when a shader pack does not provide {@code block.properties} or leaves common
 * vegetation, terrain, and light blocks unmapped so waving plants, swaying leaves, and emissives
 * function out-of-the-box.
 */
public final class PrLegacyBlockIds {
    private static final Map<ResourceLocation, Integer> DEFAULT_IDS = new HashMap<>(128);

    static {
        // Natural terrain
        map("stone", 1);
        map("granite", 1);
        map("diorite", 1);
        map("andesite", 1);
        map("deepslate", 1);
        map("grass_block", 2);
        map("dirt", 3);
        map("coarse_dirt", 3);
        map("podzol", 3);
        map("cobblestone", 4);
        map("bedrock", 7);
        map("sand", 12);
        map("red_sand", 12);
        map("gravel", 13);
        map("clay", 82);
        map("snow_block", 80);
        map("snow", 78);
        map("ice", 79);
        map("packed_ice", 174);
        map("blue_ice", 174);

        // Fluids
        map("water", 8);
        map("lava", 10);

        // Wood, leaves & foliage
        map("oak_planks", 5);
        map("spruce_planks", 5);
        map("birch_planks", 5);
        map("jungle_planks", 5);
        map("acacia_planks", 5);
        map("dark_oak_planks", 5);
        map("mangrove_planks", 5);
        map("cherry_planks", 5);
        map("bamboo_planks", 5);
        map("crimson_planks", 5);
        map("warped_planks", 5);

        map("oak_sapling", 6);
        map("spruce_sapling", 6);
        map("birch_sapling", 6);
        map("jungle_sapling", 6);
        map("acacia_sapling", 6);
        map("dark_oak_sapling", 6);
        map("cherry_sapling", 6);

        map("oak_log", 17);
        map("spruce_log", 17);
        map("birch_log", 17);
        map("jungle_log", 17);
        map("acacia_log", 17);
        map("dark_oak_log", 17);
        map("mangrove_log", 17);
        map("cherry_log", 17);

        // Leaves (ID 18: key for waving foliage in almost all packs)
        map("oak_leaves", 18);
        map("spruce_leaves", 18);
        map("birch_leaves", 18);
        map("jungle_leaves", 18);
        map("acacia_leaves", 18);
        map("dark_oak_leaves", 18);
        map("mangrove_leaves", 18);
        map("cherry_leaves", 18);
        map("azalea_leaves", 18);
        map("flowering_azalea_leaves", 18);

        // Glass
        map("glass", 20);
        map("glass_pane", 102);

        // Plants & waving vegetation
        map("short_grass", 31);
        map("grass", 31);
        map("fern", 31);
        map("dead_bush", 32);
        map("dandelion", 37);
        map("poppy", 38);
        map("blue_orchid", 38);
        map("allium", 38);
        map("azure_bluet", 38);
        map("red_tulip", 38);
        map("orange_tulip", 38);
        map("white_tulip", 38);
        map("pink_tulip", 38);
        map("oxeye_daisy", 38);
        map("cornflower", 38);
        map("lily_of_the_valley", 38);
        map("wither_rose", 38);
        map("sunflower", 175);
        map("lilac", 175);
        map("rose_bush", 175);
        map("peony", 175);
        map("tall_grass", 175);
        map("large_fern", 175);

        map("brown_mushroom", 39);
        map("red_mushroom", 40);
        map("brown_mushroom_block", 99);
        map("red_mushroom_block", 100);
        map("mushroom_stem", 99);

        // Crops & Agriculture
        map("wheat", 59);
        map("farmland", 60);
        map("cactus", 81);
        map("sugar_cane", 83);
        map("pumpkin", 86);
        map("carved_pumpkin", 86);
        map("melon", 103);
        map("pumpkin_stem", 104);
        map("melon_stem", 105);
        map("attached_pumpkin_stem", 104);
        map("attached_melon_stem", 105);
        map("vine", 106);
        map("lily_pad", 111);
        map("nether_wart", 115);
        map("cocoa", 127);
        map("carrots", 141);
        map("potatoes", 142);
        map("beetroots", 207);
        map("sweet_berry_bush", 208);
        map("kelp", 209);
        map("kelp_plant", 209);
        map("seagrass", 210);
        map("tall_seagrass", 210);

        // Light sources & Emissives
        map("torch", 50);
        map("wall_torch", 50);
        map("soul_torch", 50);
        map("soul_wall_torch", 50);
        map("fire", 51);
        map("soul_fire", 51);
        map("campfire", 51);
        map("soul_campfire", 51);
        map("glowstone", 89);
        map("jack_o_lantern", 91);
        map("redstone_lamp", 123);
        map("sea_lantern", 169);
        map("magma_block", 213);
        map("shroomlight", 89);
        map("lantern", 50);
        map("soul_lantern", 50);

        // Redstone & utility
        map("redstone_wire", 55);
        map("redstone_torch", 75);
        map("redstone_wall_torch", 75);
        map("repeater", 93);
        map("comparator", 149);
        map("redstone_block", 152);

        // Nether & End
        map("netherrack", 87);
        map("soul_sand", 88);
        map("soul_soil", 88);
        map("end_stone", 121);
        map("nether_portal", 90);
        map("end_portal", 119);
    }

    private static void map(String name, int id) {
        DEFAULT_IDS.put(ResourceLocation.withDefaultNamespace(name), id);
    }

    private PrLegacyBlockIds() {
    }

    /** Returns the legacy block ID or -1 if unmapped. */
    public static int get(ResourceLocation id) {
        return DEFAULT_IDS.getOrDefault(id, -1);
    }

    /** Populates the target map with legacy defaults for any unmapped entries. */
    public static void populateDefaults(Map<ResourceLocation, Integer> target) {
        DEFAULT_IDS.forEach(target::putIfAbsent);
    }
}
