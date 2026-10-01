package net.pryzma.render;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

/**
 * High-performance, zero-allocation memo of fuzzed biome lookups for render-thread callers.
 * Validated by an epoch counter bumped on chunk load/unload or level changes.
 */
public final class PrBiomeCache {
    private static final int SIZE = 4096;
    private static final int MASK = SIZE - 1;
    private static final long[] KEYS = new long[SIZE];
    private static final int[] STAMPS = new int[SIZE];
    private static final Object[] VALUES = new Object[SIZE]; // Holder<Biome>
    private static int epoch = 1;
    private static Level owner;

    private PrBiomeCache() {
    }

    public static int epoch() {
        return epoch;
    }

    /**
     * Bumps the cache epoch, invalidating all memoized entries in O(1).
     * Called on chunk load (ClientLevel.onChunkLoaded) and chunk unload (ClientChunkCache.drop).
     */
    public static void invalidate() {
        if (++epoch == 0) {
            java.util.Arrays.fill(STAMPS, 0);
            epoch = 1;
        }
    }

    /**
     * Drops all stored Holder references and resets the owner level.
     * Called on level disconnect or dimension changes (LevelRenderer.setLevel).
     */
    public static void release() {
        java.util.Arrays.fill(VALUES, null);
        owner = null;
        invalidate();
    }

    @SuppressWarnings("unchecked")
    public static Holder<Biome> biome(Level level, BlockPos pos) {
        if (level != owner) {
            owner = level;
            invalidate();
        }
        long key = pos.asLong();
        int slot = (int) mix(key) & MASK;
        if (STAMPS[slot] == epoch && KEYS[slot] == key) {
            return (Holder<Biome>) VALUES[slot];
        }
        Holder<Biome> biome = level.getBiome(pos);
        KEYS[slot] = key;
        STAMPS[slot] = epoch;
        VALUES[slot] = biome;
        return biome;
    }

    /** Stafford variant 13 64-bit mixer for optimal avalanche and hash distribution */
    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }
}
