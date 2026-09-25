package net.pryzma.item;

import java.util.Map;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/**
 * The published CIT state: item, armor and elytra rules per item and enchantment rules per
 * enchantment, each list in OptiFine order, plus the global settings. Immutable once built.
 */
final class PrCitIndex {
    static final PrCitIndex EMPTY = new PrCitIndex(Map.of(), Map.of(), PrCitGlobal.DEFAULT, 0);

    final Map<Item, PrCitEntry[]> items;
    final Map<ResourceLocation, PrCitEntry[]> enchantments;
    final PrCitGlobal global;
    final int rules;

    PrCitIndex(Map<Item, PrCitEntry[]> items, Map<ResourceLocation, PrCitEntry[]> enchantments, PrCitGlobal global, int rules) {
        this.items = items;
        this.enchantments = enchantments;
        this.global = global;
        this.rules = rules;
    }

    boolean isEmpty() {
        return items.isEmpty() && enchantments.isEmpty() && global.useGlint();
    }
}
