package net.pryzma.item;

import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;

/**
 * A rule with what it draws. Item rules carry baked models: a whole replacement model
 * ({@code model=}) and texture models whose quads replace the item's own ({@code texture=}),
 * each with sub-models keyed by the override model they stand in for ({@code item/bow_pulling_0}).
 * Armor, elytra and enchantment rules carry plain textures.
 */
final class PrCitEntry {
    final PrCitRule rule;
    final BakedModel model;
    final Map<String, BakedModel> subModels;
    final BakedModel texture;
    final Map<String, BakedModel> subTextures;
    final ResourceLocation plainTexture;
    final Map<String, ResourceLocation> plainTextures;
    /** Glint texture width in pixels; the glint is scaled by it as in OptiFine. */
    final int width;
    /** Texture models wrapped around the item models they replace the quads of; render thread only. */
    private final Map<BakedModel, BakedModel> wrapped = new IdentityHashMap<>();
    /** Enchantment layer render types by kind and strength, created on first use; render thread only. */
    RenderType[] glintTypes;

    private PrCitEntry(PrCitRule rule, BakedModel model, Map<String, BakedModel> subModels, BakedModel texture,
            Map<String, BakedModel> subTextures, ResourceLocation plainTexture, Map<String, ResourceLocation> plainTextures,
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

    static PrCitEntry item(PrCitRule rule, BakedModel model, Map<String, BakedModel> subModels, BakedModel texture,
            Map<String, BakedModel> subTextures) {
        return new PrCitEntry(rule, model, Map.copyOf(subModels), texture, Map.copyOf(subTextures), null, Map.of(), 16);
    }

    static PrCitEntry plain(PrCitRule rule, ResourceLocation texture, Map<String, ResourceLocation> textures, int width) {
        return new PrCitEntry(rule, null, Map.of(), null, Map.of(), texture, Map.copyOf(textures), width);
    }

    /** The whole-model replacement for the override model at {@code location}, if the rule has one. */
    BakedModel subModel(ResourceLocation location) {
        return location == null ? null : subModels.get(location.getPath());
    }

    /**
     * {@code resolved} with its quads taken from the rule's texture model: the sub-texture of its
     * override location, else the main one. Transforms stay the item's own; 3D models are left
     * alone, as OptiFine does.
     */
    BakedModel textured(BakedModel resolved, ResourceLocation location) {
        if (texture == null && subTextures.isEmpty() || resolved.isGui3d()) {
            return resolved;
        }
        BakedModel quads = location == null ? null : subTextures.get(location.getPath());
        if (quads == null) {
            quads = texture;
        }
        if (quads == null) {
            return resolved;
        }
        BakedModel source = quads;
        return wrapped.computeIfAbsent(resolved, r -> new PrCitTextureModel(r, source));
    }
}
