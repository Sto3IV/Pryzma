package net.pryzma.light;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.GlowSquid;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.pryzma.PryzmaConfig;

/**
 * Native Dynamic Lights engine for Pryzma 2.0.
 * <p>
 * Evaluates light emission from held items, ignited entities, blazes, magma cubes, glowing entities,
 * and dropped items. Propagates light using smooth Euclidean distance falloff (R = 7.5 blocks)
 * and injects dynamic light into rendering pipelines (entities, block entities, held items,
 * and shaders) without triggering costly chunk meshing re-bakes.
 */
public final class PrDynamicLights {
    public static final double MAX_DIST = 7.5;
    public static final double MAX_DIST_SQ = 56.25;

    public static final class LightSource {
        public final int entityId;
        public double x;
        public double y;
        public double z;
        public int lightLevel;

        public LightSource(int entityId, double x, double y, double z, int lightLevel) {
            this.entityId = entityId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.lightLevel = lightLevel;
        }
    }

    private static final LightSource[] EMPTY_SOURCES = new LightSource[0];
    private static volatile LightSource[] ACTIVE_ARRAY = EMPTY_SOURCES;
    private static volatile boolean HAS_SOURCES = false;
    private static long lastUpdateMs = 0L;

    private PrDynamicLights() {}

    /** Whether Dynamic Lights is enabled (1 = Fast, 2 = Fancy, 3 = OFF). */
    public static boolean isEnabled() {
        return PryzmaConfig.prDynamicLights != 3;
    }

    /** Whether dynamic lights has any currently active light sources in the scene. */
    public static boolean hasSources() {
        return HAS_SOURCES;
    }

    /** Whether Fancy mode is enabled (smooth per-frame interpolation vs stepped updates). */
    public static boolean isFancy() {
        return PryzmaConfig.prDynamicLights == 2;
    }

    /** Returns the intrinsic light level of an item stack. */
    public static int getLightLevel(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        Item item = stack.getItem();
        if (item instanceof BlockItem blockItem) {
            Block block = blockItem.getBlock();
            if (block == Blocks.LIGHT) {
                return 0;
            }
            if (block == Blocks.GLOW_LICHEN) {
                return 6;
            }
            if (block == Blocks.CAVE_VINES || block == Blocks.CAVE_VINES_PLANT) {
                return 12;
            }
            return block.defaultBlockState().getLightEmission();
        }
        if (item == Items.LAVA_BUCKET) {
            return 15;
        }
        if (item == Items.BLAZE_ROD || item == Items.BLAZE_POWDER) {
            return 10;
        }
        if (item == Items.GLOWSTONE_DUST || item == Items.PRISMARINE_CRYSTALS) {
            return 8;
        }
        if (item == Items.MAGMA_CREAM || item == Items.GLOW_INK_SAC || item == Items.GLOW_ITEM_FRAME) {
            return 8;
        }
        if (item == Items.NETHER_STAR) {
            return 7;
        }
        return 0;
    }

    /** Returns the intrinsic light level emitted by an entity. */
    public static int getLightLevel(Entity entity) {
        if (!isEnabled() || entity == null) {
            return 0;
        }
        if (entity instanceof Player player && player.isSpectator()) {
            return 0;
        }
        if (entity.isOnFire()) {
            return 15;
        }
        if (entity instanceof AbstractHurtingProjectile || entity instanceof PrimedTnt) {
            return 15;
        }
        if (entity instanceof Blaze blaze) {
            return blaze.isOnFire() ? 15 : 10;
        }
        if (entity instanceof MagmaCube magmaCube) {
            return magmaCube.squish > 0.6F ? 13 : 8;
        }
        if (entity instanceof Creeper creeper && creeper.getSwelling(0.0F) > 0.001F) {
            return 15;
        }
        if (entity instanceof GlowSquid glowSquid) {
            return (int) Mth.clampedLerp(0.0F, 11.0F, 1.0F - (float) glowSquid.getDarkTicksRemaining() / 10.0F);
        }
        if (entity instanceof GlowItemFrame) {
            return 8;
        }
        if (entity instanceof LivingEntity living) {
            int main = getLightLevel(living.getMainHandItem());
            int off = getLightLevel(living.getOffhandItem());
            int head = getLightLevel(living.getItemBySlot(EquipmentSlot.HEAD));
            return Math.max(Math.max(main, off), head);
        }
        if (entity instanceof ItemEntity itemEntity) {
            return getLightLevel(itemEntity.getItem());
        }
        return 0;
    }

