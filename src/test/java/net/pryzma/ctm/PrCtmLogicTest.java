package net.pryzma.ctm;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;

import org.junit.jupiter.api.Test;

import net.minecraft.core.Direction;

/**
 * PrCtmLogic against OptiFine. Every {@code of*} member and the {@code BlockDir} tables below are
 * copied verbatim from the decompiled ConnectedTextures / Config / BlockDir by
 * scratchpad/gen_ctm_test.py, with neighbour tests reduced to the direction they probe.
 */
class PrCtmLogicTest {
    // ------------------------------------------------------------------ OptiFine reference

    enum BlockDir {
        DOWN(Direction.DOWN),
        UP(Direction.UP),
        NORTH(Direction.NORTH),
        SOUTH(Direction.SOUTH),
        WEST(Direction.WEST),
        EAST(Direction.EAST),
        NORTH_WEST(Direction.NORTH, Direction.WEST),
        NORTH_EAST(Direction.NORTH, Direction.EAST),
        SOUTH_WEST(Direction.SOUTH, Direction.WEST),
        SOUTH_EAST(Direction.SOUTH, Direction.EAST),
        DOWN_NORTH(Direction.DOWN, Direction.NORTH),
        DOWN_SOUTH(Direction.DOWN, Direction.SOUTH),
        UP_NORTH(Direction.UP, Direction.NORTH),
        UP_SOUTH(Direction.UP, Direction.SOUTH),
        DOWN_WEST(Direction.DOWN, Direction.WEST),
        DOWN_EAST(Direction.DOWN, Direction.EAST),
        UP_WEST(Direction.UP, Direction.WEST),
        UP_EAST(Direction.UP, Direction.EAST);

        private final Direction facing1;
        private final Direction facing2;

        BlockDir(Direction facing1) {
            this(facing1, null);
        }

        BlockDir(Direction facing1, Direction facing2) {
            this.facing1 = facing1;
            this.facing2 = facing2;
        }

        int[] step() {
            int x = facing1.getStepX() + (facing2 == null ? 0 : facing2.getStepX());
            int y = facing1.getStepY() + (facing2 == null ? 0 : facing2.getStepY());
            int z = facing1.getStepZ() + (facing2 == null ? 0 : facing2.getStepZ());
            return new int[] {x, y, z};
        }
    }

    static final BlockDir[] SIDES_Y_NEG_DOWN = new BlockDir[]{BlockDir.WEST, BlockDir.EAST, BlockDir.NORTH, BlockDir.SOUTH};
    static final BlockDir[] SIDES_Y_POS_UP = new BlockDir[]{BlockDir.WEST, BlockDir.EAST, BlockDir.SOUTH, BlockDir.NORTH};
    static final BlockDir[] SIDES_Z_NEG_NORTH = new BlockDir[]{BlockDir.EAST, BlockDir.WEST, BlockDir.DOWN, BlockDir.UP};
    static final BlockDir[] SIDES_Z_POS_SOUTH = new BlockDir[]{BlockDir.WEST, BlockDir.EAST, BlockDir.DOWN, BlockDir.UP};
    static final BlockDir[] SIDES_X_NEG_WEST = new BlockDir[]{BlockDir.NORTH, BlockDir.SOUTH, BlockDir.DOWN, BlockDir.UP};
    static final BlockDir[] SIDES_X_POS_EAST = new BlockDir[]{BlockDir.SOUTH, BlockDir.NORTH, BlockDir.DOWN, BlockDir.UP};
    static final BlockDir[] EDGES_Y_NEG_DOWN = new BlockDir[]{BlockDir.NORTH_EAST, BlockDir.NORTH_WEST, BlockDir.SOUTH_EAST, BlockDir.SOUTH_WEST};
    static final BlockDir[] EDGES_Y_POS_UP = new BlockDir[]{BlockDir.SOUTH_EAST, BlockDir.SOUTH_WEST, BlockDir.NORTH_EAST, BlockDir.NORTH_WEST};
    static final BlockDir[] EDGES_Z_NEG_NORTH = new BlockDir[]{BlockDir.DOWN_WEST, BlockDir.DOWN_EAST, BlockDir.UP_WEST, BlockDir.UP_EAST};
    static final BlockDir[] EDGES_Z_POS_SOUTH = new BlockDir[]{BlockDir.DOWN_EAST, BlockDir.DOWN_WEST, BlockDir.UP_EAST, BlockDir.UP_WEST};
    static final BlockDir[] EDGES_X_NEG_WEST = new BlockDir[]{BlockDir.DOWN_SOUTH, BlockDir.DOWN_NORTH, BlockDir.UP_SOUTH, BlockDir.UP_NORTH};
    static final BlockDir[] EDGES_X_POS_EAST = new BlockDir[]{BlockDir.DOWN_NORTH, BlockDir.DOWN_SOUTH, BlockDir.UP_NORTH, BlockDir.UP_SOUTH};

