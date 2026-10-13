package net.pryzma.lod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.IntUnaryOperator;

import org.junit.jupiter.api.Test;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

/**
 * The column rules of the Farlands LOD scanner: crowns over forest floors, water over its floor, lava as ground, and
 * the limits of every descent. Columns are built bottom-up from runs of block kinds; everything else is air.
 */
class LodColumnScanTest {
    private static final int MIN_Y = -64;
    private static final int NONE = LodColumnScan.NONE;

    /** A column of block kinds that fails the test when asked outside {@code [MIN_Y, top]}. */
    private static final class Column implements IntUnaryOperator {
        private final Int2IntOpenHashMap kinds = new Int2IntOpenHashMap();
        private int top = Integer.MAX_VALUE;
        private int lowestAsked = Integer.MAX_VALUE;

        Column() {
            kinds.defaultReturnValue(LodColumnScan.AIR);
        }

        /** Blocks {@code from..to} (inclusive) are {@code kind}. */
        Column run(int from, int to, int kind) {
            for (int y = from; y <= to; y++) {
                kinds.put(y, kind);
            }
            return this;
        }

        LodColumnScan.Result scan(int topY) {
            top = topY;
            LodColumnScan.Result out = new LodColumnScan.Result();
            LodColumnScan.scan(this, topY, MIN_Y, out);
            return out;
        }

        @Override
        public int applyAsInt(int y) {
            assertTrue(y >= MIN_Y && y <= top, "asked for y " + y + " outside [" + MIN_Y + ", " + top + "]");
            lowestAsked = Math.min(lowestAsked, y);
            return kinds.get(y);
        }
    }

    @Test
    void openGroundIsJustGround() {
        LodColumnScan.Result r = new Column().run(MIN_Y, 70, LodColumnScan.SOLID).scan(70);
        assertEquals(70, r.ground);
        assertEquals(LodColumnScan.SOLID, r.groundKind);
        assertFalse(r.hasCanopy());
        assertFalse(r.hasFluid());
    }

    @Test
    void treeCrownsFloatAboveTheForestFloor() {
        Column column = new Column()
                .run(MIN_Y, 65, LodColumnScan.SOLID)
                .run(66, 71, LodColumnScan.TRUNK)
                .run(72, 75, LodColumnScan.FOLIAGE)
                .run(74, 74, LodColumnScan.AIR); // a hole inside the crown
        LodColumnScan.Result r = column.scan(75);
        assertTrue(r.hasCanopy());
        assertEquals(75, r.canopyTop);
        assertEquals(72, r.canopyBottom, "the lowest foliage block above the ground");
        assertEquals(65, r.ground, "the forest floor below the trunk");
        assertEquals(LodColumnScan.SOLID, r.groundKind);
        assertFalse(r.hasFluid());
        assertEquals(65, column.lowestAsked, "the descent stops at the ground");
    }

    @Test
    void undergrowthBelowATrunkDeepensTheCanopy() {
        LodColumnScan.Result r = new Column()
                .run(MIN_Y, 66, LodColumnScan.SOLID)
                .run(67, 67, LodColumnScan.FOLIAGE)
                .run(68, 69, LodColumnScan.PASSABLE)
                .run(70, 73, LodColumnScan.TRUNK)
                .run(74, 77, LodColumnScan.FOLIAGE)
                .scan(77);
        assertEquals(77, r.canopyTop);
        assertEquals(67, r.canopyBottom, "foliage anywhere above the ground belongs to the canopy");
        assertEquals(66, r.ground);
    }

    @Test
    void aBushOnTheGroundIsACanopyRestingOnIt() {
        LodColumnScan.Result r = new Column().run(MIN_Y, 64, LodColumnScan.SOLID).run(65, 65, LodColumnScan.FOLIAGE).scan(65);
        assertEquals(65, r.canopyTop);
        assertEquals(65, r.canopyBottom);
        assertEquals(64, r.ground);
    }

    @Test
    void aTreeInWaterKeepsCanopyWaterAndFloor() {
        LodColumnScan.Result r = new Column()
                .run(MIN_Y, 55, LodColumnScan.SOLID)
                .run(56, 62, LodColumnScan.WATER)
                .run(63, 66, LodColumnScan.TRUNK)
                .run(67, 70, LodColumnScan.FOLIAGE)
                .scan(70);
        assertEquals(70, r.canopyTop);
        assertEquals(67, r.canopyBottom);
        assertEquals(62, r.fluid);
        assertEquals(55, r.ground);
        assertEquals(LodColumnScan.SOLID, r.groundKind);
    }

    @Test
    void foliageOverLavaHasLavaGround() {
        LodColumnScan.Result r = new Column()
                .run(MIN_Y, 30, LodColumnScan.LAVA)
                .run(31, 40, LodColumnScan.AIR)
                .run(41, 43, LodColumnScan.FOLIAGE)
                .scan(43);
        assertTrue(r.hasCanopy());
        assertEquals(30, r.ground);
        assertEquals(LodColumnScan.LAVA, r.groundKind);
        assertFalse(r.hasFluid(), "lava is opaque ground, not a translucent fluid");
    }