    /** Computes interpolated dynamic light level at specific 3D coordinates (0.0 to 15.0). */
    public static double getLightLevelAt(double x, double y, double z) {
        if (!HAS_SOURCES) {
            return 0.0;
        }
        LightSource[] sources = ACTIVE_ARRAY;
        int len = sources.length;
        if (len == 0) {
            return 0.0;
        }
        double maxLevel = 0.0;
        for (int i = 0; i < len; i++) {
            LightSource src = sources[i];
            if (src.lightLevel <= 0) {
                continue;
            }
            double dx = x - src.x;
            double dy = y - src.y;
            double dz = z - src.z;
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq < MAX_DIST_SQ) {
                double dist = Math.sqrt(distSq);
                double falloff = 1.0 - (dist / MAX_DIST);
                double level = falloff * src.lightLevel;
                if (level > maxLevel) {
                    maxLevel = level;
                }
            }
        }
        return Math.min(15.0, Math.max(0.0, maxLevel));
    }

    /** Blends dynamic light level (0.0..15.0) into a packed light integer. */
    public static int blendLight(double dynamicLight, int vanillaPackedLight) {
        if (dynamicLight <= 0.0) {
            return vanillaPackedLight;
        }
        int dynamicBlock = (int) (dynamicLight * 16.0);
        int vanillaBlock = vanillaPackedLight & 0xFFFF;
        if (dynamicBlock > vanillaBlock) {
            return (vanillaPackedLight & 0xFFFF0000) | (dynamicBlock & 0xFFFF);
        }
        return vanillaPackedLight;
    }

    /** Injects dynamic light at a block position into packed light coordinates. */
    public static int getCombinedLight(BlockPos pos, int vanillaPackedLight) {
        if (!isEnabled() || pos == null) {
            return vanillaPackedLight;
        }
        double dynamic = getLightLevelAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        return blendLight(dynamic, vanillaPackedLight);
    }

    /** Injects dynamic light onto a rendered entity. */
    public static int getEntityLight(Entity entity, int vanillaPackedLight) {
        if (!isEnabled() || entity == null) {
            return vanillaPackedLight;
        }
        double dynamic = getLightLevelAt(entity.getX(), entity.getY() + entity.getEyeHeight() * 0.5, entity.getZ());
        int ownLight = getLightLevel(entity);
        if (ownLight > 0) {
            dynamic = Math.max(dynamic, (double) ownLight);
        }
        return blendLight(dynamic, vanillaPackedLight);
    }

    /** Injects dynamic light onto a rendered block entity. */
    public static int getBlockEntityLight(BlockEntity blockEntity, int vanillaPackedLight) {
        if (!isEnabled() || blockEntity == null) {
            return vanillaPackedLight;
        }
        return getCombinedLight(blockEntity.getBlockPos(), vanillaPackedLight);
    }

    /** Injects dynamic light onto held items in first or third person. */
    public static int getItemInHandLight(LivingEntity holder, int vanillaPackedLight) {
        if (!isEnabled() || holder == null) {
            return vanillaPackedLight;
        }
        int ownLight = getLightLevel(holder);
        double dynamic = Math.max((double) ownLight, getLightLevelAt(holder.getX(), holder.getEyeY(), holder.getZ()));
        return blendLight(dynamic, vanillaPackedLight);
    }

    /** Updates active light sources from the current client level. Called per frame from LevelRenderer. */
    public static void update(LevelRenderer levelRenderer, ClientLevel level) {
        if (!isEnabled() || level == null) {
            clear();
            return;
        }
        long now = System.currentTimeMillis();
        long interval = isFancy() ? 40L : 200L;
        if (now - lastUpdateMs < interval) {
            return;
        }
        lastUpdateMs = now;

        List<LightSource> active = new ArrayList<>();
        for (Entity entity : level.entitiesForRendering()) {
            int light = getLightLevel(entity);
            if (light > 0) {
                LightSource src = new LightSource(entity.getId(), entity.getX(), entity.getY(), entity.getZ(), light);
                active.add(src);
            }
        }
        LightSource[] arr = active.toArray(EMPTY_SOURCES);
        ACTIVE_ARRAY = arr;
        HAS_SOURCES = arr.length > 0;
    }

    /** Clears all tracked light sources. */
    public static void clear() {
        ACTIVE_ARRAY = EMPTY_SOURCES;
        HAS_SOURCES = false;
    }

    /** Returns count of currently active dynamic light sources. */
    public static int getSourceCount() {
        return ACTIVE_ARRAY.length;
    }
}