    static final BlockDir[][] OF_SIDES = {SIDES_Y_NEG_DOWN, SIDES_Y_POS_UP, SIDES_Z_NEG_NORTH, SIDES_Z_POS_SOUTH,
            SIDES_X_NEG_WEST, SIDES_X_POS_EAST};
    static final BlockDir[][] OF_EDGES = {EDGES_Y_NEG_DOWN, EDGES_Y_POS_UP, EDGES_Z_NEG_NORTH, EDGES_Z_POS_SOUTH,
            EDGES_X_NEG_WEST, EDGES_X_POS_EAST};

    /** getConnectedTextureCtmIndex: first the side connections, then (Fancy) the corner gaps. */
    static int ofCtmIndex(boolean[] sides, boolean[] gaps, boolean fancy) {
        boolean[] borders = sides;
        int index = 0;
        if (borders[0] & !borders[1] & !borders[2] & !borders[3]) {
           index = 3;
        } else if (!borders[0] & borders[1] & !borders[2] & !borders[3]) {
           index = 1;
        } else if (!borders[0] & !borders[1] & borders[2] & !borders[3]) {
           index = 12;
        } else if (!borders[0] & !borders[1] & !borders[2] & borders[3]) {
           index = 36;
        } else if (borders[0] & borders[1] & !borders[2] & !borders[3]) {
           index = 2;
        } else if (!borders[0] & !borders[1] & borders[2] & borders[3]) {
           index = 24;
        } else if (borders[0] & !borders[1] & borders[2] & !borders[3]) {
           index = 15;
        } else if (borders[0] & !borders[1] & !borders[2] & borders[3]) {
           index = 39;
        } else if (!borders[0] & borders[1] & borders[2] & !borders[3]) {
           index = 13;
        } else if (!borders[0] & borders[1] & !borders[2] & borders[3]) {
           index = 37;
        } else if (!borders[0] & borders[1] & borders[2] & borders[3]) {
           index = 25;
        } else if (borders[0] & !borders[1] & borders[2] & borders[3]) {
           index = 27;
        } else if (borders[0] & borders[1] & !borders[2] & borders[3]) {
           index = 38;
        } else if (borders[0] & borders[1] & borders[2] & !borders[3]) {
           index = 14;
        } else if (borders[0] & borders[1] & borders[2] & borders[3]) {
           index = 26;
        }

        if (index == 0 || !fancy) {
            return index;
        }
        borders = gaps;
        if (index == 13 && borders[0]) {
           index = 4;
        } else if (index == 15 && borders[1]) {
           index = 5;
        } else if (index == 37 && borders[2]) {
           index = 16;
        } else if (index == 39 && borders[3]) {
           index = 17;
        } else if (index == 14 && borders[0] && borders[1]) {
           index = 7;
        } else if (index == 25 && borders[0] && borders[2]) {
           index = 6;
        } else if (index == 27 && borders[3] && borders[1]) {
           index = 19;
        } else if (index == 38 && borders[3] && borders[2]) {
           index = 18;
        } else if (index == 14 && !borders[0] && borders[1]) {
           index = 31;
        } else if (index == 25 && borders[0] && !borders[2]) {
           index = 30;
        } else if (index == 27 && !borders[3] && borders[1]) {
           index = 41;
        } else if (index == 38 && borders[3] && !borders[2]) {
           index = 40;
        } else if (index == 14 && borders[0] && !borders[1]) {
           index = 29;
        } else if (index == 25 && !borders[0] && borders[2]) {
           index = 28;
        } else if (index == 27 && borders[3] && !borders[1]) {
           index = 43;
        } else if (index == 38 && !borders[3] && borders[2]) {
           index = 42;
        } else if (index == 26 && borders[0] && borders[1] && borders[2] && borders[3]) {
           index = 46;
        } else if (index == 26 && !borders[0] && borders[1] && borders[2] && borders[3]) {
           index = 9;
        } else if (index == 26 && borders[0] && !borders[1] && borders[2] && borders[3]) {
           index = 21;
        } else if (index == 26 && borders[0] && borders[1] && !borders[2] && borders[3]) {
           index = 8;
        } else if (index == 26 && borders[0] && borders[1] && borders[2] && !borders[3]) {
           index = 20;
        } else if (index == 26 && borders[0] && borders[1] && !borders[2] && !borders[3]) {
           index = 11;
        } else if (index == 26 && !borders[0] && !borders[1] && borders[2] && borders[3]) {
           index = 22;
        } else if (index == 26 && !borders[0] && borders[1] && !borders[2] && borders[3]) {
           index = 23;
        } else if (index == 26 && borders[0] && !borders[1] && borders[2] && !borders[3]) {
           index = 10;
        } else if (index == 26 && borders[0] && !borders[1] && !borders[2] && borders[3]) {
           index = 34;
        } else if (index == 26 && !borders[0] && borders[1] && borders[2] && !borders[3]) {
           index = 35;
        } else if (index == 26 && borders[0] && !borders[1] && !borders[2] && !borders[3]) {
           index = 32;
        } else if (index == 26 && !borders[0] && borders[1] && !borders[2] && !borders[3]) {
           index = 33;
        } else if (index == 26 && !borders[0] && !borders[1] && borders[2] && !borders[3]) {
           index = 44;
        } else if (index == 26 && !borders[0] && !borders[1] && !borders[2] && borders[3]) {
           index = 45;
        }

        return index;
    }

