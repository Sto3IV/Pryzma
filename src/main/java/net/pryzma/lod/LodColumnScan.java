package net.pryzma.lod;

import java.util.function.IntUnaryOperator;

/**
 * Farlands LOD column scan: the layers of one column of blocks, read top down from the {@code MOTION_BLOCKING}
 * heightmap top. Pure: it works on block kinds ({@link #AIR} .. {@link #LAVA}) that the caller derives from block
 * states, so the rules are testable without game state.
 *
 * <ul>
 * <li>A column topped by foliage is a tree. The scan descends through foliage, trunks, air and plants to the ground
 * below and reports the foliage between as the canopy: its highest block and its lowest one above the ground. A tree
 * standing in water or on lava keeps both. If no ground lies within {@value #MAX_TREE_DEPTH} blocks, the foliage
 * itself is the ground (a canopy over a cave or the void).</li>
 * <li>A translucent fluid (water and anything without lava's opacity) is a fluid surface; the scan follows it down
 * to its floor, at most {@value #MAX_FLUID_DEPTH} blocks. Air pockets and plants below the surface are skipped.</li>
 * <li>Lava is opaque: it is ground, not a fluid surface.</li>
 * <li>Trunks are only passed through under a canopy; a log on top of a column is ground (a stump, a log house).</li>
 * </ul>
 */
public final class LodColumnScan {
    /** Air. */
    public static final int AIR = 0;
    /** A block without collision and without fluid: plants, snow layers, torches. */
    public static final int PASSABLE = 1;
    /** Leaves. */
    public static final int FOLIAGE = 2;
    /** A log: a trunk when it stands under a canopy. */
    public static final int TRUNK = 3;
    /** Any other block that blocks motion. */
    public static final int SOLID = 4;
    /** A translucent fluid surface: water, and blocks without collision that hold water (kelp, seagrass). */
    public static final int WATER = 5;
    /** Lava, and blocks without collision that hold it. */
    public static final int LAVA = 6;

    /** Block y of a layer that is absent. */
    public static final int NONE = Integer.MIN_VALUE;
    /** Deepest a tree's canopy may lie above its ground: taller than any vanilla tree. */
    public static final int MAX_TREE_DEPTH = 48;
    /** Deepest fluid column searched for the floor below it. */
    public static final int MAX_FLUID_DEPTH = 64;

    private LodColumnScan() {
    }

    /** The layers of one column as block y coordinates (the blocks, not their top faces). Reused between columns. */
    public static final class Result {
        /** Highest foliage block of a canopy, {@link #NONE} without one. */
        public int canopyTop;
        /** Lowest foliage block of the canopy above the ground. */
        public int canopyBottom;
        /** Top block of the translucent fluid, {@link #NONE} without one. */
        public int fluid;
        /** Top block of the ground: the land, the floor below the fluid, or the forest floor; {@link #NONE} if not reached. */
        public int ground;
        /** Kind of the {@link #ground} block: {@link #SOLID}, {@link #TRUNK}, {@link #FOLIAGE} or {@link #LAVA}. */
        public int groundKind;

        public Result() {
            reset();
        }

        void reset() {
            canopyTop = NONE;
            canopyBottom = NONE;
            fluid = NONE;
            ground = NONE;
            groundKind = NONE;
        }

        public boolean hasCanopy() {
            return canopyTop != NONE;
        }

        public boolean hasFluid() {
            return fluid != NONE;
        }

        public boolean hasGround() {
            return ground != NONE;
        }
    }

    /**
     * Scans the column whose {@code MOTION_BLOCKING} top block is at {@code topY}.
     *
     * @param kindAt kind of the block at a y of this column; only asked for y in {@code [minY, topY]}
     * @param minY   lowest block of the level
     * @return false for a column without a surface (a heightmap pointing at air or below the level); {@code out} is
     *         then reset
     */
    public static boolean scan(IntUnaryOperator kindAt, int topY, int minY, Result out) {
        out.reset();
        if (topY < minY) {
            return false;
        }
        int kind = kindAt.applyAsInt(topY);
        if (kind == AIR || kind == PASSABLE) {
            return false;
        }
        int y = topY;
        if (kind == FOLIAGE) {
            int lowestFoliage = topY;
            int limit = Math.max(minY, topY - MAX_TREE_DEPTH);
            int groundY = NONE;
            int groundKind = NONE;
            for (int below = topY - 1; below >= limit; below--) {
                int k = kindAt.applyAsInt(below);
                if (k == FOLIAGE) {
                    lowestFoliage = below;
                } else if (k == SOLID || k == WATER || k == LAVA) {
                    groundY = below;
                    groundKind = k;
                    break;
                }
                // Trunks, air and plants: still inside the tree.
            }
            if (groundY == NONE) {
                // No ground in reach: the foliage is the surface, as the heightmap says.
                out.ground = topY;
                out.groundKind = FOLIAGE;
                return true;
            }
            out.canopyTop = topY;
            out.canopyBottom = lowestFoliage;
            y = groundY;
            kind = groundKind;
        }
        if (kind == WATER) {
            out.fluid = y;
            int limit = Math.max(minY, y - MAX_FLUID_DEPTH);
            for (int below = y - 1; below >= limit; below--) {
                int k = kindAt.applyAsInt(below);
                if (k != WATER && k != AIR && k != PASSABLE) {
                    out.ground = below;
                    out.groundKind = k == TRUNK || k == FOLIAGE ? k : k == LAVA ? LAVA : SOLID;
                    break;
                }
            }
            return true;
        }
        out.ground = y;
        out.groundKind = kind == LAVA ? LAVA : kind == TRUNK ? TRUNK : kind == FOLIAGE ? FOLIAGE : SOLID;
        return true;
    }
}
