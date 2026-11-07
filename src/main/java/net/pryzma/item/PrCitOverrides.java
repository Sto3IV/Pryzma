package net.pryzma.item;

import java.util.Map;

import com.google.common.collect.MapMaker;

import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.resources.Identifier;

/**
 * Which model location each baked override model came from ({@code item/bow_pulling_0}), so a
 * resolved item model can be matched against {@code model.<name>} and {@code texture.<name>}.
 * Keys are weak and compared by identity: models of a previous resource state fall away with it.
 */
public final class PrCitOverrides {
    private static final Map<ItemModel, Identifier> LOCATIONS = new MapMaker().weakKeys().makeMap();

    private PrCitOverrides() {
    }

    /** Called for every override model the bakery bakes. */
    public static void record(ItemModel model, Identifier location) {
        if (model != null && location != null) {
            LOCATIONS.put(model, location);
        }
    }

    /** The override location {@code resolved} was baked from, or {@code null} when it is the base model. */
    static Identifier location(ItemModel resolved, ItemModel base) {
        return resolved == base || resolved == null ? null : LOCATIONS.get(resolved);
    }
}