    /** getConnectedTextureHorizontal: the two neighbours it tests, {left, right}. */
    static Direction[] ofHorizontal(int vertAxis, int side) {
        Direction left = null;
        Direction right = null;
        label46:
        switch (vertAxis) {
           case 0:
              switch (side) {
                 case 0:
                    left = Direction.WEST;
                    right = Direction.EAST;
                    break label46;
                 case 1:
                    left = Direction.WEST;
                    right = Direction.EAST;
                    break label46;
                 case 2:
                    left = Direction.EAST;
                    right = Direction.WEST;
                    break label46;
                 case 3:
                    left = Direction.WEST;
                    right = Direction.EAST;
                    break label46;
                 case 4:
                    left = Direction.NORTH;
                    right = Direction.SOUTH;
                    break label46;
                 case 5:
                    left = Direction.SOUTH;
                    right = Direction.NORTH;
                 default:
                    break label46;
              }
           case 1:
              switch (side) {
                 case 0:
                    left = Direction.EAST;
                    right = Direction.WEST;
                    break label46;
                 case 1:
                    left = Direction.WEST;
                    right = Direction.EAST;
                    break label46;
                 case 2:
                    left = Direction.WEST;
                    right = Direction.EAST;
                    break label46;
                 case 3:
                    left = Direction.WEST;
                    right = Direction.EAST;
                    break label46;
                 case 4:
                    left = Direction.DOWN;
                    right = Direction.UP;
                    break label46;
                 case 5:
                    left = Direction.UP;
                    right = Direction.DOWN;
                 default:
                    break label46;
              }
           case 2:
              switch (side) {
                 case 0:
                    left = Direction.SOUTH;
                    right = Direction.NORTH;
                    break;
                 case 1:
                    left = Direction.NORTH;
                    right = Direction.SOUTH;
                    break;
                 case 2:
                    left = Direction.DOWN;
                    right = Direction.UP;
                    break;
                 case 3:
                    left = Direction.UP;
                    right = Direction.DOWN;
                    break;
                 case 4:
                    left = Direction.NORTH;
                    right = Direction.SOUTH;
                    break;
                 case 5:
                    left = Direction.NORTH;
                    right = Direction.SOUTH;
              }
        }
        return new Direction[] {left, right};
    }

