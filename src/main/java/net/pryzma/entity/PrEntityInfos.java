package net.pryzma.entity;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.npc.VillagerDataHolder;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.entity.BedBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.pryzma.core.PrHash;

/**
 * {@link PrEntityInfo} views of entities and block entities with OptiFine's definitions
 * ({@code RandomEntity}, {@code RandomTileEntity}). One mutable view per kind is reused on the
 * render thread.
 */
public final class PrEntityInfos {
    private static final EntityView ENTITY = new EntityView();
    private static final BlockEntityView BLOCK_ENTITY = new BlockEntityView();

    private PrEntityInfos() {
    }

    /** The shared view of {@code entity}. Render thread only; valid until the next call. */
    public static PrEntityInfo entity(Entity entity) {
        ENTITY.entity = entity;
        return ENTITY;
    }

    public static PrEntityInfo blockEntity(BlockEntity blockEntity) {
        BLOCK_ENTITY.blockEntity = blockEntity;
        return BLOCK_ENTITY;
    }

    /** OptiFine's random id of an entity: the low 31 bits of the UUID's least significant half. */
    public static int randomId(UUID uuid) {
        return (int) (uuid.getLeastSignificantBits() & 0x7FFFFFFFL);
    }

    /** Records where a client entity appeared: random rules match biome and height at that point. */
    public static void recordSpawn(Entity entity, Level level) {
        PrEntityData data = PrEntityData.of(entity);
        BlockPos pos = entity.blockPosition();
        data.spawnPos = pos;
        data.spawnBiome = level.getBiome(pos).unwrapKey().map(k -> k.location()).orElse(null);
    }

    private static final class EntityView implements PrEntityInfo {
        Entity entity;

        @Override
        public int randomId() {
            return PrEntityInfos.randomId(entity.getUUID());
        }

        @Override
        public BlockPos spawnPos() {
            BlockPos pos = PrEntityData.of(entity).spawnPos;
            return pos != null ? pos : entity.blockPosition();
        }

        @Override
        public ResourceLocation spawnBiome() {
            ResourceLocation biome = PrEntityData.of(entity).spawnBiome;
            if (biome == null) {
                biome = entity.level().getBiome(entity.blockPosition()).unwrapKey().map(k -> k.location()).orElse(null);
            }
            return biome;
        }

        @Override
        public String name() {
            return entity.hasCustomName() ? entity.getCustomName().getString() : null;
        }

        @Override
        public int health() {
            return entity instanceof LivingEntity living ? (int) living.getHealth() : 0;
        }

        @Override
        public int maxHealth() {
            return entity instanceof LivingEntity living ? (int) living.getMaxHealth() : 0;
        }

        @Override
        public ResourceLocation profession() {
            return entity instanceof VillagerDataHolder villager
                    ? BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession()) : null;
        }

        @Override
        public int professionLevel() {
            return entity instanceof VillagerDataHolder villager ? villager.getVillagerData().getLevel() : 0;
        }

        @Override
        public DyeColor color() {
            if (entity instanceof Wolf wolf) {
                return wolf.isTame() ? wolf.getCollarColor() : null;
            }
            if (entity instanceof Cat cat) {
                return cat.isTame() ? cat.getCollarColor() : null;
            }
            if (entity instanceof Sheep sheep) {
                return sheep.getColor();
            }
            return entity instanceof Llama llama ? llama.getSwag() : null;
        }

        @Override
        public Boolean baby() {
            return entity instanceof LivingEntity living ? living.isBaby() : null;
        }

        @Override
        public int size() {
            if (entity instanceof Slime slime) {
                return slime.getSize() - 1;
            }
            return entity instanceof Phantom phantom ? phantom.getPhantomSize() : -1;
        }

        @Override
        public CompoundTag nbt() {
            return PrEntityData.of(entity).nbt(entity);
        }

        @Override
        public BlockState blockState() {
            if (entity instanceof ItemEntity item && item.getItem().getItem() instanceof BlockItem block) {
                return block.getBlock().defaultBlockState();
            }
            return PrEntityData.of(entity).blockOn(entity);
        }
    }

    private static final class BlockEntityView implements PrEntityInfo {
        BlockEntity blockEntity;
        private BlockEntity nbtOwner;
        private CompoundTag nbt;
        private long nbtTime;

        @Override
        public int randomId() {
            BlockPos pos = spawnPos();
            return PrHash.random(pos.getX(), pos.getY(), pos.getZ(), 0);
        }

        /** A bed's head uses the foot's position, so both halves pick the same variant. */
        @Override
        public BlockPos spawnPos() {
            if (blockEntity instanceof BedBlockEntity) {
                BlockState state = blockEntity.getBlockState();
                if (state.hasProperty(BedBlock.PART) && state.getValue(BedBlock.PART) == BedPart.HEAD) {
                    Direction facing = state.getValue(BedBlock.FACING);
                    return blockEntity.getBlockPos().relative(facing.getOpposite());
                }
            }
            return blockEntity.getBlockPos();
        }

        @Override
        public ResourceLocation spawnBiome() {
            Level level = blockEntity.getLevel();
            return level == null ? null
                    : level.getBiome(blockEntity.getBlockPos()).unwrapKey().map(k -> k.location()).orElse(null);
        }

        @Override
        public String name() {
            return blockEntity instanceof Nameable nameable && nameable.hasCustomName()
                    ? nameable.getCustomName().getString() : null;
        }

        @Override
        public int health() {
            return -1;
        }

        @Override
        public int maxHealth() {
            return -1;
        }

        @Override
        public ResourceLocation profession() {
            return null;
        }

        @Override
        public int professionLevel() {
            return 0;
        }

        @Override
        public DyeColor color() {
            if (blockEntity instanceof BedBlockEntity bed) {
                return bed.getColor();
            }
            return blockEntity instanceof ShulkerBoxBlockEntity box ? box.getColor() : null;
        }

        @Override
        public Boolean baby() {
            return null;
        }

        @Override
        public int size() {
            return -1;
        }

        @Override
        public CompoundTag nbt() {
            long now = System.currentTimeMillis();
            if (nbtOwner != blockEntity || nbt == null || now - nbtTime > 1000L) {
                Level level = blockEntity.getLevel();
                nbt = level == null ? new CompoundTag() : blockEntity.saveWithoutMetadata(level.registryAccess());
                nbtOwner = blockEntity;
                nbtTime = now;
            }
            return nbt;
        }

        @Override
        public BlockState blockState() {
            return blockEntity.getBlockState();
        }
    }
}
