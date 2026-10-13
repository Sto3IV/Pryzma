package net.pryzma.lod;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Farlands LOD mesher: turns the surface columns of one macro-sector ({@value LodDataStore#SECTOR_BLOCKS} x
 * {@value LodDataStore#SECTOR_BLOCKS} blocks) into a compact quad mesh. Pure Java without GL or game state; it
 * runs on the LOD worker threads.
 *
 * <h2>Detail levels</h2>
 * Level {@code L} meshes cells of {@code 2^L x 2^L} columns (L = 0..{@value #MAX_LEVEL}); a cell never crosses a
 * chunk border. Each cell has up to three layers ({@link LodChunkData}):
 * <ul>
 * <li><b>Ground</b>: the highest ground column of the cell, so silhouettes survive the reduction. Isolated trees no
 * longer raise it: their foliage is a canopy, not ground.</li>
 * <li><b>Water</b>: the highest water surface of the cell, where it lies above the cell's ground. Its floor is the
 * ground layer below it, lit as deep as it lies, and shows through the translucent surface.</li>
 * <li><b>Canopy</b>: at level 0 a column's own foliage. Coarser cells are canopy where foliage covers at least half
 * of their columns; the cell then takes one of its canopy columns, picked by a hash of the cell's world position, and
 * moves it up or down by up to {@code max(1, cell / 4)} blocks by another hash. A forest keeps the height
 * distribution of its real crowns and its roughness instead of collapsing into one flat table at its tallest tree,
 * and the same cell always looks the same.</li>
 * </ul>
 *
 * <h2>Faces</h2>
 * <ul>
 * <li>Tops of every layer: adjacent cells with the same height, colour, light and material merge greedily into
 * rectangles.</li>
 * <li>Ground skirts: every cell edge where the neighbour's ground is lower gets a wall from the cell's ground down to
 * the neighbour's; consecutive equal walls along an edge merge. A neighbour without data gets a
 * {@value #VOID_SKIRT}-block skirt. Across a sector border the neighbour height is the lowest full-resolution ground
 * column along the cell's edge; any coarser level of the neighbour sector is at least that high (cells take maxima),
 * so the walls of two sectors meshed at different levels always overlap and the border cannot crack.</li>
 * <li>Canopy walls close the sides of each slab of the crowns' thickness (clamped to
 * {@value #MIN_CANOPY_DEPTH}..{@value #MAX_CANOPY_DEPTH} blocks, from 1 at level 0) wherever the neighbouring slab
 * does not cover them: above a lower neighbouring crown and below a higher one. They never drop to the forest floor,
 * so a forest edge reads as crowns over open ground and not as a green cliff. Canopies have undersides where they
 * float above the ground.</li>
 * <li>Water walls close a water surface against lower neighbours and the void, down to its own floor.</li>
 * </ul>
 * Canopy slabs end at or above the ground and water of their cell, so no two faces of a mesh ever share a plane and a
 * direction.
 *
 * <h2>Vertex layout</h2>
 * {@value #VERTEX_BYTES} bytes, native byte order, the Distant Horizons vertex ABI ({@link LodVertexFormat}); four
 * vertices per quad in the corner order of vanilla's {@code FaceInfo} (counter-clockwise seen from outside), drawn
 * with the shared quad index buffer:
 * <pre>
 *  0  ushort x, y, z  position: x and z relative to the sector's minimum corner, y + {@value #Y_BIAS}
 *  6  ushort meta     light in the low byte: block light << 4 | sky light; high byte 0 (no micro offset)
 *  8  ubyte r, g, b, a colour: albedo x biome tint, unshaded; alpha 255, water {@value #WATER_ALPHA}
 * 12  ubyte material  {@link LodMaterial} id
 * 13  ubyte normal    face direction in {@code Direction} order: down, up, north, south, west, east
 * 14  two bytes of padding
 * </pre>
 * Opaque quads (ground and canopy) come first, water quads after them ({@link LodMesh}).
 */
public final class NativeLodMesher {
    public static final int VERTEX_BYTES = 16;
    public static final int QUAD_BYTES = VERTEX_BYTES * 4;
    public static final int OFFSET_POSITION = 0;
    public static final int OFFSET_META = 6;
    public static final int OFFSET_COLOR = 8;
    public static final int OFFSET_MATERIAL = 12;
    public static final int OFFSET_NORMAL = 13;
    /** Added to every y so that it fits an unsigned short: covers y = -2048..63487, every legal build height. */
    public static final int Y_BIAS = 2048;
    /** Coarsest detail level: 8 x 8 columns per cell. */
    public static final int MAX_LEVEL = 3;
    /** Depth of the skirt along an edge whose neighbour has no data. */
    public static final int VOID_SKIRT = 16;
    /** Depth of the stand-in floor below water whose floor lies out of the scan's reach. */
    public static final int FLOORLESS_DEPTH = LodColumnScan.MAX_FLUID_DEPTH;
    /** Thinnest canopy slab at levels 1 and coarser. */
    public static final int MIN_CANOPY_DEPTH = 2;
    /** Thickest canopy slab: crowns are not drawn as walls down to the forest floor. */
    public static final int MAX_CANOPY_DEPTH = 4;
    /** Vertex alpha of water. */
    public static final int WATER_ALPHA = 0xB8;
    public static final int NORMAL_DOWN = 0;
    public static final int NORMAL_UP = 1;
    public static final int NORMAL_NORTH = 2;
    public static final int NORMAL_SOUTH = 3;
    public static final int NORMAL_WEST = 4;
    public static final int NORMAL_EAST = 5;
    /** Height of a cell or column without a surface. */
    static final int EMPTY = Integer.MIN_VALUE;

    private static final int SECTOR_BLOCKS = LodDataStore.SECTOR_BLOCKS;
    private static final int INITIAL_QUADS = 1024;
    private static final int OPAQUE = 0xFF;

    private NativeLodMesher() {
    }

    /** The four side faces: the neighbour's offset, whether the edge runs along x, and the normal index. */
    enum Side {
        NORTH(0, -1, true, NORMAL_NORTH),
        SOUTH(0, 1, true, NORMAL_SOUTH),
        WEST(-1, 0, false, NORMAL_WEST),
        EAST(1, 0, false, NORMAL_EAST);

        static final Side[] VALUES = values();

        final int dx;
        final int dz;
        final boolean alongX;
        final int normal;

        Side(int dx, int dz, boolean alongX, int normal) {
            this.dx = dx;
            this.dz = dz;
            this.alongX = alongX;
            this.normal = normal;
        }
    }

    /** Unit normal of normal index {@code normal} as {@code x, y, z}. */
    public static int[] normalVector(int normal) {
        return switch (normal) {
            case NORMAL_DOWN -> new int[] {0, -1, 0};
            case NORMAL_UP -> new int[] {0, 1, 0};
            case NORMAL_NORTH -> new int[] {0, 0, -1};
            case NORMAL_SOUTH -> new int[] {0, 0, 1};
            case NORMAL_WEST -> new int[] {-1, 0, 0};
            case NORMAL_EAST -> new int[] {1, 0, 0};
            default -> throw new IllegalArgumentException("Normal index " + normal);
        };
    }

    /**
     * Meshes sector {@code (sectorX, sectorZ)} at detail {@code level}.
     *
     * @param chunks the sector and its one-chunk border as {@link LodDataStore.SectorSnapshot#chunks()} lays them
     *               out; null entries are chunks without data
     */
    public static LodMesh mesh(int sectorX, int sectorZ, LodChunkData[] chunks, int level, LodColorSource colors) {
        if (chunks.length != LodDataStore.SNAPSHOT_SPAN * LodDataStore.SNAPSHOT_SPAN) {
            throw new IllegalArgumentException("Expected a sector snapshot, got " + chunks.length + " chunks");
        }
        if (level < 0 || level > MAX_LEVEL) {
            throw new IllegalArgumentException("Detail level " + level + " outside 0.." + MAX_LEVEL);
        }
        Grid g = Grid.build(sectorX, sectorZ, chunks, level, colors);
        QuadWriter opaque = new QuadWriter();
        emitFlat(g, g.groundTop, g.groundColor, g.groundLight, g.groundMaterial, OPAQUE, true, opaque);
        for (Side side : Side.VALUES) {
            emitGroundWalls(g, side, opaque);
        }
        emitFlat(g, g.canopyTop, g.canopyColor, g.canopyLight, g.canopyMaterial, OPAQUE, true, opaque);
        for (Side side : Side.VALUES) {
            emitCanopyWalls(g, side, opaque);
        }
        emitFlat(g, g.canopyUnderside, g.canopyColor, g.canopyLight, g.canopyMaterial, OPAQUE, false, opaque);
        QuadWriter water = new QuadWriter();
        emitFlat(g, g.waterTop, g.waterColor, g.waterLight, g.waterMaterial, WATER_ALPHA, true, water);
        for (Side side : Side.VALUES) {
            emitWaterWalls(g, side, water);
        }
        return QuadWriter.finish(sectorX, sectorZ, level, opaque, water);
    }

    /** Horizontal faces at {@code heights}: greedy rectangles over cells of equal height, colour, light and material. */
    private static void emitFlat(Grid g, int[] heights, int[] colors, int[] lights, int[] materials, int alpha, boolean up,
            QuadWriter out) {
        int size = g.size;
        boolean[] done = new boolean[size * size];
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                int c = z * size + x;
                if (done[c] || heights[c] == EMPTY) {
                    continue;
                }
                int height = heights[c];
                int color = colors[c];
                int light = lights[c];
                int material = materials[c];
                int width = 1;
                while (x + width < size && !done[c + width]
                        && same(heights, colors, lights, materials, c + width, height, color, light, material)) {
                    width++;
                }
                int depth = 1;
                grow:
                while (z + depth < size) {
                    int row = (z + depth) * size + x;
                    for (int k = 0; k < width; k++) {
                        if (done[row + k] || !same(heights, colors, lights, materials, row + k, height, color, light, material)) {
                            break grow;
                        }
                    }
                    depth++;
                }
                for (int dz = 0; dz < depth; dz++) {
                    int row = (z + dz) * size + x;
                    Arrays.fill(done, row, row + width, true);
                }
                out.flat(x * g.cell, z * g.cell, (x + width) * g.cell, (z + depth) * g.cell, height, color, alpha, light, material, up);
            }
        }
    }

    private static boolean same(int[] heights, int[] colors, int[] lights, int[] materials, int c, int height, int color, int light,
            int material) {
        return heights[c] == height && colors[c] == color && lights[c] == light && materials[c] == material;
    }

    /** Ground skirts on one side of every cell: down to the neighbour's ground, the lowest one across the sector edge. */
    private static void emitGroundWalls(Grid g, Side side, QuadWriter out) {
        int size = g.size;
        int[] border = g.groundBorder[side.ordinal()];
        WallRuns runs = new WallRuns(out, side, g.cell, OPAQUE);
        for (int line = 0; line < size; line++) {
            runs.begin(line);
            for (int along = 0; along < size; along++) {
                int c = g.cellAt(side, line, along);
                int top = g.groundTop[c];
                int bottom = 0;
                boolean wall = false;
                if (top != EMPTY) {
                    int n = g.neighbour(side, line, along);
                    int neighbour = n >= 0 ? g.groundTop[n] : border[along];
                    bottom = neighbour == EMPTY ? top - VOID_SKIRT : neighbour;
                    wall = bottom < top;
                }
                runs.cell(along, wall, top, bottom, g.groundColor[c], g.groundLight[c], g.groundMaterial[c]);
            }
            runs.end(size);
        }
    }

    /**
     * Canopy walls on one side: the part of the slab's side the neighbouring slab does not cover. That is the whole
     * side next to a cell without canopy and across the sector edge (the neighbour's canopy is not known at this
     * level: the slab is closed); next to a canopy it is what rises above the neighbour's top and what hangs below the
     * neighbour's bottom, so staggered crowns leave no see-through slit.
     */
    private static void emitCanopyWalls(Grid g, Side side, QuadWriter out) {
        int size = g.size;
        WallRuns upper = new WallRuns(out, side, g.cell, OPAQUE);
        WallRuns lower = new WallRuns(out, side, g.cell, OPAQUE);
        for (int line = 0; line < size; line++) {
            upper.begin(line);
            lower.begin(line);
            for (int along = 0; along < size; along++) {
                int c = g.cellAt(side, line, along);
                int top = g.canopyTop[c];
                int bottom = g.canopyBottom[c];
                int upperBottom = bottom;
                int lowerTop = bottom;
                if (top != EMPTY) {
                    int n = g.neighbour(side, line, along);
                    if (n >= 0 && g.canopyTop[n] != EMPTY) {
                        upperBottom = Math.max(bottom, g.canopyTop[n]);
                        lowerTop = Math.min(top, g.canopyBottom[n]);
                    }
                }
                int color = g.canopyColor[c];
                int light = g.canopyLight[c];
                int material = g.canopyMaterial[c];
                upper.cell(along, top != EMPTY && upperBottom < top, top, upperBottom, color, light, material);
                lower.cell(along, top != EMPTY && bottom < lowerTop, lowerTop, bottom, color, light, material);
            }
            upper.end(size);
            lower.end(size);
        }
    }

    /** Water walls on one side: down to the neighbour's visible top, not below the cell's own floor. */
    private static void emitWaterWalls(Grid g, Side side, QuadWriter out) {
        int size = g.size;
        int[] border = g.visibleBorder[side.ordinal()];
        WallRuns runs = new WallRuns(out, side, g.cell, WATER_ALPHA);
        for (int line = 0; line < size; line++) {
            runs.begin(line);
            for (int along = 0; along < size; along++) {
                int c = g.cellAt(side, line, along);
                int top = g.waterTop[c];
                int bottom = 0;
                boolean wall = false;
                if (top != EMPTY) {
                    int n = g.neighbour(side, line, along);
                    int neighbour = n >= 0 ? g.visibleTop(n) : border[along];
                    int floor = g.groundTop[c];
                    bottom = neighbour == EMPTY ? floor : Math.max(neighbour, floor);
                    wall = bottom < top;
                }
                runs.cell(along, wall, top, bottom, g.waterColor[c], g.waterLight[c], g.waterMaterial[c]);
            }
            runs.end(size);
        }
    }

    /** Ground height of column {@code i}: its solid surface, or a stand-in floor below a fluid without one. */
    static int groundHeight(LodChunkData data, int i) {
        int solid = data.heightSolid[i];
        return solid != LodChunkData.NO_SURFACE ? solid : data.heightFluid[i] - FLOORLESS_DEPTH;
    }

    /** Height of the visible top below any canopy of column {@code i}: water where there is some, else the ground. */
    static int visibleHeight(LodChunkData data, int i) {
        int ground = groundHeight(data, i);
        return data.hasFluid(i) ? Math.max(ground, data.heightFluid[i]) : ground;
    }

    /** Light of the ground of column {@code i}: below water, sky light fades by one level per block of water. */
    static int groundLight(LodChunkData data, int i) {
        int light = data.packedLight(i);
        if (!data.hasFluid(i)) {
            return light;
        }
        int depth = Math.max(0, data.heightFluid[i] - groundHeight(data, i));
        int sky = Math.max(0, (light & 15) - depth);
        return light & 0xF0 | sky;
    }

    /** Deterministic hash of a world position and a salt, uniform over all 32 bits. */
    static int hash(int x, int z, int salt) {
        int h = x * 0x27D4EB2D + z * 0x165667B1 + salt * 0x9E3779B9;
        h ^= h >>> 15;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        h *= 0xC2B2AE35;
        h ^= h >>> 16;
        return h;
    }

    /** Height offset of a canopy cell at world position {@code (x, z)} and {@code level}: 0 at level 0. */
    static int canopyJitter(int x, int z, int level) {
        if (level == 0) {
            return 0;
        }
        int amplitude = Math.max(1, (1 << level) / 4);
        return Integer.remainderUnsigned(hash(x, z, level * 31 + 7), 2 * amplitude + 1) - amplitude;
    }

    /** The sector reduced to cells of the detail level, with the neighbour heights across each sector edge. */
    static final class Grid {
        final int level;
        final int cell;
        final int size;
        /** World block coordinates of the sector's minimum corner. */
        final int originX;
        final int originZ;
        final int[] groundTop;
        final int[] groundColor;
        final int[] groundLight;
        final int[] groundMaterial;
        final int[] waterTop;
        final int[] waterColor;
        final int[] waterLight;
        final int[] waterMaterial;
        final int[] canopyTop;
        final int[] canopyBottom;
        /** {@link #canopyBottom} where the slab floats above its cell's ground and water, else {@link #EMPTY}. */
        final int[] canopyUnderside;
        final int[] canopyColor;
        final int[] canopyLight;
        final int[] canopyMaterial;
        /** Per {@link Side}: the lowest ground across the sector edge for each cell along it, {@link #EMPTY} for void. */
        final int[][] groundBorder = new int[Side.VALUES.length][];
        /** Per {@link Side}: the lowest visible top (ground or water) across the sector edge, {@link #EMPTY} for void. */
        final int[][] visibleBorder = new int[Side.VALUES.length][];

        private Grid(int sectorX, int sectorZ, int level) {
            this.level = level;
            this.cell = 1 << level;
            this.size = SECTOR_BLOCKS >> level;
            this.originX = sectorX * SECTOR_BLOCKS;
            this.originZ = sectorZ * SECTOR_BLOCKS;
            int cells = size * size;
            groundTop = filled(cells);
            groundColor = new int[cells];
            groundLight = new int[cells];
            groundMaterial = new int[cells];
            waterTop = filled(cells);
            waterColor = new int[cells];
            waterLight = new int[cells];
            waterMaterial = new int[cells];
            canopyTop = filled(cells);
            canopyBottom = filled(cells);
            canopyUnderside = filled(cells);
            canopyColor = new int[cells];
            canopyLight = new int[cells];
            canopyMaterial = new int[cells];
        }

        private static int[] filled(int cells) {
            int[] out = new int[cells];
            Arrays.fill(out, EMPTY);
            return out;
        }

        static Grid build(int sectorX, int sectorZ, LodChunkData[] chunks, int level, LodColorSource colors) {
            Grid g = new Grid(sectorX, sectorZ, level);
            for (int z = 0; z < g.size; z++) {
                for (int x = 0; x < g.size; x++) {
                    g.fillCell(chunks, x, z, colors);
                }
            }
            for (Side side : Side.VALUES) {
                g.borderHeights(chunks, side);
            }
            return g;
        }

        /** Cell on cell line {@code line} (z for edges along x, x otherwise) at {@code along}. */
        int cellAt(Side side, int line, int along) {
            return side.alongX ? line * size + along : along * size + line;
        }

        /** The cell across {@code side} of the cell at {@code (line, along)}, or -1 beyond the sector edge. */
        int neighbour(Side side, int line, int along) {
            int x = (side.alongX ? along : line) + side.dx;
            int z = (side.alongX ? line : along) + side.dz;
            return x >= 0 && x < size && z >= 0 && z < size ? z * size + x : -1;
        }

        /** Visible top of cell {@code c} below its canopy: its water where there is some, else its ground. */
        int visibleTop(int c) {
            return waterTop[c] != EMPTY ? waterTop[c] : groundTop[c];
        }

        /** A cell lies inside one chunk: the cell size divides 16 and cells start on multiples of it. */
        private void fillCell(LodChunkData[] chunks, int cellX, int cellZ, LodColorSource colors) {
            int c = cellZ * size + cellX;
            int bx = cellX * cell;
            int bz = cellZ * cell;
            LodChunkData data = chunks[LodDataStore.snapshotIndex(bx >> 4, bz >> 4)];
            if (data == null) {
                return;
            }
            int bestGround = EMPTY;
            int groundIndex = -1;
            int bestWater = EMPTY;
            int waterIndex = -1;
            int canopies = 0;
            for (int dz = 0; dz < cell; dz++) {
                for (int dx = 0; dx < cell; dx++) {
                    int i = LodChunkData.index(bx + dx & 15, bz + dz & 15);
                    if (!data.hasSurface(i)) {
                        continue;
                    }
                    int ground = groundHeight(data, i);
                    if (ground > bestGround) {
                        bestGround = ground;
                        groundIndex = i;
                    }
                    if (data.hasFluid(i) && data.heightFluid[i] > bestWater) {
                        bestWater = data.heightFluid[i];
                        waterIndex = i;
                    }
                    if (data.hasCanopy(i)) {
                        canopies++;
                    }
                }
            }
            if (groundIndex < 0) {
                return;
            }
            groundTop[c] = bestGround;
            boolean floorless = data.heightSolid[groundIndex] == LodChunkData.NO_SURFACE;
            int groundState = floorless ? data.fluidStateId[groundIndex] : data.blockStateId[groundIndex];
            int groundTint = floorless ? data.fluidTint[groundIndex] : data.solidTint[groundIndex];
            groundColor[c] = LodColors.multiply(colors.baseColor(groundState) & 0xFFFFFF, groundTint);
            groundLight[c] = groundLight(data, groundIndex);
            // A stand-in floor is opaque ground in the water's colour, not water: packs treat DH_BLOCK_WATER as water.
            groundMaterial[c] = floorless ? LodMaterial.UNKNOWN : material(colors, groundState);
            int floor = bestGround;
            if (waterIndex >= 0 && bestWater > bestGround) {
                waterTop[c] = bestWater;
                waterColor[c] = LodColors.multiply(colors.baseColor(data.fluidStateId[waterIndex]) & 0xFFFFFF, data.fluidTint[waterIndex]);
                waterLight[c] = data.packedLight(waterIndex);
                waterMaterial[c] = LodMaterial.WATER;
                floor = bestWater;
            }
            fillCanopy(data, c, bx, bz, canopies, floor, colors);
        }

        /** The canopy of cell {@code c}: see the class description. {@code floor} is the cell's visible top below it. */
        private void fillCanopy(LodChunkData data, int c, int bx, int bz, int canopies, int floor, LodColorSource colors) {
            int representative = -1;
            int minDepth;
            int jitter = canopyJitter(originX + bx, originZ + bz, level);
            if (level == 0) {
                int i = LodChunkData.index(bx & 15, bz & 15);
                if (data.hasCanopy(i)) {
                    representative = i;
                }
                minDepth = 1;
            } else {
                if (canopies * 2 < cell * cell) {
                    return;
                }
                int pick = Integer.remainderUnsigned(hash(originX + bx, originZ + bz, level), canopies);
                scan:
                for (int dz = 0; dz < cell; dz++) {
                    for (int dx = 0; dx < cell; dx++) {
                        int i = LodChunkData.index(bx + dx & 15, bz + dz & 15);
                        if (data.hasSurface(i) && data.hasCanopy(i) && pick-- == 0) {
                            representative = i;
                            break scan;
                        }
                    }
                }
                minDepth = MIN_CANOPY_DEPTH;
            }
            if (representative < 0) {
                return;
            }
            int realTop = data.canopyTop[representative];
            int top = realTop + jitter;
            if (top <= floor) {
                return;
            }
            int depth = Math.clamp(realTop - data.canopyBottom[representative], minDepth, MAX_CANOPY_DEPTH);
            int bottom = Math.max(top - depth, floor);
            int state = data.canopyStateId[representative];
            canopyTop[c] = top;
            canopyBottom[c] = bottom;
            canopyUnderside[c] = bottom > floor ? bottom : EMPTY;
            canopyColor[c] = LodColors.multiply(colors.baseColor(state) & 0xFFFFFF, data.canopyTint[representative]);
            canopyLight[c] = data.packedCanopyLight(representative);
            canopyMaterial[c] = material(colors, state);
        }

        /** The lowest ground and visible top across the sector edge along each cell; {@link #EMPTY} if all are void. */
        private void borderHeights(LodChunkData[] chunks, Side side) {
            int[] ground = new int[size];
            int[] visible = new int[size];
            for (int k = 0; k < size; k++) {
                int lowestGround = Integer.MAX_VALUE;
                int lowestVisible = Integer.MAX_VALUE;
                boolean any = false;
                for (int d = 0; d < cell; d++) {
                    int along = k * cell + d;
                    int bx = side.alongX ? along : side.dx < 0 ? -1 : SECTOR_BLOCKS;
                    int bz = side.alongX ? side.dz < 0 ? -1 : SECTOR_BLOCKS : along;
                    LodChunkData data = chunks[LodDataStore.snapshotIndex(bx >> 4, bz >> 4)];
                    if (data == null) {
                        continue;
                    }
                    int i = LodChunkData.index(bx & 15, bz & 15);
                    if (!data.hasSurface(i)) {
                        continue;
                    }
                    any = true;
                    lowestGround = Math.min(lowestGround, groundHeight(data, i));
                    lowestVisible = Math.min(lowestVisible, visibleHeight(data, i));
                }
                ground[k] = any ? lowestGround : EMPTY;
                visible[k] = any ? lowestVisible : EMPTY;
            }
            groundBorder[side.ordinal()] = ground;
            visibleBorder[side.ordinal()] = visible;
        }

        private static int material(LodColorSource colors, int stateId) {
            int material = colors.material(stateId);
            return LodMaterial.isValid(material) ? material : LodMaterial.UNKNOWN;
        }
    }

    /** Merges consecutive equal walls along one cell line into single quads. */
    private static final class WallRuns {
        private final QuadWriter out;
        private final Side side;
        private final int cell;
        private final int alpha;
        private int line;
        private int start = -1;
        private int top;
        private int bottom;
        private int color;
        private int light;
        private int material;

        WallRuns(QuadWriter out, Side side, int cell, int alpha) {
            this.out = out;
            this.side = side;
            this.cell = cell;
            this.alpha = alpha;
        }

        void begin(int line) {
            this.line = line;
            this.start = -1;
        }

        void cell(int along, boolean wall, int top, int bottom, int color, int light, int material) {
            if (start >= 0 && (!wall || top != this.top || bottom != this.bottom || color != this.color || light != this.light
                    || material != this.material)) {
                flush(along);
            }
            if (wall && start < 0) {
                start = along;
                this.top = top;
                this.bottom = bottom;
                this.color = color;
                this.light = light;
                this.material = material;
            }
        }

        void end(int size) {
            if (start >= 0) {
                flush(size);
            }
        }

        private void flush(int to) {
            out.side(side, line, start, to, bottom, top, color, alpha, light, material, cell);
            start = -1;
        }
    }

    /** Appends quads to a growing heap buffer in the LOD vertex layout. */
    static final class QuadWriter {
        private ByteBuffer buffer = ByteBuffer.allocate(INITIAL_QUADS * QUAD_BYTES).order(ByteOrder.nativeOrder());
        private int quads;
        private int minY = Integer.MAX_VALUE;
        private int maxY = Integer.MIN_VALUE;

        /** Horizontal face at {@code y} over {@code [x0, x1] x [z0, z1]}, facing up or down. */
        void flat(int x0, int z0, int x1, int z1, int y, int rgb, int alpha, int light, int material, boolean up) {
            reserve();
            if (up) {
                vertex(x0, y, z0, rgb, alpha, light, material, NORMAL_UP);
                vertex(x0, y, z1, rgb, alpha, light, material, NORMAL_UP);
                vertex(x1, y, z1, rgb, alpha, light, material, NORMAL_UP);
                vertex(x1, y, z0, rgb, alpha, light, material, NORMAL_UP);
            } else {
                vertex(x0, y, z0, rgb, alpha, light, material, NORMAL_DOWN);
                vertex(x1, y, z0, rgb, alpha, light, material, NORMAL_DOWN);
                vertex(x1, y, z1, rgb, alpha, light, material, NORMAL_DOWN);
                vertex(x0, y, z1, rgb, alpha, light, material, NORMAL_DOWN);
            }
            track(y, y);
        }

        /**
         * Wall facing {@code side} on cell line {@code line}, spanning cells {@code [from, to)} along the edge and
         * {@code [bottom, top]} vertically.
         */
        void side(Side side, int line, int from, int to, int bottom, int top, int rgb, int alpha, int light, int material, int cell) {
            int n = side.normal;
            int a0 = from * cell;
            int a1 = to * cell;
            reserve();
            switch (side) {
                case NORTH -> {
                    int z = line * cell;
                    vertex(a1, top, z, rgb, alpha, light, material, n);
                    vertex(a1, bottom, z, rgb, alpha, light, material, n);
                    vertex(a0, bottom, z, rgb, alpha, light, material, n);
                    vertex(a0, top, z, rgb, alpha, light, material, n);
                }
                case SOUTH -> {
                    int z = (line + 1) * cell;
                    vertex(a0, top, z, rgb, alpha, light, material, n);
                    vertex(a0, bottom, z, rgb, alpha, light, material, n);
                    vertex(a1, bottom, z, rgb, alpha, light, material, n);
                    vertex(a1, top, z, rgb, alpha, light, material, n);
                }
                case WEST -> {
                    int x = line * cell;
                    vertex(x, top, a0, rgb, alpha, light, material, n);
                    vertex(x, bottom, a0, rgb, alpha, light, material, n);
                    vertex(x, bottom, a1, rgb, alpha, light, material, n);
                    vertex(x, top, a1, rgb, alpha, light, material, n);
                }
                case EAST -> {
                    int x = (line + 1) * cell;
                    vertex(x, top, a1, rgb, alpha, light, material, n);
                    vertex(x, bottom, a1, rgb, alpha, light, material, n);
                    vertex(x, bottom, a0, rgb, alpha, light, material, n);
                    vertex(x, top, a0, rgb, alpha, light, material, n);
                }
            }
            track(bottom, top);
        }

        int quads() {
            return quads;
        }

        /** One buffer: the opaque quads, then the water quads; the culling box spans both. */
        static LodMesh finish(int sectorX, int sectorZ, int level, QuadWriter opaque, QuadWriter water) {
            int total = opaque.quads + water.quads;
            ByteBuffer out = ByteBuffer.allocate(total * QUAD_BYTES).order(ByteOrder.nativeOrder());
            opaque.buffer.flip();
            out.put(opaque.buffer);
            water.buffer.flip();
            out.put(water.buffer);
            out.flip();
            if (total == 0) {
                return new LodMesh(sectorX, sectorZ, level, out, 0, 0, 0, 0);
            }
            return new LodMesh(sectorX, sectorZ, level, out, opaque.quads, water.quads, Math.min(opaque.minY, water.minY),
                    Math.max(opaque.maxY, water.maxY));
        }

        private void reserve() {
            if (buffer.remaining() < QUAD_BYTES) {
                ByteBuffer grown = ByteBuffer.allocate(buffer.capacity() * 2).order(ByteOrder.nativeOrder());
                buffer.flip();
                grown.put(buffer);
                buffer = grown;
            }
            quads++;
        }

        private void track(int low, int high) {
            minY = Math.min(minY, low);
            maxY = Math.max(maxY, high);
        }

        private void vertex(int x, int y, int z, int rgb, int alpha, int light, int material, int normal) {
            buffer.putShort((short) x).putShort((short) Math.clamp(y + (long) Y_BIAS, 0L, 0xFFFFL)).putShort((short) z)
                    .putShort((short) (light & 0xFF));
            buffer.put((byte) (rgb >> 16)).put((byte) (rgb >> 8)).put((byte) rgb).put((byte) alpha);
            buffer.put((byte) material).put((byte) normal).put((byte) 0).put((byte) 0);
        }
    }
}