    /** getConnectedTextureVertical: the two neighbours it tests, {bottom, top}. */
    static Direction[] ofVertical(int vertAxis, int side) {
        Direction bottom = null;
        Direction top = null;
        switch (vertAxis) {
           case 0:
              if (side == 1) {
                 bottom = Direction.SOUTH;
                 top = Direction.NORTH;
              } else if (side == 0) {
                 bottom = Direction.NORTH;
                 top = Direction.SOUTH;
              } else {
                 bottom = Direction.DOWN;
                 top = Direction.UP;
              }
              break;
           case 1:
              if (side == 3) {
                 bottom = Direction.DOWN;
                 top = Direction.UP;
              } else if (side == 2) {
                 bottom = Direction.UP;
                 top = Direction.DOWN;
              } else {
                 bottom = Direction.SOUTH;
                 top = Direction.NORTH;
              }
              break;
           case 2:
              if (side == 5) {
                 bottom = Direction.UP;
                 top = Direction.DOWN;
              } else if (side == 4) {
                 bottom = Direction.DOWN;
                 top = Direction.UP;
              } else {
                 bottom = Direction.WEST;
                 top = Direction.EAST;
              }
        }
        return new Direction[] {bottom, top};
    }

    /** getConnectedTextureRepeat for a width x height tile set, as a tile index. */
    static int ofRepeat(int x, int y, int z, int side, int width, int height) {
        int nx = 0;
        int ny = 0;
        switch (side) {
           case 0:
              nx = x;
              ny = -z - 1;
              break;
           case 1:
              nx = x;
              ny = z;
              break;
           case 2:
              nx = -x - 1;
              ny = -y;
              break;
           case 3:
              nx = x;
              ny = -y;
              break;
           case 4:
              nx = z;
              ny = -y;
              break;
           case 5:
              nx = -z - 1;
              ny = -y;
        }

        nx %= width;
        ny %= height;
        if (nx < 0) {
           nx += width;
        }

        if (ny < 0) {
           ny += height;
        }

        int index = ny * width + nx;
        return index;
    }

    static int ofFixSideByAxis(int side, int vertAxis) {
       switch (vertAxis) {
          case 0:
             return side;
          case 1:
             switch (side) {
                case 0:
                   return 2;
                case 1:
                   return 3;
                case 2:
                   return 1;
                case 3:
                   return 0;
                default:
                   return side;
             }
          case 2:
             switch (side) {
                case 0:
                   return 4;
                case 1:
                   return 5;
                case 2:
                case 3:
                default:
                   return side;
                case 4:
                   return 1;
                case 5:
                   return 0;
             }
          default:
             return side;
       }
    }

    static int ofIntHash(int x) {
       x = x ^ 61 ^ x >> 16;
       x += x << 3;
       x ^= x >> 4;
       x *= 668265261;
       return x ^ x >> 15;
    }

    static int ofGetRandom(int x, int y, int z, int face) {
       int rand = ofIntHash(face + 37);
       rand = ofIntHash(rand + x);
       rand = ofIntHash(rand + z);
       return ofIntHash(rand + y);
    }

    // ------------------------------------------------------------------ tests

    @Test
    void ctmIndexMatchesOptifineForEveryNeighbourhood() {
        for (int s = 0; s < 16; s++) {
            boolean[] sides = bits(s);
            int base = PrCtmLogic.baseIndex(sides[0], sides[1], sides[2], sides[3]);
            assertEquals(ofCtmIndex(sides, new boolean[4], false), base, "fast, sides " + s);
            for (int g = 0; g < 16; g++) {
                boolean[] gaps = bits(g);
                int fancy = base == 0 ? 0 : PrCtmLogic.refine(base, gaps[0], gaps[1], gaps[2], gaps[3]);
                assertEquals(ofCtmIndex(sides, gaps, true), fancy, "fancy, sides " + s + " gaps " + g);
            }
        }
    }

