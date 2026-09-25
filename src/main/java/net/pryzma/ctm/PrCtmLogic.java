package net.pryzma.ctm;

import net.minecraft.core.Direction;
import net.pryzma.core.PrHash;

/**
 * Pure OptiFine CTM arithmetic: face-local neighbour directions and the tile index tables. Sides
 * are {@code Direction.get3DDataValue()} values (0 down, 1 up, 2 north, 3 south, 4 west, 5 east),
 * which are OptiFine's side numbers.
 */
final class PrCtmLogic {
    private static final Direction D = Direction.DOWN;
    private static final Direction U = Direction.UP;
    private static final Direction N = Direction.NORTH;
    private static final Direction S = Direction.SOUTH;
    private static final Direction W = Direction.WEST;
    private static final Direction E = Direction.EAST;

    /** Per side: left, right, bottom, top as seen when looking at the face. */
    static final Direction[][] SIDES = {
            {W, E, N, S},
            {W, E, S, N},
            {E, W, D, U},
            {W, E, D, U},
            {N, S, D, U},
            {S, N, D, U},
    };

    /** Horizontal method: [pillar axis 0=Y 1=Z 2=X][side] = {left, right}. */
    static final Direction[][][] HORIZONTAL = {
            {{W, E}, {W, E}, {E, W}, {W, E}, {N, S}, {S, N}},
            {{E, W}, {W, E}, {W, E}, {W, E}, {D, U}, {U, D}},
            {{S, N}, {N, S}, {D, U}, {U, D}, {N, S}, {N, S}},
    };

    /** Vertical method: [pillar axis][side] = {bottom, top}. */
    static final Direction[][][] VERTICAL = {
            {{N, S}, {S, N}, {D, U}, {D, U}, {D, U}, {D, U}},
            {{S, N}, {S, N}, {U, D}, {D, U}, {S, N}, {S, N}},
            {{W, E}, {W, E}, {W, E}, {W, E}, {D, U}, {U, D}},
    };

    private PrCtmLogic() {
    }

    /** Tile index from the four side connections (Fast mode, and the start of Fancy). */
    static int baseIndex(boolean l, boolean r, boolean b, boolean t) {
        int mask = (l ? 1 : 0) | (r ? 2 : 0) | (b ? 4 : 0) | (t ? 8 : 0);
        return BASE[mask];
    }

    // mask bits: 1 left, 2 right, 4 bottom, 8 top
    private static final int[] BASE = {
            0,  // none
            3,  // l
            1,  // r
            2,  // l r
            12, // b
            15, // l b
            13, // r b
            14, // l r b
            36, // t
            39, // l t
            37, // r t
            38, // l r t
            24, // b t
            27, // l b t
            25, // r b t
            26, // l r b t
    };

    /**
     * Fancy refinement: {@code g0..g3} are true where the corner is a gap (not connected): g0 right-
     * bottom, g1 left-bottom, g2 right-top, g3 left-top. OptiFine's table, verbatim.
     */
    static int refine(int index, boolean g0, boolean g1, boolean g2, boolean g3) {
        return switch (index) {
            case 13 -> g0 ? 4 : 13;
            case 15 -> g1 ? 5 : 15;
            case 37 -> g2 ? 16 : 37;
            case 39 -> g3 ? 17 : 39;
            case 14 -> g0 && g1 ? 7 : !g0 && g1 ? 31 : g0 ? 29 : 14;
            case 25 -> g0 && g2 ? 6 : g0 ? 30 : g2 ? 28 : 25;
            case 27 -> g3 && g1 ? 19 : g1 ? 41 : g3 ? 43 : 27;
            case 38 -> g3 && g2 ? 18 : g3 ? 40 : g2 ? 42 : 38;
            case 26 -> refineCenter(g0, g1, g2, g3);
            default -> index;
        };
    }

    private static int refineCenter(boolean g0, boolean g1, boolean g2, boolean g3) {
        int mask = (g0 ? 1 : 0) | (g1 ? 2 : 0) | (g2 ? 4 : 0) | (g3 ? 8 : 0);
        return CENTER[mask];
    }

    // mask bits: 1 g0, 2 g1, 4 g2, 8 g3
    private static final int[] CENTER = {
            26, // no gaps
            32, // g0
            33, // g1
            11, // g0 g1
            44, // g2
            10, // g0 g2
            35, // g1 g2
            20, // g0 g1 g2
            45, // g3
            34, // g0 g3
            23, // g1 g3
            8,  // g0 g1 g3
            22, // g2 g3
            21, // g0 g2 g3
            9,  // g1 g2 g3
            46, // all
    };

    /** Horizontal / vertical tile from the two connections along the axis. */
    static int pairIndex(boolean first, boolean second) {
        if (first) {
            return second ? 1 : 2;
        }
        return second ? 0 : 3;
    }

    /** OptiFine side remap for pillar blocks in the {@code faces} filter. */
    static int fixSideByAxis(int side, int axis) {
        return switch (axis) {
            case 1 -> switch (side) {
                case 0 -> 2;
                case 1 -> 3;
                case 2 -> 1;
                case 3 -> 0;
                default -> side;
            };
            case 2 -> switch (side) {
                case 0 -> 4;
                case 1 -> 5;
                case 4 -> 1;
                case 5 -> 0;
                default -> side;
            };
            default -> side;
        };
    }

    /** OptiFine {@code Config.getRandom}: deterministic per position and face. */
    static int random(int x, int y, int z, int face) {
        return PrHash.random(x, y, z, face);
    }

    static int intHash(int x) {
        return PrHash.intHash(x);
    }

    /** Repeat method: tile index for a block position on one side of a width x height pattern. */
    static int repeatIndex(int x, int y, int z, int side, int width, int height) {
        int nx;
        int ny;
        switch (side) {
            case 0 -> {
                nx = x;
                ny = -z - 1;
            }
            case 1 -> {
                nx = x;
                ny = z;
            }
            case 2 -> {
                nx = -x - 1;
                ny = -y;
            }
            case 3 -> {
                nx = x;
                ny = -y;
            }
            case 4 -> {
                nx = z;
                ny = -y;
            }
            case 5 -> {
                nx = -z - 1;
                ny = -y;
            }
            default -> {
                nx = 0;
                ny = 0;
            }
        }
        return Math.floorMod(ny, height) * width + Math.floorMod(nx, width);
    }
}
