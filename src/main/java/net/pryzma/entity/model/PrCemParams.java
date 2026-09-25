package net.pryzma.entity.model;

import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.pryzma.core.PrHash;
import net.pryzma.core.expr.PrExpr;

/**
 * The entity parameters of CEM animations, with the definitions of OptiFine's
 * {@code RenderEntityParameterFloat} and {@code RenderEntityParameterBool}.
 */
final class PrCemParams {
    private PrCemParams() {
    }

    /** The parameter called {@code name}, or {@code null} when there is none. */
    static PrExpr param(String name) {
        return switch (name) {
            case "limb_swing" -> (PrExpr.F) () -> boatOrCart(true, PrCemContext.limbSwing);
            case "limb_speed" -> (PrExpr.F) () -> boatOrCart(false, PrCemContext.limbSpeed);
            case "age" -> (PrExpr.F) () -> PrCemContext.age;
            case "head_yaw" -> (PrExpr.F) () -> PrCemContext.headYaw;
            case "head_pitch" -> (PrExpr.F) () -> PrCemContext.headPitch;
            case "health" -> (PrExpr.F) () -> living() == null ? 0.0F : living().getHealth();
            case "max_health" -> (PrExpr.F) () -> living() == null ? 0.0F : living().getMaxHealth();
            case "hurt_time" -> (PrExpr.F) () -> {
                LivingEntity l = living();
                return l == null || l.hurtTime <= 0 ? 0.0F : l.hurtTime - PrCemContext.partialTick;
            };
            case "death_time" -> (PrExpr.F) () -> {
                LivingEntity l = living();
                return l == null || l.deathTime <= 0 ? 0.0F : l.deathTime + PrCemContext.partialTick;
            };
            case "idle_time" -> (PrExpr.F) () -> {
                LivingEntity l = living();
                return l == null || l.getNoActionTime() <= 0 ? 0.0F : l.getNoActionTime() + PrCemContext.partialTick;
            };
            case "move_forward" -> (PrExpr.F) () -> living() == null ? 0.0F : living().zza;
            case "move_strafing" -> (PrExpr.F) () -> living() == null ? 0.0F : living().xxa;
            case "pos_x" -> (PrExpr.F) () -> position(0);
            case "pos_y" -> (PrExpr.F) () -> position(1);
            case "pos_z" -> (PrExpr.F) () -> position(2);
            case "rot_x" -> (PrExpr.F) () -> {
                Entity e = PrCemContext.entity;
                return e == null ? 0.0F : toRad(Mth.lerp(PrCemContext.partialTick, e.xRotO, e.getXRot()));
            };
            case "rot_y" -> (PrExpr.F) () -> {
                Entity e = PrCemContext.entity;
                if (e instanceof LivingEntity l) {
                    return toRad(Mth.lerp(PrCemContext.partialTick, l.yBodyRotO, l.yBodyRot));
                }
                return e == null ? 0.0F : toRad(Mth.lerp(PrCemContext.partialTick, e.yRotO, e.getYRot()));
            };
            case "id" -> (PrExpr.F) PrCemParams::id;
            case "player_pos_x" -> (PrExpr.F) () -> player() == null ? 0.0F : (float) Mth.lerp(PrCemContext.partialTick, player().xo, player().getX());
            case "player_pos_y" -> (PrExpr.F) () -> player() == null ? 0.0F : (float) Mth.lerp(PrCemContext.partialTick, player().yo, player().getY());
            case "player_pos_z" -> (PrExpr.F) () -> player() == null ? 0.0F : (float) Mth.lerp(PrCemContext.partialTick, player().zo, player().getZ());
            case "player_rot_x" -> (PrExpr.F) () -> player() == null ? 0.0F : toRad(Mth.lerp(PrCemContext.partialTick, player().xRotO, player().getXRot()));
            case "player_rot_y" -> (PrExpr.F) () -> player() == null ? 0.0F : toRad(Mth.lerp(PrCemContext.partialTick, player().yRotO, player().getYRot()));
            case "frame_time" -> (PrExpr.F) () -> PrCemContext.frameSeconds;
            case "frame_counter" -> (PrExpr.F) () -> PrCemContext.frames;
            case "anger_time" -> (PrExpr.F) () -> living() instanceof NeutralMob n ? n.getRemainingPersistentAngerTime() : 0.0F;
            case "anger_time_start" -> (PrExpr.F) () -> 0.0F;
            case "swing_progress" -> (PrExpr.F) () -> living() == null ? 0.0F : living().getAttackAnim(PrCemContext.partialTick);
            case "dimension" -> (PrExpr.F) PrCemParams::dimension;
            case "rule_index" -> (PrExpr.F) () -> PrCemContext.ruleIndex;
            case "is_alive" -> (PrExpr.B) () -> PrCemContext.entity != null && PrCemContext.entity.isAlive();
            case "is_aggressive" -> (PrExpr.B) () -> PrCemContext.entity instanceof Mob m && m.isAggressive();
            case "is_burning" -> (PrExpr.B) () -> PrCemContext.entity != null && PrCemContext.entity.isOnFire();
            case "is_child" -> (PrExpr.B) () -> living() != null && living().isBaby();
            case "is_glowing" -> (PrExpr.B) () -> PrCemContext.entity != null && PrCemContext.entity.isCurrentlyGlowing();
            case "is_hurt" -> (PrExpr.B) () -> living() != null && living().hurtTime > 0;
            case "is_in_hand" -> (PrExpr.B) () -> isHandContext(PrCemContext.itemContext);
            case "is_in_item_frame" -> (PrExpr.B) () -> PrCemContext.itemContext == ItemDisplayContext.FIXED;
            case "is_in_gui" -> (PrExpr.B) () -> PrCemContext.itemContext == ItemDisplayContext.GUI;
            case "is_on_head" -> (PrExpr.B) () -> PrCemContext.itemContext == ItemDisplayContext.HEAD;
            case "is_in_ground" -> (PrExpr.B) () -> PrCemContext.entity instanceof AbstractArrow a
                    && (a.inGround || a.tickCount == 0 && a.xo == 0.0 && a.yo == 0.0 && a.zo == 0.0);
            case "is_in_lava" -> (PrExpr.B) () -> PrCemContext.entity != null && PrCemContext.entity.isInLava();
            case "is_in_water" -> (PrExpr.B) () -> {
                if (PrCemContext.blockEntity != null) {
                    var state = PrCemContext.blockEntity.getBlockState();
                    return state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED);
                }
                return PrCemContext.entity != null && PrCemContext.entity.isInWater();
            };
            case "is_invisible" -> (PrExpr.B) () -> PrCemContext.entity != null && PrCemContext.entity.isInvisible();
            case "is_on_ground" -> (PrExpr.B) () -> PrCemContext.entity != null && PrCemContext.entity.onGround();
            case "is_on_shoulder" -> (PrExpr.B) () -> false;
            case "is_ridden" -> (PrExpr.B) () -> PrCemContext.entity != null && PrCemContext.entity.isVehicle();
            case "is_riding" -> (PrExpr.B) () -> PrCemContext.entity != null && PrCemContext.entity.isPassenger();
            case "is_sitting" -> (PrExpr.B) () -> PrCemContext.entity instanceof TamableAnimal t ? t.isInSittingPose()
                    : PrCemContext.entity instanceof Fox f && f.isSitting();
            case "is_sneaking" -> (PrExpr.B) () -> PrCemContext.entity != null && PrCemContext.entity.isCrouching();
            case "is_sprinting" -> (PrExpr.B) () -> PrCemContext.entity != null && PrCemContext.entity.isSprinting();
            case "is_tamed" -> (PrExpr.B) () -> PrCemContext.entity instanceof TamableAnimal t && t.isTame();
            case "is_wet" -> (PrExpr.B) () -> PrCemContext.entity != null && PrCemContext.entity.isInWaterOrRain();
            default -> null;
        };
    }

    private static LivingEntity living() {
        return PrCemContext.entity instanceof LivingEntity l ? l : null;
    }

    private static LocalPlayer player() {
        return Minecraft.getInstance().player;
    }

    private static boolean isHandContext(ItemDisplayContext c) {
        return c == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || c == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || c == ItemDisplayContext.THIRD_PERSON_LEFT_HAND || c == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
    }

    /** Boats report their rowing as limb swing, carts their travel along the rail (OptiFine). */
    private static float boatOrCart(boolean swing, float fallback) {
        Entity e = PrCemContext.entity;
        if (e instanceof Boat boat) {
            if (!swing) {
                return 1.0F;
            }
            return Math.max(boat.getRowingTime(0, PrCemContext.partialTick), boat.getRowingTime(1, PrCemContext.partialTick));
        }
        if (e instanceof AbstractMinecart cart) {
            if (!swing) {
                return 1.0F;
            }
            float x = (float) Mth.lerp(PrCemContext.partialTick, cart.xOld, cart.getX());
            float z = (float) Mth.lerp(PrCemContext.partialTick, cart.zOld, cart.getZ());
            return -(x + z);
        }
        return fallback;
    }

    private static float position(int axis) {
        Entity e = PrCemContext.entity;
        if (e != null) {
            float t = PrCemContext.partialTick;
            return (float) switch (axis) {
                case 0 -> Mth.lerp(t, e.xo, e.getX());
                case 1 -> Mth.lerp(t, e.yo, e.getY());
                default -> Mth.lerp(t, e.zo, e.getZ());
            };
        }
        if (PrCemContext.blockEntity != null) {
            BlockPos p = PrCemContext.blockEntity.getBlockPos();
            return axis == 0 ? p.getX() : axis == 1 ? p.getY() : p.getZ();
        }
        return 0.0F;
    }

    /** A per-entity constant: bit patterns of the UUID (entities) or the position hash (block entities). */
    private static float id() {
        Entity e = PrCemContext.entity;
        if (e != null) {
            UUID uuid = e.getUUID();
            return Float.intBitsToFloat(Long.hashCode(uuid.getLeastSignificantBits()) ^ Long.hashCode(uuid.getMostSignificantBits()));
        }
        if (PrCemContext.blockEntity != null) {
            BlockPos p = PrCemContext.blockEntity.getBlockPos();
            return Float.intBitsToFloat(PrHash.random(p.getX(), p.getY(), p.getZ(), 0));
        }
        return 0.0F;
    }

    private static float dimension() {
        Level level = Minecraft.getInstance().level;
        if (level == null || level.dimension() == Level.OVERWORLD) {
            return 0.0F;
        }
        return level.dimension() == Level.NETHER ? -1.0F : level.dimension() == Level.END ? 1.0F : 0.0F;
    }

    private static float toRad(float degrees) {
        return degrees / 180.0F * (float) Math.PI;
    }
}
