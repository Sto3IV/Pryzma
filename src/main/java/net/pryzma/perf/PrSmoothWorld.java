package net.pryzma.perf;

import java.util.List;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.pryzma.PryzmaConfig;

/**
 * Smooth World mob pacing for the integrated server (Pryzma 1.x {@code SmoothWorldHelper}).
 *
 * <p>With exactly one player in the level, calm passive mobs more than {@link #RADIUS} blocks away
 * on either horizontal axis run their AI and pathfinding ({@code Mob.serverAiStep}) every
 * {@link #INTERVAL}th tick instead of every tick. Physics, ageing, despawn checks and damage keep
 * running every tick; only decision making slows down, and a mob that is hurt, targeted, bred,
 * leashed, ridden, tamed or a baby is never throttled.
 *
 * <p>Villagers and bees are excluded on purpose: their AI drives trading, breeding, iron and honey
 * farms, which must not slow down while the player stands elsewhere.
 */
public final class PrSmoothWorld {
    /** Horizontal distance (per axis) beyond which pacing applies. */
    public static final double RADIUS = 64.0;
    /** A paced mob thinks on one tick out of this many, staggered by entity id. */
    public static final int INTERVAL = 4;

    private static long skipped;
    private static long ran;

    private PrSmoothWorld() {
    }

    /** Whether {@code mob} skips its AI step this tick. Server thread only. */
    public static boolean skipAi(Mob mob) {
        if (!PryzmaConfig.prSmoothWorld || !(mob.level() instanceof ServerLevel level)) {
            return false;
        }
        List<ServerPlayer> players = level.players();
        if (players.size() != 1 || !isCalm(mob)) {
            return false;
        }
        ServerPlayer player = players.get(0);
        boolean skip = skipsTick(mob.getX() - player.getX(), mob.getZ() - player.getZ(), mob.tickCount, mob.getId());
        if (skip) {
            skipped++;
        } else {
            ran++;
        }
        return skip;
    }

    /** Pure pacing rule: outside the radius, only every {@link #INTERVAL}th tick (by id) runs. */
    static boolean skipsTick(double dx, double dz, int tickCount, int id) {
        return (Math.abs(dx) > RADIUS || Math.abs(dz) > RADIUS) && Math.floorMod(tickCount + id, INTERVAL) != 0;
    }

    /** Passive, adult, unhurt, untamed and otherwise unengaged: nothing depends on its reaction time. */
    static boolean isCalm(Mob mob) {
        if (!(mob instanceof Animal || mob instanceof AmbientCreature || mob instanceof WaterAnimal)
                || mob instanceof Enemy || mob instanceof Bee) {
            return false;
        }
        if (mob.tickCount < 20 || mob.isBaby() || mob.hurtTime > 0 || mob.getHealth() < mob.getMaxHealth()) {
            return false;
        }
        if (mob.getTarget() != null || mob.isLeashed() || mob.isPassenger() || mob.isVehicle()) {
            return false;
        }
        if (mob instanceof OwnableEntity owned && owned.getOwnerUUID() != null) {
            return false;
        }
        return !(mob instanceof Animal animal && animal.isInLove());
    }

    /** AI steps skipped and run by paced mobs since start; read by the development self-test. */
    public static long skippedSteps() {
        return skipped;
    }

    public static long pacedSteps() {
        return ran;
    }
}
