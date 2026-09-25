package net.pryzma.item;

import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** What a CIT rule reads from an item stack. The game wraps an {@code ItemStack}; tests supply their own. */
public interface PrCitItem {
    ResourceLocation item();

    /** OptiFine's damage: the damage value, or a potion's legacy value; negative for a potion without one. */
    int damage();

    int maxDamage();

    int count();

    /** Number of enchantments (stored ones for an enchanted book). */
    int enchantments();

    ResourceLocation enchantment(int index);

    int enchantmentLevel(int index);

    /**
     * A compound holding component {@code id} encoded as NBT under its id, or every component for
     * {@code *}; empty when the item lacks it.
     */
    Tag components(String id);

    /** Whether the item is drawn in the off hand. */
    boolean offHand();
}
