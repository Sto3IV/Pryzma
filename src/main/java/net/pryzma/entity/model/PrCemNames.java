package net.pryzma.entity.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The {@code .jem} names of a model layer, the scheme OptiFine packs and EMF use: the layer's model
 * path, {@code _<layer>} for layers other than {@code main}, a few historical renames, and fallback
 * names tried in order ({@code player_slim} falls back to {@code player}, cart variants to
 * {@code minecart} ...). The part name table is looked up under {@link #mapId}.
 */
public record PrCemNames(String namespace, List<String> files, String mapId) {
    private static final Map<String, String> RENAMES = Map.ofEntries(
            Map.entry("creeper_armor", "creeper_charge"),
            Map.entry("sheep_fur", "sheep_wool"),
            Map.entry("ender_dragon", "dragon"),
            Map.entry("leash_knot", "lead_knot"),
            Map.entry("pufferfish_big", "puffer_fish_big"),
            Map.entry("pufferfish_medium", "puffer_fish_medium"),
            Map.entry("pufferfish_small", "puffer_fish_small"),
            Map.entry("tropical_fish_large", "tropical_fish_b"),
            Map.entry("tropical_fish_large_pattern", "tropical_fish_pattern_b"),
            Map.entry("tropical_fish_small", "tropical_fish_a"),
            Map.entry("tropical_fish_small_pattern", "tropical_fish_pattern_a"),
            Map.entry("creeper_head", "head_creeper"),
            Map.entry("dragon_skull", "head_dragon"),
            Map.entry("piglin_head", "head_piglin"),
            Map.entry("player_head", "head_player"),
            Map.entry("skeleton_skull", "head_skeleton"),
            Map.entry("wither_skeleton_skull", "head_wither_skeleton"),
            Map.entry("zombie_head", "head_zombie"));

    private static final Map<String, String> FALLBACKS = Map.ofEntries(
            Map.entry("evoker", "evocation_illager"),
            Map.entry("vindicator", "vindication_illager"),
            Map.entry("evoker_fangs", "evocation_fangs"),
            Map.entry("player_slim", "player"),
            Map.entry("chest_minecart", "minecart"),
            Map.entry("command_block_minecart", "minecart"),
            Map.entry("spawner_minecart", "minecart"),
            Map.entry("tnt_minecart", "minecart"),
            Map.entry("furnace_minecart", "minecart"),
            Map.entry("hopper_minecart", "minecart"),
            Map.entry("salmon_small", "salmon"),
            Map.entry("salmon_large", "salmon"));

    /**
     * @param modelNamespace namespace of the layer's model id
     * @param modelPath      path of the layer's model id, e.g. {@code creeper} or {@code boat/oak}
     * @param layer          the layer, e.g. {@code main} or {@code armor}
     */
    public static PrCemNames of(String modelNamespace, String modelPath, String layer) {
        String name = "main".equals(layer) ? modelPath : modelPath + "_" + layer;
        if (!"minecraft".equals(modelNamespace)) {
            String file = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
            return new PrCemNames(modelNamespace, List.of(file, "modded/" + modelNamespace + "/" + file), file);
        }
        // Boats share one model per kind: boat/<wood> is "boat", bamboo is a raft.
        if (modelPath.startsWith("boat/") || modelPath.startsWith("chest_boat/")) {
            boolean chest = modelPath.startsWith("chest_");
            boolean raft = modelPath.endsWith("/bamboo");
            String kind = (chest ? "chest_" : "") + (raft ? "raft" : "boat");
            return new PrCemNames("minecraft", List.of(kind), kind);
        }
        String primary = RENAMES.getOrDefault(name, name);
        List<String> files = new ArrayList<>();
        files.add(primary);
        String mapId = primary;
        String fallback = FALLBACKS.get(primary);
        if (fallback != null) {
            files.add(fallback);
            if (!primary.equals("evoker") && !primary.equals("vindicator") && !primary.equals("evoker_fangs")) {
                mapId = fallback;
            }
        }
        if (primary.endsWith("_collar")) {
            files.add(primary.substring(0, primary.length() - "_collar".length()));
        }
        if (primary.endsWith("_inner_armor") && !primary.equals("inner_armor")) {
            files.add("inner_armor");
            mapId = "inner_armor";
        } else if (primary.endsWith("_outer_armor") && !primary.equals("outer_armor")) {
            files.add("outer_armor");
            mapId = "outer_armor";
        }
        return new PrCemNames("minecraft", List.copyOf(files), mapId);
    }
}
