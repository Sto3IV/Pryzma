package net.pryzma.util;

/*
 * Ranni: this code is so bad that my eyes are bleeding.
 */

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.pryzma.Config;

/**
 * Helper for the Smooth World feature:
 * <ul>
 *   <li>Server-side mob tick culling for distant, peaceful, non-baby mobs.</li>
 *   <li>Paces client-server synchronization without freezing render threads on multi-core systems.</li>
 * </ul>
 */
public final class SmoothWorldHelper {

    private SmoothWorldHelper() {
    }

    /**
     * Checks if the mob can skip full AI and pathfinding updates this tick.
     */
    public static boolean shouldSkipMobUpdate(Mob mob) {
        if (mob == null || !Config.isSmoothWorld()) {
            return false;
        }
        if (mob.isBaby()) {
            return false;
        }
        if (mob.hurtTime > 0) {
            return false;
        }
        if (mob.tickCount < 20) {
            return false;
        }

        Level level = mob.level();
        if (level == null || level.isClientSide()) {
            return false;
        }

        List<? extends Player> players = level.players();
        if (players == null || players.size() != 1) {
            return false;
        }

        Player player = players.get(0);
        if (player == null) {
            return false;
        }

        double dx = Math.abs(mob.getX() - player.getX());
        double dz = Math.abs(mob.getZ() - player.getZ());
        double distSq = dx * dx + dz * dz;

        int viewDistChunks = Config.getChunkViewDistance();
        if (viewDistChunks <= 0) {
            viewDistChunks = 12;
        }
        double threshold = viewDistChunks * 16.0;
        return distSq > threshold * threshold;
    }

    /**
     * Minimal tick update for culled mobs: increments despawn timer so distant mobs
     * don't accumulate forever.
     */
    public static void onMobUpdateMinimal(Mob mob) {
        if (mob == null) {
            return;
        }
        // In LivingEntity: noActionTime tracks ticks without player interaction
        mob.setNoActionTime(mob.getNoActionTime() + 1);
        if (mob instanceof Monster) {
            float magic = mob.getLightLevelDependentMagicValue();
            if (magic > 0.5f) {
                mob.setNoActionTime(mob.getNoActionTime() + 2);
            }
        }
    }

    /**
     * Modernized tick synchronization for local singleplayer server.
     * Keeps server thread at standard priority without sleeping the client render thread.
     */
    public static void waitForServerThread(GameRenderer gameRenderer, Minecraft minecraft) {
        if (gameRenderer == null || minecraft == null || !Config.isSmoothWorld()) {
            return;
        }
        if (minecraft.isLocalServer() && minecraft.getSingleplayerServer() != null) {
            Thread serverThread = minecraft.getSingleplayerServer().getRunningThread();
            if (serverThread != null && serverThread.getPriority() != Thread.NORM_PRIORITY) {
                serverThread.setPriority(Thread.NORM_PRIORITY);
            }
        }
    }
}