    @Test
    void sideAndCornerDirectionsMatchOptifine() {
        for (int side = 0; side < 6; side++) {
            Direction[] d = PrCtmLogic.SIDES[side];
            for (int i = 0; i < 4; i++) {
                assertArrayEquals(OF_SIDES[side][i].step(), step(d[i]), "side " + side + " border " + i);
            }
            // Corner i is right+bottom, left+bottom, right+top, left+top: the Fancy corner pass
            // and the overlay edges both use OptiFine's EDGES order.
            Direction[][] corners = {{d[1], d[2]}, {d[0], d[2]}, {d[1], d[3]}, {d[0], d[3]}};
            for (int i = 0; i < 4; i++) {
                assertArrayEquals(OF_EDGES[side][i].step(), step(corners[i][0], corners[i][1]), "side " + side + " corner " + i);
            }
        }
    }

    @Test
    void horizontalAndVerticalNeighboursMatchOptifine() {
        for (int axis = 0; axis < 3; axis++) {
            for (int side = 0; side < 6; side++) {
                assertArrayEquals(ofHorizontal(axis, side), PrCtmLogic.HORIZONTAL[axis][side], "horizontal " + axis + "/" + side);
                assertArrayEquals(ofVertical(axis, side), PrCtmLogic.VERTICAL[axis][side], "vertical " + axis + "/" + side);
            }
        }
    }

    @Test
    void pairIndexMatchesOptifine() {
        // OptiFine: first ? (second ? 1 : 2) : (second ? 0 : 3), for both horizontal and vertical.
        assertEquals(3, PrCtmLogic.pairIndex(false, false));
        assertEquals(2, PrCtmLogic.pairIndex(true, false));
        assertEquals(1, PrCtmLogic.pairIndex(true, true));
        assertEquals(0, PrCtmLogic.pairIndex(false, true));
    }

    @Test
    void repeatIndexMatchesOptifine() {
        for (int w = 1; w <= 5; w++) {
            for (int h = 1; h <= 5; h++) {
                for (int x = -9; x <= 9; x++) {
                    for (int y = -70; y <= 70; y += 7) {
                        for (int z = -9; z <= 9; z++) {
                            for (int side = 0; side < 6; side++) {
                                assertEquals(ofRepeat(x, y, z, side, w, h), PrCtmLogic.repeatIndex(x, y, z, side, w, h));
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void randomMatchesOptifine() {
        Random r = new Random(7);
        for (int i = 0; i < 20000; i++) {
            int x = r.nextInt(60_000_001) - 30_000_000;
            int y = r.nextInt(4064) - 2032;
            int z = r.nextInt(60_000_001) - 30_000_000;
            int face = r.nextInt(6);
            assertEquals(ofGetRandom(x, y, z, face), PrCtmLogic.random(x, y, z, face));
            int v = r.nextInt();
            assertEquals(ofIntHash(v), PrCtmLogic.intHash(v));
        }
    }

    @Test
    void fixSideByAxisMatchesOptifine() {
        for (int axis = 0; axis < 3; axis++) {
            for (int side = 0; side < 6; side++) {
                assertEquals(ofFixSideByAxis(side, axis), PrCtmLogic.fixSideByAxis(side, axis), axis + "/" + side);
            }
        }
    }

    private static boolean[] bits(int mask) {
        return new boolean[] {(mask & 1) != 0, (mask & 2) != 0, (mask & 4) != 0, (mask & 8) != 0};
    }

    private static int[] step(Direction... dirs) {
        int[] s = new int[3];
        for (Direction d : dirs) {
            s[0] += d.getStepX();
            s[1] += d.getStepY();
            s[2] += d.getStepZ();
        }
        return s;
    }
}
