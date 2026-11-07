package net.pryzma.item;

import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.resources.Identifier;

/**
 * A rule with what it draws. Item rules carry item models: a whole replacement model
 * ({@code model=}) and texture models whose quads replace the item's own ({@code texture=}),
 * each with sub-models keyed by the override model they stand in for ({@code item/bow_pulling_0}).
 * Armor, elytra and enchantment rules carry plain textures.
 */
final class PrCitEntry {
    final PrCitRule rule;
    final ItemModel model;
    final Map<String, ItemModel> subModels;
    final ItemModel texture;
    final Map<String, ItemModel> subTextures;
    final Identifier plainTexture;
    final Map<String, Identifier> plainTextures;
    /** Glint texture width in pixels; the glint is scaled by it as in OptiFine. */
    final int width;
    /** Texture models wrapped around the item models they replace; render thread only. */
    private final Map<ItemModel, ItemModel> wrapped = new IdentityHashMap<>();

    private PrCitEntry(PrCitRule rule, ItemModel model, Map<String, ItemModel> subModels, ItemModel texture,
            Map<String, ItemModel> subTextures, Identifier plainTexture, Map<String, Identifier> plainTextures,
            int width) {
        this.rule = rule;
        this.model = model;
        this.subModels = subModels;
        this.texture = texture;
        this.subTextures = subTextures;
        this.plainTexture = plainTexture;
        this.plainTextures = plainTextures;
        this.width = width;
    }

    static PrCitEntry item(PrCitRule rule, ItemModel model, Map<String, ItemModel> subModels, ItemModel texture,
            Map<String, ItemModel> subTextures) {
        return new PrCitEntry(rule, model, Map.copyOf(subModels), texture, Map.copyOf(subTextures), null, Map.of(), 16);
    }

    static PrCitEntry plain(PrCitRule rule, Identifier texture, Map<String, Identifier> textures, int width) {
        return new PrCitEntry(rule, null, Map.of(), null, Map.of(), texture, Map.copyOf(textures), width);
    }

    /** The whole-model replacement for the override model at {@code location}, if the rule has one. */
    ItemModel subModel(Identifier location) {
        return location == null ? null : subModels.get(location.getPath());
    }

    /**
     * {@code resolved} with its quads taken from the rule's texture model: the sub-texture of its
     * override location, else the main one.
     */
    ItemModel textured(ItemModel resolved, Identifier location) {
        if (texture == null && subTextures.isEmpty()) {
            return resolved;
        }
        ItemModel quads = location == null ? null : subTextures.get(location.getPath());
        if (quads == null) {
            quads = texture;
        }
        if (quads == null) {
            return resolved;
        }
        ItemModel source = quads;
        return wrapped.computeIfAbsent(resolved, r -> new PrCitTextureModel(r, source));
    }
}
