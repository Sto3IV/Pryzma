package net.pryzma.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What an OptiFine random entity / model rule may ask about the thing being rendered (OptiFine's
 * {@code IRandomEntity}). Values that do not apply are {@code null} or {@code -1}.
 */
public interface PrEntityInfo {
    /** The stable random id: the low bits of the UUID for entities, a position hash for block entities. */
    int randomId();

    BlockPos spawnPos();

    ResourceLocation spawnBiome();

    /** The custom name, or {@code null}. */
    String name();

    int health();

    int maxHealth();

    /** Villager profession id, or {@code null} when the entity has none. */
    ResourceLocation profession();

    int professionLevel();

    DyeColor color();

    /** {@code null} when the entity cannot be a baby. */
    Boolean baby();

    /** Slime size minus one or phantom size; -1 otherwise. */
    int size();

    CompoundTag nbt();

    BlockState blockState();
}
