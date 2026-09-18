package net.pryzma.lod.config;

/**
 * Global configuration and runtime options for the native Pryzma LOD subsystem.
 */
public final class LodConfig {
    private static volatile boolean enabled = true;
    private static volatile int lodDistanceChunks = 128;
    private static volatile boolean renderWater = true;
    private static volatile boolean enableSkirts = true;
    private static volatile int nearFieldDiscardChunks = 16;

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static int getLodDistanceChunks() {
        return lodDistanceChunks;
    }

    public static void setLodDistanceChunks(int chunks) {
        lodDistanceChunks = Math.max(32, Math.min(512, chunks));
    }

    public static boolean isRenderWater() {
        return renderWater;
    }

    public static void setRenderWater(boolean value) {
        renderWater = value;
    }

    public static boolean isEnableSkirts() {
        return enableSkirts;
    }

    public static void setEnableSkirts(boolean value) {
        enableSkirts = value;
    }

    public static int getNearFieldDiscardChunks() {
        return nearFieldDiscardChunks;
    }

    public static void setNearFieldDiscardChunks(int chunks) {
        nearFieldDiscardChunks = Math.max(4, Math.min(64, chunks));
    }
}
