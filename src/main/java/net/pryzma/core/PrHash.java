package net.pryzma.core;

/** OptiFine's deterministic hashes, shared by CTM, colormaps, random entities and expressions. */
public final class PrHash {
    private PrHash() {
    }

    /** OptiFine {@code Config.intHash} (Thomas Wang's integer hash). */
    public static int intHash(int x) {
        x = x ^ 61 ^ x >> 16;
        x += x << 3;
        x ^= x >> 4;
        x *= 668265261;
        return x ^ x >> 15;
    }

    /** OptiFine {@code Config.getRandom(BlockPos, face)}: deterministic per position and face. */
    public static int random(int x, int y, int z, int face) {
        int r = intHash(face + 37);
        r = intHash(r + x);
        r = intHash(r + z);
        return intHash(r + y);
    }
}