    @Test
    void foliageWithoutGroundInReachIsItselfTheGround() {
        // Leaves over a deep ravine: the nearest ground lies one block beyond the tree depth.
        int top = 100;
        int beyond = top - LodColumnScan.MAX_TREE_DEPTH - 1;
        Column column = new Column().run(MIN_Y, beyond, LodColumnScan.SOLID).run(top - 2, top, LodColumnScan.FOLIAGE);
        LodColumnScan.Result r = column.scan(top);
        assertFalse(r.hasCanopy());
        assertEquals(top, r.ground);
        assertEquals(LodColumnScan.FOLIAGE, r.groundKind);
        assertEquals(top - LodColumnScan.MAX_TREE_DEPTH, column.lowestAsked, "no block below the tree depth is read");

        // One block higher, the ground is found.
        LodColumnScan.Result reached = new Column().run(MIN_Y, beyond + 1, LodColumnScan.SOLID).run(top - 2, top, LodColumnScan.FOLIAGE)
                .scan(top);
        assertTrue(reached.hasCanopy());
        assertEquals(beyond + 1, reached.ground);
    }

    @Test
    void foliageAtTheBottomOfTheWorldStopsThere() {
        LodColumnScan.Result r = new Column().run(MIN_Y, MIN_Y + 2, LodColumnScan.FOLIAGE).scan(MIN_Y + 2);
        assertEquals(MIN_Y + 2, r.ground);
        assertEquals(LodColumnScan.FOLIAGE, r.groundKind);
    }

    @Test
    void waterIsFollowedDownToItsFloorThroughPlantsAndAir() {
        LodColumnScan.Result r = new Column()
                .run(MIN_Y, 40, LodColumnScan.SOLID)
                .run(41, 41, LodColumnScan.PASSABLE)
                .run(42, 42, LodColumnScan.AIR)
                .run(43, 62, LodColumnScan.WATER)
                .scan(62);
        assertEquals(62, r.fluid);
        assertEquals(40, r.ground);
        assertEquals(LodColumnScan.SOLID, r.groundKind);
        assertFalse(r.hasCanopy());
    }

    @Test
    void waterOverAFloorOfLogsOrLeavesKeepsItsKind() {
        assertEquals(LodColumnScan.TRUNK,
                new Column().run(MIN_Y, 50, LodColumnScan.TRUNK).run(51, 60, LodColumnScan.WATER).scan(60).groundKind);
        assertEquals(LodColumnScan.FOLIAGE,
                new Column().run(MIN_Y, 50, LodColumnScan.FOLIAGE).run(51, 60, LodColumnScan.WATER).scan(60).groundKind);
        assertEquals(LodColumnScan.LAVA,
                new Column().run(MIN_Y, 50, LodColumnScan.LAVA).run(51, 60, LodColumnScan.WATER).scan(60).groundKind);
    }

    @Test
    void deepWaterBeyondReachHasNoFloor() {
        int surface = 62;
        Column column = new Column().run(MIN_Y, surface, LodColumnScan.WATER);
        LodColumnScan.Result r = column.scan(surface);
        assertEquals(surface, r.fluid);
        assertFalse(r.hasGround());
        assertEquals(surface - LodColumnScan.MAX_FLUID_DEPTH, column.lowestAsked, "the descent is bounded");
    }

    @Test
    void lavaOnTopIsGround() {
        LodColumnScan.Result r = new Column().run(MIN_Y, 31, LodColumnScan.LAVA).scan(31);
        assertEquals(31, r.ground);
        assertEquals(LodColumnScan.LAVA, r.groundKind);
        assertFalse(r.hasFluid());
    }

    @Test
    void aLogOnTopIsGroundNotATrunk() {
        LodColumnScan.Result r = new Column().run(MIN_Y, 64, LodColumnScan.SOLID).run(65, 66, LodColumnScan.TRUNK).scan(66);
        assertEquals(66, r.ground);
        assertEquals(LodColumnScan.TRUNK, r.groundKind);
        assertFalse(r.hasCanopy());
    }

    @Test
    void columnsWithoutASurfaceAreRejectedAndReset() {
        LodColumnScan.Result out = new LodColumnScan.Result();
        Column tree = new Column().run(MIN_Y, 64, LodColumnScan.SOLID).run(65, 70, LodColumnScan.FOLIAGE);
        tree.top = 70;
        assertTrue(LodColumnScan.scan(tree, 70, MIN_Y, out));
        assertTrue(out.hasCanopy());

        Column air = new Column();
        air.top = 80;
        assertFalse(LodColumnScan.scan(air, 80, MIN_Y, out), "a heightmap pointing at air");
        assertFalse(out.hasCanopy(), "a rejected column leaves no layer behind");
        assertFalse(out.hasFluid());
        assertFalse(out.hasGround());

        Column plant = new Column().run(MIN_Y, 63, LodColumnScan.SOLID).run(64, 64, LodColumnScan.PASSABLE);
        plant.top = 64;
        assertFalse(LodColumnScan.scan(plant, 64, MIN_Y, out), "plants never stop the heightmap");
        assertFalse(LodColumnScan.scan(new Column(), MIN_Y - 1, MIN_Y, out), "an empty column");
        assertEquals(NONE, out.ground);
        assertEquals(NONE, out.groundKind);
    }
}
