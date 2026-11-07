package net.pryzma.item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.pryzma.Pryzma;

/**
 * Turns the rules of a reload into the published {@link PrCitIndex} in Minecraft 26.3.
 */
public final class PrCitModels {
    private static final AtomicReference<PrCitLoader.Result> PENDING = new AtomicReference<>(PrCitLoader.Result.EMPTY);
    private static final Map<String, String> LEATHER = Map.of(
            "minecraft:leather_helmet", "leather_helmet",
            "minecraft:leather_chestplate", "leather_chestplate",
            "minecraft:leather_leggings", "leather_leggings",
            "minecraft:leather_boots", "leather_boots");
    private static volatile PrCitIndex prepared;

    private PrCitModels() {
    }

    static void setPending(PrCitLoader.Result result) {
        PENDING.set(result);
    }

    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        prepared = build(PENDING.getAndSet(PrCitLoader.Result.EMPTY), event.getBakingResult().itemStackModels());
    }

    public static void onBakingCompleted(ModelEvent.BakingCompleted event) {
        PrCitIndex next = prepared;
        prepared = null;
        if (next != null) {
            PrCit.publish(next);
        }
    }

    static PrCitIndex build(PrCitLoader.Result source, Map<Identifier, ItemModel> itemModels) {
        if (source.rules().isEmpty() || itemModels == null || itemModels.isEmpty()) {
            return new PrCitIndex(Map.of(), Map.of(), source.global(), 0);
        }
        PrCitBaker baker = new PrCitBaker(source, itemModels, message -> Pryzma.LOGGER.warn("CIT: {}", message));
        Map<Item, List<PrCitEntry>> items = new IdentityHashMap<>();
        Map<Identifier, List<PrCitEntry>> enchantments = new LinkedHashMap<>();
        Set<String> missingItems = new TreeSet<>();
        int count = 0;
        for (PrCitRule rule : source.rules()) {
            Consumer<String> warn = PrCitLoader.warn(rule.location);
            PrCitEntry entry = rule.type == PrCitRule.Type.ITEM ? itemEntry(rule, baker) : plainEntry(rule, source);
            if (entry == null) {
                warn.accept("Nothing to draw");
                continue;
            }
            if (rule.type == PrCitRule.Type.ENCHANTMENT) {
                rule.enchantments.forEach(id -> enchantments.computeIfAbsent(id, k -> new ArrayList<>()).add(entry));
                count++;
                continue;
            }
            boolean any = false;
            for (Identifier id : rule.items) {
                Optional<Item> item = BuiltInRegistries.ITEM.getOptional(id);
                if (item.isPresent()) {
                    items.computeIfAbsent(item.get(), k -> new ArrayList<>()).add(entry);
                    any = true;
                } else {
                    missingItems.add(id.toString());
                }
            }
            if (any) {
                count++;
            } else {
                warn.accept("No items defined");
            }
        }
        if (!missingItems.isEmpty()) {
            Pryzma.LOGGER.info("CIT rules name {} items that are not installed, e.g. {}", missingItems.size(),
                    missingItems.stream().limit(5).toList());
            Pryzma.LOGGER.debug("CIT items not installed: {}", missingItems);
        }
        Pryzma.LOGGER.info("CIT: {} rules for {} items and {} enchantments", count, items.size(), enchantments.size());
        return new PrCitIndex(freeze(items), freeze(enchantments), source.global(), count);
    }

    private static <K> Map<K, PrCitEntry[]> freeze(Map<K, List<PrCitEntry>> lists) {
        Map<K, PrCitEntry[]> out = lists instanceof IdentityHashMap ? new IdentityHashMap<>() : new HashMap<>();
        lists.forEach((k, v) -> out.put(k, v.toArray(new PrCitEntry[0])));
        return out;
    }

    private static PrCitEntry itemEntry(PrCitRule rule, PrCitBaker baker) {
        ItemModel model = rule.model == null ? null : baker.bake(rule.model);
        Map<String, ItemModel> subModels = new HashMap<>();
        rule.models.forEach((key, id) -> {
            ItemModel sub = baker.bake(id);
            if (sub != null) {
                subModels.put("item/" + key, sub);
            }
        });
        ItemModel texture = null;
        Map<String, ItemModel> subTextures = new HashMap<>();
        List<Identifier> layers = layers(rule);
        if (layers != null) {
            texture = baker.generated(layers);
        }
        rule.textures.forEach((key, file) -> {
            ItemModel sub = baker.generated(List.of(PrCitRule.spriteOf(file)));
            if (sub != null) {
                subTextures.put("item/" + key, sub);
            }
        });
        if (model == null && subModels.isEmpty() && texture == null && subTextures.isEmpty()) {
            return null;
        }
        return PrCitEntry.item(rule, model, subModels, texture, subTextures);
    }

    static List<Identifier> layers(PrCitRule rule) {
        if (rule.items.size() == 1) {
            String item = rule.items.get(0).toString();
            boolean potion = item.equals("minecraft:potion") || item.equals("minecraft:splash_potion")
                    || item.equals("minecraft:lingering_potion");
            if (potion && rule.damage != null && !rule.damage.isEmpty()) {
                boolean splash = (rule.damage.firstMin() & PrCitPotions.SPLASH) != 0;
                Identifier overlay = layer(rule, "potion_overlay", null, "item/potion_overlay");
                Identifier bottle = splash
                        ? layer(rule, "potion_bottle_splash", rule.texture, "item/splash_potion")
                        : layer(rule, "potion_bottle_drinkable", rule.texture,
                                item.equals("minecraft:lingering_potion") ? "item/lingering_potion" : "item/potion");
                return List.of(overlay, bottle);
            }
            String leather = LEATHER.get(item);
            if (leather != null) {
                return List.of(layer(rule, leather, rule.texture, "item/" + leather),
                        layer(rule, leather + "_overlay", null, "item/" + leather + "_overlay"));
            }
        }
        return rule.texture == null ? null : List.of(PrCitRule.spriteOf(rule.texture));
    }

    private static Identifier layer(PrCitRule rule, String key, Identifier fallback, String vanilla) {
        Identifier file = rule.textures.get(key);
        if (file == null) {
            file = fallback;
        }
        return file != null ? PrCitRule.spriteOf(file) : Identifier.withDefaultNamespace(vanilla);
    }

    private static PrCitEntry plainEntry(PrCitRule rule, PrCitLoader.Result source) {
        Identifier texture = rule.texture == null ? null : source.physical().get(rule.texture);
        Map<String, Identifier> textures = new HashMap<>();
        rule.textures.forEach((key, file) -> {
            Identifier physical = source.physical().get(file);
            if (physical != null) {
                textures.put(key, physical);
            }
        });
        if (texture == null && textures.isEmpty()) {
            return null;
        }
        int width = rule.texture == null ? 16 : source.widths().getOrDefault(rule.texture, 16);
        return PrCitEntry.plain(rule, texture, textures, width);
    }
}
