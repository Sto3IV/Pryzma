package net.pryzma.world;

import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.pryzma.PryzmaConfig;

/**
 * Weather off and Time (day only / night only) act on the singleplayer world, as in 1.x: rain is
 * cleared when it starts, and in creative the clock is held inside the chosen half of the day.
 */
public final class PrWorldControl {
    private PrWorldControl() {
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (!(event.getServer() instanceof IntegratedServer server)) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (PryzmaConfig.prTime != 0 && server.getDefaultGameType() == GameType.CREATIVE) {
                holdTime(level);
            }
            if (!PryzmaConfig.prWeather && (level.getRainLevel(1.0F) > 0.0F || level.isThundering())) {
                level.setWeatherParameters(6000, 0, false, false);
            }
        }
    }

    private static void holdTime(ServerLevel level) {
        long time = level.getDayTime();
        long timeOfDay = time % 24000L;
        if (PryzmaConfig.prTime == 1) {
            if (timeOfDay <= 1000L) {
                level.setDayTime(time - timeOfDay + 1001L);
            }
            if (timeOfDay >= 11000L) {
                level.setDayTime(time - timeOfDay + 24001L);
            }
        } else if (PryzmaConfig.prTime == 2) {
            if (timeOfDay <= 14000L) {
                level.setDayTime(time - timeOfDay + 14001L);
            }
            if (timeOfDay >= 22000L) {
                level.setDayTime(time - timeOfDay + 24000L + 14001L);
            }
        }
    }
}
