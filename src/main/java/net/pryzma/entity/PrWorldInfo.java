package net.pryzma.entity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/** World conditions of OptiFine rules: moon phase, time of day and weather. */
public interface PrWorldInfo {
    enum Weather {
        CLEAR, RAIN, THUNDER
    }

    int moonPhase();

    /** Day time modulo 24000. */
    int dayTime();

    Weather weather();

    /** The client world; a world-less client (menus) reports phase 0, noon and clear skies. */
    static PrWorldInfo client() {
        return ClientWorld.INSTANCE;
    }

    final class ClientWorld implements PrWorldInfo {
        static final ClientWorld INSTANCE = new ClientWorld();

        private ClientWorld() {
        }

        @Override
        public int moonPhase() {
            ClientLevel level = Minecraft.getInstance().level;
            return level == null ? 0 : level.getMoonPhase();
        }

        @Override
        public int dayTime() {
            ClientLevel level = Minecraft.getInstance().level;
            return level == null ? 6000 : (int) (level.getDayTime() % 24000L);
        }

        @Override
        public Weather weather() {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) {
                return Weather.CLEAR;
            }
            // OptiFine Weather.getWeather: thunder, then rain, above half strength.
            if (level.getThunderLevel(0.0F) > 0.5F) {
                return Weather.THUNDER;
            }
            return level.getRainLevel(0.0F) > 0.5F ? Weather.RAIN : Weather.CLEAR;
        }
    }
}
