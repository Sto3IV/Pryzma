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
import java.util.function.Function;

import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.pryzma.Pryzma;

/**
 * Turns the rules of a reload into the published {@link PrCitIndex}: item rules are baked once
 * the block atlas is stitched ({@code ModifyBakingResult}, on the reload worker) and the result
 * goes live with the new models and atlas ({@code BakingCompleted}, on the render thread), so a
 * frame never mixes new models with the old atlas.
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
        prepared = build(PENDING.getAndSet(PrCitLoader.Result.EMPTY), event.getTextureGetter());
    }

    public static void onBakingCompleted(ModelEvent.BakingCompleted event) {
        PrCitIndex next = prepared;
        prepared = null;
        if (next != null) {
            PrCit.publish(next);
        }
    }

    static PrCitIndex build(PrCitLoader.Result source, Function<Material, TextureAtlasSprite> sprites) {
        if (source.rules().isEmpty()) {
            return new PrCitIndex(Map.of(), Map.of(), source.global(), 0);
        }
        PrCitBaker baker = new PrCitBaker(source, known(source, sprites), message -> Pryzma.LOGGER.warn("CIT: {}", message));
        Map<Item, List<PrCitEntry>> items = new IdentityHashMap<>();
        Map<ResourceLocation, List<PrCitEntry>> enchantments = new LinkedHashMap<>();
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
            for (ResourceLocation id : rule.items) {
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
            // Packs list items of mods that may not be installed; the full list is for debugging only.
            Pryzma.LOGGER.info("CIT rules name {} items that are not installed, e.g. {}", missingItems.size(),
                    missingItems.stream().limit(5).toList());
            Pryzma.LOGGER.debug("CIT items not installed: {}", missingItems);
        }
        Pryzma.LOGGER.info("CIT: {} rules for {} items and {} enchantments", count, items.size(), enchantments.size());
        return new PrCitIndex(freeze(items), freeze(enchantments), source.global(), count);
    }

    /**
     * The atlas lookup, except that block atlas sprites the loader looked for and did not find
     * resolve to the missing sprite at once: the loader already reported them, and the game
     * would log a stack trace for each model using one.
     */
    private static Function<Material, TextureAtlasSprite> known(PrCitLoader.Result source, Function<Material, TextureAtlasSprite> sprites) {
        TextureAtlasSprite missing = sprites.apply(new Material(TextureAtlas.LOCATION_BLOCKS, MissingTextureAtlasSprite.getLocation()));
        return material -> {
            ResourceLocation id = material.texture();
            boolean looked = material.atlasLocation().equals(TextureAtlas.LOCATION_BLOCKS) && !id.getPath().startsWith("block/")
                    && !id.getPath().startsWith("item/") && !source.sprites().containsKey(id)
                    && !id.equals(MissingTextureAtlasSprite.getLocation());
            return looked ? missing : sprites.apply(material);
        };
    }

    private static <K> Map<K, PrCitEntry[]> freeze(Map<K, List<PrCitEntry>> lists) {
        Map<K, PrCitEntry[]> out = lists instanceof IdentityHashMap ? new IdentityHashMap<>() : new HashMap<>();
        lists.forEach((k, v) -> out.put(k, v.toArray(new PrCitEntry[0])));
        return out;
    }

    private static PrCitEntry itemEntry(PrCitRule rule, PrCitBaker baker) {
        BakedModel model = rule.model == null ? null : baker.bake(rule.model);
        Map<String, BakedModel> subModels = new HashMap<>();
        rule.models.forEach((key, id) -> {
            BakedModel sub = baker.bake(id);
            if (sub != null) {
                subModels.put("item/" + key, sub);
            }
        });
        BakedModel texture = null;
        Map<String, BakedModel> subTextures = new HashMap<>();
        List<ResourceLocation> layers = layers(rule);
        if (layers != null) {
            texture = baker.generated(layers);
        }
        rule.textures.forEach((key, file) -> subTextures.put("item/" + key, baker.generated(List.of(PrCitRule.spriteOf(file)))));
        if (model == null && subModels.isEmpty() && texture == null && subTextures.isEmpty()) {
            return null;
        }
        return PrCitEntry.item(rule, model, subModels, texture, subTextures);
    }

    /**
     * OptiFine {@code getModelTextures}: the layers of the replacement item model. A potion keeps
     * its tinted overlay under the bottle and leather armor its overlay over the dyed layer; a
     * plain {@code texture=} stands in for the bottle or the dyed layer (OptiFine ignores it there).
     */
    static List<ResourceLocation> layers(PrCitRule rule) {
        if (rule.items.size() == 1) {
            String item = rule.items.get(0).toString();
            boolean potion = item.equals("minecraft:potion") || item.equals("minecraft:splash_potion")
                    || item.equals("minecraft:lingering_potion");
            if (potion && rule.damage != null && !rule.damage.isEmpty()) {
                boolean splash = (rule.damage.firstMin() & PrCitPotions.SPLASH) != 0;
                ResourceLocation overlay = layer(rule, "potion_overlay", null, "item/potion_overlay");
                ResourceLocation bottle = splash
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

    private static ResourceLocation layer(PrCitRule rule, String key, ResourceLocation fallback, String vanilla) {
        ResourceLocation file = rule.textures.get(key);
        if (file == null) {
            file = fallback;
        }
        return file != null ? PrCitRule.spriteOf(file) : ResourceLocation.withDefaultNamespace(vanilla);
    }

    private static PrCitEntry plainEntry(PrCitRule rule, PrCitLoader.Result source) {
        ResourceLocation texture = rule.texture == null ? null : source.physical().get(rule.texture);
        Map<String, ResourceLocation> textures = new HashMap<>();
        rule.textures.forEach((key, file) -> {
            ResourceLocation physical = source.physical().get(file);
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
