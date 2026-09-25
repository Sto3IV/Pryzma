package net.pryzma.core.expr;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.pryzma.Pryzma;

/**
 * The world state that OptiFine's built-in expression functions read: {@code time},
 * {@code day_time}, {@code day_count}, the clock of {@code smooth()} and the frame counter that
 * throttles {@code print()}. Tests supply their own; the game uses {@link #minecraft()}.
 */
public interface PrExprEnv {
    /** Game time modulo 720720 plus the partial tick. */
    float time();

    /** Day time modulo 24000 plus the partial tick. */
    float dayTime();

    /** Whole days since the world started. */
    float dayCount();

    /** Wall clock in milliseconds, for {@code smooth()}. */
    long millis();

    /** Frames rendered so far, for {@code print()} throttling. */
    int frameCounter();

    void print(String message);

    static PrExprEnv minecraft() {
        return MinecraftEnv.INSTANCE;
    }

    /** Values of the running client. Safe to call without a world: everything is then zero. */
    final class MinecraftEnv implements PrExprEnv {
        static final MinecraftEnv INSTANCE = new MinecraftEnv();
        private static int frames;

        private MinecraftEnv() {
        }

        /** Advanced once per rendered frame by the frame hook. */
        public static void onFrame() {
            frames++;
        }

        private static float partialTick() {
            return Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        }

        @Override
        public float time() {
            ClientLevel level = Minecraft.getInstance().level;
            return level == null ? 0.0F : (float) (level.getGameTime() % 720720L) + partialTick();
        }

        @Override
        public float dayTime() {
            ClientLevel level = Minecraft.getInstance().level;
            return level == null ? 0.0F : (float) (level.getDayTime() % 24000L) + partialTick();
        }

        @Override
        public float dayCount() {
            ClientLevel level = Minecraft.getInstance().level;
            return level == null ? 0.0F : (float) (level.getDayTime() / 24000L);
        }

        @Override
        public long millis() {
            return System.currentTimeMillis();
        }

        @Override
        public int frameCounter() {
            return frames;
        }

        @Override
        public void print(String message) {
            Pryzma.LOGGER.info(message);
        }
    }
}
