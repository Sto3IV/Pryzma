package net.pryzma.item;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.resources.Identifier;

/**
 * Resolves CIT item models from the baked item models in Minecraft 26.3.
 */
final class PrCitBaker {
    private final PrCitLoader.Result source;
    private final Map<Identifier, ItemModel> itemModels;
    private final Consumer<String> warn;

    PrCitBaker(PrCitLoader.Result source, Map<Identifier, ItemModel> itemModels, Consumer<String> warn) {
        this.source = source;
        this.itemModels = itemModels != null ? itemModels : Map.of();
        this.warn = warn;
    }

    /** The item model at {@code id}, or {@code null} when it cannot be resolved. */
    ItemModel bake(Identifier id) {
        if (id == null) {
            return null;
        }
        ItemModel model = itemModels.get(id);
        if (model != null) {
            return model;
        }
        String path = id.getPath();
        if (path.startsWith("item/")) {
            Identifier stripped = Identifier.fromNamespaceAndPath(id.getNamespace(), path.substring(5));
            model = itemModels.get(stripped);
            if (model != null) {
                return model;
            }
        } else {
            Identifier prefixed = Identifier.fromNamespaceAndPath(id.getNamespace(), "item/" + path);
            model = itemModels.get(prefixed);
            if (model != null) {
                return model;
            }
        }
        warn.accept("Model not found: " + id);
        return null;
    }

    /** Resolves an item model matching the given texture layers. */
    ItemModel generated(List<Identifier> layers) {
        if (layers == null || layers.isEmpty()) {
            return null;
        }
        for (Identifier layer : layers) {
            ItemModel model = bake(layer);
            if (model != null) {
                return model;
            }
        }
        return null;
    }
}
