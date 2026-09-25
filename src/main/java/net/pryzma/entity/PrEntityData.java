package net.pryzma.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Client-side facts about one entity that OptiFine rules read: where it spawned (random textures and
 * models match {@code biomes} and {@code heights} against the spawn point, not the current one),
 * cached NBT and the block it stands on, and the {@code var.*} values of CEM animations.
 */
public final class PrEntityData {
    private static final long NBT_REFRESH_MS = 1000L;
    private static final long BLOCK_REFRESH_MS = 50L;

    public BlockPos spawnPos;
    public ResourceLocation spawnBiome;

    private CompoundTag nbt;
    private long nbtTime;
    private BlockState blockOn;
    private long blockOnTime;

    /** CEM {@code var.*} and {@code varb.*} values of this entity, indexed by the model's variable slots. */
    public float[] cemVars;

    /** Holder interface mixed into {@link Entity}. */
    public interface Holder {
        PrEntityData prData();
    }

    public static PrEntityData of(Entity entity) {
        return ((Holder) entity).prData();
    }

    /** The entity's NBT as {@code saveWithoutId} writes it, refreshed at most once a second (OptiFine). */
    public CompoundTag nbt(Entity entity) {
        long now = System.currentTimeMillis();
        if (nbt == null || now - nbtTime > NBT_REFRESH_MS) {
            CompoundTag tag = new CompoundTag();
            try {
                entity.saveWithoutId(tag);
            } catch (RuntimeException e) {
                // Client copies of some modded entities cannot serialise; the rule then sees no tags.
            }
            if (entity instanceof TamableAnimal tamable) {
                tag.putBoolean("Sitting", tamable.isInSittingPose());
            }
            nbt = tag;
            nbtTime = now;
        }
        return nbt;
    }

    /** The block at the entity's feet, or the one below when that is air; refreshed every 50 ms. */
    public BlockState blockOn(Entity entity) {
        long now = System.currentTimeMillis();
        if (blockOn == null || now - blockOnTime > BLOCK_REFRESH_MS) {
            BlockPos pos = entity.blockPosition();
            BlockState state = entity.level().getBlockState(pos);
            blockOn = state.isAir() ? entity.level().getBlockState(pos.below()) : state;
            blockOnTime = now;
        }
        return blockOn;
    }
}
