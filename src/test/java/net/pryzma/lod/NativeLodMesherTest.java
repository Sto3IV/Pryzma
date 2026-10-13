package net.pryzma.lod;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.IntBinaryOperator;

import org.junit.jupiter.api.Test;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

/**
 * Farlands LOD mesher: merged tops, gap-free ground skirts, canopies that read as crowns, water in its own pass, the
 * vertex layout and the winding of every quad, at every detail level. {@link #assertSealedGround} derives the expected
 * ground from the column heights alone (a cell is its highest column; a wall runs from a cell down to its lower
 * neighbour, across the sector edge to the lowest neighbouring column, into the void for
 * {@value NativeLodMesher#VOID_SKIRT} blocks) and checks the mesh against it face by face.
 */
class NativeLodMesherTest {
    private static final int NONE = Integer.MIN_VALUE;
    private static final int SKY = 15;
    private static final int[] PALETTE = {0x80C040, 0x3060A0, 0xD0B080};
    /** States 0..2 are ground, {@link #LEAVES_STATE} is foliage, {@link #WATER_STATE} is water. */
    private static final int LEAVES_STATE = 7;
    private static final int WATER_STATE = 9;
    private static final int LEAVES_RGB = 0x2F6F1F;
    private static final int WATER_RGB = 0x3F76E4;
    private static final LodColorSource COLORS = new LodColorSource() {
        @Override
        public int baseColor(int stateId) {
            return stateId == LEAVES_STATE ? LEAVES_RGB : stateId == WATER_STATE ? WATER_RGB : PALETTE[stateId % PALETTE.length];
        }

        @Override
        public int material(int stateId) {
            return stateId == LEAVES_STATE ? LodMaterial.LEAVES : stateId == WATER_STATE ? LodMaterial.WATER : LodMaterial.STONE;
        }
    };

    // ------------------------------------------------------------------ ground

    @Test
    void flatSectorAmongFlatNeighboursIsOneQuadAtEveryLevel() {
        IntBinaryOperator height = (bx, bz) -> 64;
        LodChunkData[] chunks = ground(height, (bx, bz) -> 0);
        for (int level = 0; level <= NativeLodMesher.MAX_LEVEL; level++) {
            LodMesh mesh = assertSealedGround(chunks, height, (bx, bz) -> 0, level);
            assertEquals(1, mesh.quadCount(), "level " + level);
            assertEquals(0, mesh.waterQuads());
            assertEquals(64, mesh.minY());
            assertEquals(64, mesh.maxY());
            Quad top = quads(mesh).get(0);
            assertEquals(0, top.minX());
            assertEquals(LodDataStore.SECTOR_BLOCKS, top.maxX());
            assertEquals(0, top.minZ());
            assertEquals(LodDataStore.SECTOR_BLOCKS, top.maxZ());
            assertEquals(LodMaterial.STONE, top.material());
        }
    }

    @Test
    void sectorWithoutNeighboursGetsOneVoidSkirtPerSide() {
        IntBinaryOperator height = (bx, bz) -> inSector(bx, bz) ? 64 : NONE;
        LodChunkData[] chunks = ground(height, (bx, bz) -> 0);
        for (int level = 0; level <= NativeLodMesher.MAX_LEVEL; level++) {
            LodMesh mesh = assertSealedGround(chunks, height, (bx, bz) -> 0, level);
            assertEquals(5, mesh.quadCount(), "a top and four skirts, level " + level);
            assertEquals(64 - NativeLodMesher.VOID_SKIRT, mesh.minY());
            assertEquals(64, mesh.maxY());
        }
    }

    @Test
    void stepGetsExactlyOneWallOnItsHighSide() {
        IntBinaryOperator height = (bx, bz) -> bx < 128 ? 70 : 64;
        LodChunkData[] chunks = ground(height, (bx, bz) -> 0);
        for (int level = 0; level <= NativeLodMesher.MAX_LEVEL; level++) {
            LodMesh mesh = assertSealedGround(chunks, height, (bx, bz) -> 0, level);
            assertEquals(3, mesh.quadCount(), "two tops and one wall, level " + level);
            List<Quad> walls = quads(mesh).stream().filter(Quad::isWall).toList();
            assertEquals(1, walls.size());
            Quad wall = walls.get(0);
            assertEquals(NativeLodMesher.NORMAL_EAST, wall.normal());
            assertEquals(128, wall.minX());
            assertEquals(128, wall.maxX());
            assertEquals(64, wall.minY());
            assertEquals(70, wall.maxY());
            assertEquals(0, wall.minZ());
            assertEquals(LodDataStore.SECTOR_BLOCKS, wall.maxZ());
        }
    }

    @Test
    void mergingStopsAtMaterialBorders() {
        IntBinaryOperator height = (bx, bz) -> 64;
        IntBinaryOperator state = (bx, bz) -> bz < 100 ? 0 : 1;
        LodMesh mesh = assertSealedGround(ground(height, state), height, state, 0);
        assertEquals(2, mesh.quadCount(), "one top per material, no walls on flat ground");
    }

    @Test
    void coarseCellsTakeTheirHighestColumn() {
        IntBinaryOperator height = (bx, bz) -> bx == 5 && bz == 6 ? 90 : 64;
        IntBinaryOperator state = (bx, bz) -> bx == 5 && bz == 6 ? 2 : 0;
        LodChunkData[] chunks = ground(height, state);
        LodMesh mesh = assertSealedGround(chunks, height, state, 3);
        assertEquals(7, mesh.quadCount(), "the raised 8x8 cell, two tops around it and its four walls");
        Quad peak = quads(mesh).stream().filter(q -> q.normal() == NativeLodMesher.NORMAL_UP && q.minY() == 90).findFirst().orElseThrow();
        assertEquals(0, peak.minX());
        assertEquals(8, peak.maxX());
        assertEquals(0, peak.minZ());
        assertEquals(8, peak.maxZ());
        assertEquals(PALETTE[2], peak.rgb(), "the colour of the highest column, unshaded");
        for (int level = 0; level <= NativeLodMesher.MAX_LEVEL; level++) {
            assertSealedGround(chunks, height, state, level);
        }
    }

    @Test
    void sectorEdgeWallsReachTheLowestColumnAcrossTheEdge() {
        IntBinaryOperator height = (bx, bz) -> bx == LodDataStore.SECTOR_BLOCKS ? 50 + Math.floorMod(bz, 5) : 80;
        LodChunkData[] chunks = ground(height, (bx, bz) -> 0);
        for (int level = 0; level <= NativeLodMesher.MAX_LEVEL; level++) {
            assertSealedGround(chunks, height, (bx, bz) -> 0, level);
        }
        // Level 2: cells of four columns; the window bz 16..19 holds residues 1..4, so its wall stops at 51.
        LodMesh mesh = NativeLodMesher.mesh(0, 0, chunks, 2, COLORS);
        Quad wall = quads(mesh).stream()
                .filter(q -> q.normal() == NativeLodMesher.NORMAL_EAST && q.minZ() <= 16 && q.maxZ() >= 20)
                .findFirst().orElseThrow();
        assertEquals(51, wall.minY());
        assertEquals(80, wall.maxY());
    }

    @Test
    void randomTerrainWithHolesIsSealedAtEveryLevel() {
        Random random = new Random(0x5EEDL);
        int span = LodDataStore.SNAPSHOT_SPAN * 16;
        int[] table = new int[span * span];
        for (int i = 0; i < table.length; i++) {
            table[i] = 60 + random.nextInt(8);
        }
        // A chunk missing inside the sector, one missing on the east border, and scattered void columns.
        IntBinaryOperator height = (bx, bz) -> {
            if (bx >> 4 == 3 && bz >> 4 == 7 || bx >> 4 == LodDataStore.SECTOR_CHUNKS && bz >> 4 == 5) {
                return NONE;
            }
            if ((bx * 31 + bz * 17) % 97 == 0) {
                return NONE;
            }
            return table[(bz + 16) * span + bx + 16];
        };
        IntBinaryOperator state = (bx, bz) -> Math.floorMod(bx / 7 + bz / 5, PALETTE.length);
        LodChunkData[] chunks = ground(height, state);
        for (int level = 0; level <= NativeLodMesher.MAX_LEVEL; level++) {
            LodMesh mesh = assertSealedGround(chunks, height, state, level);
            assertTrue(mesh.quadCount() > 0);
        }
    }

    @Test
    void plateausMergeIntoFewQuads() {
        IntBinaryOperator height = (bx, bz) -> 64 + Math.floorDiv(bx, 64) + Math.floorDiv(bz, 64);
        LodChunkData[] chunks = ground(height, (bx, bz) -> 0);
        LodMesh mesh = assertSealedGround(chunks, height, (bx, bz) -> 0, 0);
        // 16 plateaus of 64x64, one top each; walls: 3 internal lines per axis with 4 differently high steps each,
        // plus the 4 steps down to the lower neighbours on the north and on the west edge.
        assertEquals(16 + 24 + 8, mesh.quadCount());
    }

    @Test
    void emptySnapshotMeshesToNothing() {
        LodMesh mesh = NativeLodMesher.mesh(0, 0, new LodChunkData[LodDataStore.SNAPSHOT_SPAN * LodDataStore.SNAPSHOT_SPAN], 0, COLORS);
        assertTrue(mesh.isEmpty());
        assertEquals(0, mesh.byteSize());
        assertEquals(0, mesh.vertices().remaining());
    }

    @Test
    void rejectsMalformedRequests() {
        LodChunkData[] chunks = new LodChunkData[LodDataStore.SNAPSHOT_SPAN * LodDataStore.SNAPSHOT_SPAN];
        assertThrows(IllegalArgumentException.class, () -> NativeLodMesher.mesh(0, 0, new LodChunkData[3], 0, COLORS));
        assertThrows(IllegalArgumentException.class, () -> NativeLodMesher.mesh(0, 0, chunks, -1, COLORS));
        assertThrows(IllegalArgumentException.class, () -> NativeLodMesher.mesh(0, 0, chunks, NativeLodMesher.MAX_LEVEL + 1, COLORS));
    }

    @Test
    void vertexBytesFollowTheDistantHorizonsLayout() {
        LodChunkData[] chunks = snapshot((data, i, bx, bz) -> data.setColumn(i, 64, 5, LodChunkData.NO_FLUID, 0, 12, 9));
        LodMesh mesh = NativeLodMesher.mesh(7, -3, chunks, 0, stateId -> 0x336699);
        assertEquals(7, mesh.sectorX());
        assertEquals(-3, mesh.sectorZ());
        assertEquals(0, mesh.level());
        assertEquals(1, mesh.quadCount());
        assertEquals(NativeLodMesher.QUAD_BYTES, mesh.byteSize());
        assertEquals(16, NativeLodMesher.VERTEX_BYTES, "the Distant Horizons vertex is 16 bytes");
        assertEquals(4 * NativeLodMesher.VERTEX_BYTES, NativeLodMesher.QUAD_BYTES);
        ByteBuffer b = mesh.vertices();
        assertEquals(0, b.getShort(NativeLodMesher.OFFSET_POSITION));
        assertEquals(64 + NativeLodMesher.Y_BIAS, b.getShort(NativeLodMesher.OFFSET_POSITION + 2) & 0xFFFF);
        assertEquals(0, b.getShort(NativeLodMesher.OFFSET_POSITION + 4));
        assertEquals(9 << 4 | 12, b.getShort(NativeLodMesher.OFFSET_META) & 0xFFFF, "block light << 4 | sky light, no micro offset");
        assertEquals(0x33, b.get(NativeLodMesher.OFFSET_COLOR) & 0xFF);
        assertEquals(0x66, b.get(NativeLodMesher.OFFSET_COLOR + 1) & 0xFF);
        assertEquals(0x99, b.get(NativeLodMesher.OFFSET_COLOR + 2) & 0xFF);
        assertEquals(0xFF, b.get(NativeLodMesher.OFFSET_COLOR + 3) & 0xFF);
        assertEquals(LodMaterial.UNKNOWN, b.get(NativeLodMesher.OFFSET_MATERIAL));
        assertEquals(NativeLodMesher.NORMAL_UP, b.get(NativeLodMesher.OFFSET_NORMAL));
        assertEquals(0, b.get(NativeLodMesher.OFFSET_NORMAL + 1));
        assertEquals(0, b.get(NativeLodMesher.OFFSET_NORMAL + 2));
        int third = 2 * NativeLodMesher.VERTEX_BYTES;
        assertEquals(256, b.getShort(third) & 0xFFFF);
        assertEquals(256, b.getShort(third + 4) & 0xFFFF);
    }

    // ------------------------------------------------------------------ canopy

    /** Flat ground at 64 under a forest whose crowns top out at 70..72 and bottom out three blocks lower. */
    private static LodChunkData[] forest(IntBinaryOperator canopyTop) {
        return snapshot((data, i, bx, bz) -> {
            data.setColumn(i, 64, 0, LodChunkData.NO_FLUID, 0, 11, 0);
            int top = canopyTop.applyAsInt(bx, bz);
            if (top != NONE) {
                data.setCanopy(i, top, top - 3, LEAVES_STATE, LodChunkData.NO_TINT, SKY);
            }
        });
    }

    @Test
    void forestGroundStaysFlatBelowItsCrowns() {
        LodChunkData[] chunks = forest((bx, bz) -> 70 + Math.floorMod(bx * 7 + bz * 3, 3));
        for (int level = 0; level <= NativeLodMesher.MAX_LEVEL; level++) {
            LodMesh mesh = assertSealedCanopy(chunks, 0, 0, level);
            List<Quad> ground = quads(mesh).stream().filter(q -> q.material() == LodMaterial.STONE).toList();
            assertEquals(1, ground.size(), "the forest floor is one merged top, level " + level);
            assertEquals(64, ground.get(0).minY());
            assertEquals(11, ground.get(0).light() & 15, "the shaded light below the crowns");
            List<Quad> canopy = quads(mesh).stream().filter(q -> q.material() == LodMaterial.LEAVES).toList();
            assertFalse(canopy.isEmpty(), "level " + level);
            for (Quad q : canopy) {
                assertTrue(q.minY() > 64, "no canopy face reaches the ground: " + q);
                assertTrue(q.maxY() - q.minY() <= NativeLodMesher.MAX_CANOPY_DEPTH, "canopy walls are slabs, not cliffs: " + q);
                assertEquals(SKY, q.light() & 15);
                assertEquals(LEAVES_RGB, q.rgb());
            }
            assertTrue(canopy.stream().anyMatch(q -> q.normal() == NativeLodMesher.NORMAL_DOWN), "crowns float: undersides, level " + level);
        }
    }

    @Test
    void forestEdgeWallsStopAtTheCrownsBottom() {
        // Crowns over the west half, open ground in the east half.
        LodChunkData[] chunks = forest((bx, bz) -> bx < 128 ? 72 : NONE);
        LodMesh mesh = assertSealedCanopy(chunks, 0, 0, 0);
        List<Quad> edge = quads(mesh).stream()
                .filter(q -> q.material() == LodMaterial.LEAVES && q.normal() == NativeLodMesher.NORMAL_EAST && q.minX() == 128)
                .toList();
        assertEquals(1, edge.size(), "one merged wall along the forest edge");
        assertEquals(69, edge.get(0).minY(), "three blocks of crown, not eight down to the ground");
        assertEquals(72, edge.get(0).maxY());
        assertTrue(quads(mesh).stream().noneMatch(q -> q.isWall() && q.minY() <= 64 && q.maxY() > 64), "no wall drops to the forest floor");
    }

    @Test
    void sparseTreesDoNotRaiseCoarseCells() {
        // One 2 x 2 crown per 16 x 16 area: a canopy while it covers at least half a cell, gone in coarser cells.
        LodChunkData[] chunks = forest((bx, bz) -> Math.floorMod(bx, 16) < 2 && Math.floorMod(bz, 16) < 2 ? 71 : NONE);
        for (int level = 0; level <= 1; level++) {
            LodMesh fine = assertSealedCanopy(chunks, 0, 0, level);
            assertTrue(quads(fine).stream().anyMatch(q -> q.material() == LodMaterial.LEAVES), "level " + level);
        }
        for (int level = 2; level <= NativeLodMesher.MAX_LEVEL; level++) {
            LodMesh coarse = NativeLodMesher.mesh(0, 0, chunks, level, COLORS);
            assertEquals(1, coarse.quadCount(), "only the flat ground remains, level " + level);
            assertEquals(64, coarse.maxY(), "no green pillars, level " + level);
        }
    }

    @Test
    void staggeredCrownsLeaveNoSlit() {
        // Rows of crowns alternating between [66, 70] and [68, 72]: each side face of the lower slab must be closed
        // below the higher neighbour's underside, each side of the higher slab above the lower neighbour's top.
        LodChunkData[] chunks = snapshot((data, i, bx, bz) -> {
            data.setColumn(i, 60, 0, LodChunkData.NO_FLUID, 0, 11, 0);
            int top = Math.floorMod(bx, 2) == 0 ? 70 : 72;
            data.setCanopy(i, top, top - 4, LEAVES_STATE, LodChunkData.NO_TINT, SKY);
        });
        LodMesh mesh = assertSealedCanopy(chunks, 0, 0, 0);
        List<Quad> lowSides = quads(mesh).stream()
                .filter(q -> q.material() == LodMaterial.LEAVES && q.normal() == NativeLodMesher.NORMAL_EAST && q.minX() == 1)
                .toList();
        assertEquals(1, lowSides.size(), "the lower slab's east face under the higher neighbour, merged along z");
        assertEquals(66, lowSides.get(0).minY());
        assertEquals(68, lowSides.get(0).maxY(), "up to the neighbour's underside");
        List<Quad> highSides = quads(mesh).stream()
                .filter(q -> q.material() == LodMaterial.LEAVES && q.normal() == NativeLodMesher.NORMAL_WEST && q.minX() == 1)
                .toList();
        assertEquals(1, highSides.size(), "the higher slab's west face above the lower neighbour");
        assertEquals(70, highSides.get(0).minY());
        assertEquals(72, highSides.get(0).maxY());
        for (int level = 1; level <= NativeLodMesher.MAX_LEVEL; level++) {
            assertSealedCanopy(chunks, 0, 0, level);
        }
    }

    @Test
    void canopySlabsFollowTheCrownsAtFullDetail() {
        // Level 0 keeps every crown: its top, at most MAX_CANOPY_DEPTH blocks of it, never below the ground or water.
        LodChunkData[] chunks = snapshot((data, i, bx, bz) -> {
            boolean wet = bx >= 128;
            if (wet) {
                data.setColumn(i, 58, 1, 63, WATER_STATE, SKY, 0);
            } else {
                data.setColumn(i, 64, 0, LodChunkData.NO_FLUID, 0, 12, 0);
            }
            switch (Math.floorMod(bx, 4)) {
                case 0 -> data.setCanopy(i, 75, 66, LEAVES_STATE, LodChunkData.NO_TINT, SKY); // tall crown: clamped to 4
                case 1 -> data.setCanopy(i, 65, 64, LEAVES_STATE, LodChunkData.NO_TINT, SKY); // bush on the dry ground
                case 2 -> data.setCanopy(i, 63, 62, LEAVES_STATE, LodChunkData.NO_TINT, SKY); // not above the floor: none
                default -> {
                }
            }
        });
        NativeLodMesher.Grid g = NativeLodMesher.Grid.build(0, 0, chunks, 0, COLORS);
        for (int bz = 0; bz < LodDataStore.SECTOR_BLOCKS; bz += 37) {
            for (int bx = 0; bx < LodDataStore.SECTOR_BLOCKS; bx++) {
                int c = bz * g.size + bx;
                int floor = bx >= 128 ? 63 : 64;
                switch (Math.floorMod(bx, 4)) {
                    case 0 -> {
                        assertEquals(75, g.canopyTop[c]);
                        assertEquals(75 - NativeLodMesher.MAX_CANOPY_DEPTH, g.canopyBottom[c]);
                        assertEquals(75 - NativeLodMesher.MAX_CANOPY_DEPTH, g.canopyUnderside[c], "floats: an underside");
                    }
                    case 1 -> {
                        int bottom = Math.max(64, floor);
                        assertEquals(65, g.canopyTop[c]);
                        assertEquals(bottom, g.canopyBottom[c], "never below the floor");
                        assertEquals(bottom > floor ? bottom : NativeLodMesher.EMPTY, g.canopyUnderside[c],
                                "an underside only where the slab floats: over the water, not on the dry ground");
                    }
                    default -> assertEquals(NativeLodMesher.EMPTY, g.canopyTop[c], "column " + bx);
                }
            }
        }
        assertSealedCanopy(chunks, 0, 0, 0);
    }

    @Test
    void denseFlatCanopyIsBrokenUpAtCoarseLevels() {
        LodChunkData[] chunks = forest((bx, bz) -> 72);
        for (int level = 1; level <= NativeLodMesher.MAX_LEVEL; level++) {
            LodMesh mesh = assertSealedCanopy(chunks, 0, 0, level);
            List<Quad> tops = quads(mesh).stream().filter(q -> q.material() == LodMaterial.LEAVES && q.normal() == NativeLodMesher.NORMAL_UP).toList();
            Set<Integer> heights = new HashSet<>();
            int largest = 0;
            int amplitude = Math.max(1, (1 << level) / 4);
            for (Quad q : tops) {
                heights.add(q.minY());
                largest = Math.max(largest, (q.maxX() - q.minX()) * (q.maxZ() - q.minZ()));
                assertTrue(Math.abs(q.minY() - 72) <= amplitude, "jitter stays within its amplitude: " + q);
            }
            assertEquals(2 * amplitude + 1, heights.size(), "every jitter height occurs, level " + level);
            assertTrue(largest < LodDataStore.SECTOR_BLOCKS * LodDataStore.SECTOR_BLOCKS / 64,
                    "no flat table spans a large part of the sector, level " + level + ": " + largest);
            assertArrayEquals(bytes(mesh), bytes(NativeLodMesher.mesh(0, 0, chunks, level, COLORS)), "deterministic, level " + level);
        }
    }

    @Test
    void canopyJitterDependsOnTheWorldPositionOnly() {
        for (int level = 1; level <= NativeLodMesher.MAX_LEVEL; level++) {
            int amplitude = Math.max(1, (1 << level) / 4);
            int[] counts = new int[2 * amplitude + 1];
            for (int i = 0; i < 4096; i++) {
                int x = i * 37 - 70000;
                int z = i * -53 + 12345;
                int jitter = NativeLodMesher.canopyJitter(x, z, level);
                assertEquals(jitter, NativeLodMesher.canopyJitter(x, z, level));
                counts[jitter + amplitude]++;
            }
            for (int count : counts) {
                assertTrue(count > 4096 / counts.length / 2, "spread over every value: " + Arrays.toString(counts));
            }
        }
        assertEquals(0, NativeLodMesher.canopyJitter(123, 456, 0), "level 0 shows the real crowns");
    }

    // ------------------------------------------------------------------ water

    /** A lake (floor 50, surface 63) in the middle of the sector, land at 66 around it. */
    private static LodChunkData[] lake() {
        return snapshot((data, i, bx, bz) -> {
            boolean wet = bx >= 64 && bx < 192 && bz >= 64 && bz < 192;
            if (wet) {
                data.setColumn(i, 50, 1, 63, WATER_STATE, SKY, 0);
            } else {
                data.setColumn(i, 66, 0, LodChunkData.NO_FLUID, 0, SKY, 0);
            }
        });
    }

    @Test
    void waterIsATranslucentPlaneAboveItsFloor() {
        LodChunkData[] chunks = lake();
        for (int level = 0; level <= NativeLodMesher.MAX_LEVEL; level++) {
            LodMesh mesh = NativeLodMesher.mesh(0, 0, chunks, level, COLORS);
            List<Quad> all = quads(mesh);
            List<Quad> water = all.subList(mesh.opaqueQuads(), all.size());
            assertEquals(1, water.size(), "one merged water surface and no walls against the higher shore, level " + level);
            Quad surface = water.get(0);
            assertEquals(NativeLodMesher.NORMAL_UP, surface.normal());
            assertEquals(63, surface.minY());
            assertEquals(63, surface.maxY());
            assertEquals(LodMaterial.WATER, surface.material());
            assertEquals(NativeLodMesher.WATER_ALPHA, surface.alpha());
            assertEquals(WATER_RGB, surface.rgb());
            assertEquals(64, surface.minX());
            assertEquals(192, surface.maxX());
            for (Quad q : all.subList(0, mesh.opaqueQuads())) {
                assertEquals(0xFF, q.alpha(), "the floor and the shore are opaque");
                assertTrue(q.material() != LodMaterial.WATER);
            }
            Quad floor = all.stream().filter(q -> q.normal() == NativeLodMesher.NORMAL_UP && q.minY() == 50).findFirst().orElseThrow();
            assertEquals(PALETTE[1], floor.rgb());
            assertEquals(SKY - 13, floor.light() & 15, "sky light fades through 13 blocks of water");
            assertTrue(all.stream().anyMatch(q -> q.isWall() && q.minY() == 50 && q.maxY() == 66), "the shore's wall shows through the water");
        }
    }

    @Test
    void waterAgainstTheVoidIsClosedDownToItsFloor() {
        LodChunkData[] chunks = snapshot((data, i, bx, bz) -> {
            if (inSector(bx, bz)) {
                data.setColumn(i, 40, 1, 63, WATER_STATE, SKY, 0);
            }
        });
        LodMesh mesh = NativeLodMesher.mesh(0, 0, chunks, 1, COLORS);
        List<Quad> water = quads(mesh).subList(mesh.opaqueQuads(), mesh.quadCount());
        assertEquals(5, water.size(), "the surface and one wall per sector edge");
        for (Quad q : water) {
            if (q.isWall()) {
                assertEquals(40, q.minY());
                assertEquals(63, q.maxY());
            }
        }
    }

    @Test
    void landAboveTheWaterHidesItInCoarseCells() {
        // A 2-block-wide channel at 63 between land at 64: at level 3 every cell holds land, so no water is drawn.
        LodChunkData[] chunks = snapshot((data, i, bx, bz) -> {
            if (Math.floorMod(bx, 8) < 2) {
                data.setColumn(i, 55, 1, 63, WATER_STATE, SKY, 0);
            } else {
                data.setColumn(i, 64, 0, LodChunkData.NO_FLUID, 0, SKY, 0);
            }
        });
        assertTrue(NativeLodMesher.mesh(0, 0, chunks, 0, COLORS).waterQuads() > 0);
        assertEquals(0, NativeLodMesher.mesh(0, 0, chunks, 3, COLORS).waterQuads());
    }

    @Test
    void floorlessWaterGetsADarkStandInFloor() {
        LodChunkData[] chunks = snapshot((data, i, bx, bz) -> data.setColumn(i, LodChunkData.NO_SURFACE, 0, 63, WATER_STATE, SKY, 0));
        LodMesh mesh = NativeLodMesher.mesh(0, 0, chunks, 0, COLORS);
        Quad floor = quads(mesh).get(0);
        assertEquals(63 - NativeLodMesher.FLOORLESS_DEPTH, floor.minY());
        assertEquals(0, floor.light() & 15, "no sky light that deep");
        assertEquals(0xFF, floor.alpha());
        assertEquals(LodMaterial.UNKNOWN, floor.material(), "an opaque stand-in, not water: packs shade DH_BLOCK_WATER as water");
        assertEquals(1, mesh.waterQuads());
    }

    @Test
    void noTwoFacesShareAPlaneAndADirection() {
        // Crowns over a lake shore over stepped ground: every layer at once.
        LodChunkData[] chunks = snapshot((data, i, bx, bz) -> {
            boolean wet = bx >= 100 && bx < 140;
            int ground = 60 + Math.floorDiv(bz, 40);
            if (wet) {
                data.setColumn(i, ground - 6, 1, 63, WATER_STATE, SKY, 0);
            } else {
                data.setColumn(i, Math.max(64, ground), 0, LodChunkData.NO_FLUID, 0, SKY, 0);
            }
            if (Math.floorMod(bx * 5 + bz * 3, 7) < 5) {
                data.setCanopy(i, 70 + Math.floorMod(bx + bz, 4), 66, LEAVES_STATE, LodChunkData.NO_TINT, SKY);
            }
        });
        for (int level = 0; level <= NativeLodMesher.MAX_LEVEL; level++) {
            LodMesh mesh = assertSealedCanopy(chunks, -2, 5, level);
            List<Quad> all = quads(mesh);
            for (Quad q : all) {
                assertOutwardWinding(q);
            }
            assertNoCoplanarOverlap(all, "level " + level);
        }
    }

    // ------------------------------------------------------------------ fixtures

    static boolean inSector(int bx, int bz) {
        return bx >= 0 && bx < LodDataStore.SECTOR_BLOCKS && bz >= 0 && bz < LodDataStore.SECTOR_BLOCKS;
    }

    /** Fills column {@code i} of {@code data}, which lies at sector-local block {@code (bx, bz)}. */
    @FunctionalInterface
    interface ColumnFiller {
        void fill(LodChunkData data, int i, int bx, int bz);
    }

    /**
     * A sector snapshot (the sector and its one-chunk border, sector-local blocks -16..271) filled column by column.
     * Chunks without any surface are left out, as the store leaves out chunks it has no data for.
     */
    static LodChunkData[] snapshot(ColumnFiller filler) {
        LodChunkData[] chunks = new LodChunkData[LodDataStore.SNAPSHOT_SPAN * LodDataStore.SNAPSHOT_SPAN];
        for (int cz = -1; cz <= LodDataStore.SECTOR_CHUNKS; cz++) {
            for (int cx = -1; cx <= LodDataStore.SECTOR_CHUNKS; cx++) {
                LodChunkData data = new LodChunkData(cx, cz, 1L);
                boolean any = false;
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        int i = LodChunkData.index(x, z);
                        filler.fill(data, i, cx * 16 + x, cz * 16 + z);
                        any |= data.hasSurface(i);
                    }
                }
                if (any) {
                    chunks[LodDataStore.snapshotIndex(cx, cz)] = data;
                }
            }
        }
        return chunks;
    }

    /** Dry ground whose column {@code (bx, bz)} has its top at {@code height(bx, bz)} ({@link #NONE}: no surface). */
    static LodChunkData[] ground(IntBinaryOperator height, IntBinaryOperator state) {
        return snapshot((data, i, bx, bz) -> {
            int h = height.applyAsInt(bx, bz);
            if (h != NONE) {
                data.setColumn(i, h, state.applyAsInt(bx, bz), LodChunkData.NO_FLUID, 0, SKY, 0);
            }
        });
    }

    /** One quad read back from the vertex bytes. */
    record Quad(int[] x, int[] y, int[] z, int rgb, int alpha, int light, int material, int normal) {
        int minX() {
            return Math.min(Math.min(x[0], x[1]), Math.min(x[2], x[3]));
        }

        int maxX() {
            return Math.max(Math.max(x[0], x[1]), Math.max(x[2], x[3]));
        }

        int minY() {
            return Math.min(Math.min(y[0], y[1]), Math.min(y[2], y[3]));
        }

        int maxY() {
            return Math.max(Math.max(y[0], y[1]), Math.max(y[2], y[3]));
        }

        int minZ() {
            return Math.min(Math.min(z[0], z[1]), Math.min(z[2], z[3]));
        }

        int maxZ() {
            return Math.max(Math.max(z[0], z[1]), Math.max(z[2], z[3]));
        }

        boolean isWall() {
            return normal >= NativeLodMesher.NORMAL_NORTH;
        }

        @Override
        public String toString() {
            return "Quad[x " + minX() + ".." + maxX() + " y " + minY() + ".." + maxY() + " z " + minZ() + ".." + maxZ() + " normal "
                    + normal + " material " + material + "]";
        }
    }

    /** Every quad of {@code mesh}; checks the per-vertex invariants on the way: one colour, light, material and normal per quad. */
    static List<Quad> quads(LodMesh mesh) {
        ByteBuffer b = mesh.vertices();
        assertEquals(mesh.byteSize(), b.remaining());
        List<Quad> out = new ArrayList<>(mesh.quadCount());
        for (int q = 0; q < mesh.quadCount(); q++) {
            int[] x = new int[4];
            int[] y = new int[4];
            int[] z = new int[4];
            int rgb = 0;
            int alpha = 0;
            int light = 0;
            int material = 0;
            int normal = 0;
            for (int i = 0; i < 4; i++) {
                int p = b.position() + q * NativeLodMesher.QUAD_BYTES + i * NativeLodMesher.VERTEX_BYTES;
                x[i] = b.getShort(p + NativeLodMesher.OFFSET_POSITION) & 0xFFFF;
                y[i] = (b.getShort(p + NativeLodMesher.OFFSET_POSITION + 2) & 0xFFFF) - NativeLodMesher.Y_BIAS;
                z[i] = b.getShort(p + NativeLodMesher.OFFSET_POSITION + 4) & 0xFFFF;
                int meta = b.getShort(p + NativeLodMesher.OFFSET_META) & 0xFFFF;
                assertEquals(0, meta >> 8, "no micro offset");
                int c = p + NativeLodMesher.OFFSET_COLOR;
                int vertexRgb = (b.get(c) & 0xFF) << 16 | (b.get(c + 1) & 0xFF) << 8 | b.get(c + 2) & 0xFF;
                int vertexAlpha = b.get(c + 3) & 0xFF;
                int vertexMaterial = b.get(p + NativeLodMesher.OFFSET_MATERIAL);
                int vertexNormal = b.get(p + NativeLodMesher.OFFSET_NORMAL);
                assertEquals(0, b.get(p + NativeLodMesher.OFFSET_NORMAL + 1), "padding");
                assertEquals(0, b.get(p + NativeLodMesher.OFFSET_NORMAL + 2), "padding");
                assertTrue(LodMaterial.isValid(vertexMaterial));
                assertTrue(vertexNormal >= 0 && vertexNormal < 6);
                if (i == 0) {
                    rgb = vertexRgb;
                    alpha = vertexAlpha;
                    light = meta;
                    material = vertexMaterial;
                    normal = vertexNormal;
                } else {
                    assertEquals(rgb, vertexRgb, "one colour per quad");
                    assertEquals(alpha, vertexAlpha);
                    assertEquals(light, meta);
                    assertEquals(material, vertexMaterial);
                    assertEquals(normal, vertexNormal);
                }
            }
            out.add(new Quad(x, y, z, rgb, alpha, light, material, normal));
        }
        return out;
    }

    private static byte[] bytes(LodMesh mesh) {
        ByteBuffer b = mesh.vertices().duplicate();
        byte[] out = new byte[b.remaining()];
        b.get(out);
        return out;
    }

    /** Both triangles (0, 1, 2) and (2, 3, 0) face along the stored normal: counter-clockwise seen from outside. */
    private static void assertOutwardWinding(Quad q) {
        assertTrue(facing(q, 0, 1, 2) > 0 && facing(q, 2, 3, 0) > 0, "winding of " + q);
    }

    private static long facing(Quad q, int a, int b, int c) {
        int[] n = NativeLodMesher.normalVector(q.normal());
        long ax = q.x()[b] - q.x()[a];
        long ay = q.y()[b] - q.y()[a];
        long az = q.z()[b] - q.z()[a];
        long bx = q.x()[c] - q.x()[a];
        long by = q.y()[c] - q.y()[a];
        long bz = q.z()[c] - q.z()[a];
        return (ay * bz - az * by) * n[0] + (az * bx - ax * bz) * n[1] + (ax * by - ay * bx) * n[2];
    }

    /**
     * No two faces lie in one plane, face the same way and overlap: every unit square of every face is keyed by its
     * direction, its plane and its position, and no key may repeat. Linear in the faces' area, so it runs on full
     * level-0 meshes of a few hundred thousand quads.
     */
    private static void assertNoCoplanarOverlap(List<Quad> quads, String where) {
        LongOpenHashSet squares = new LongOpenHashSet();
        for (Quad q : quads) {
            int[] n = NativeLodMesher.normalVector(q.normal());
            if (n[1] != 0) {
                for (int x = q.minX(); x < q.maxX(); x++) {
                    for (int z = q.minZ(); z < q.maxZ(); z++) {
                        addSquare(squares, q, q.minY(), x, z, where);
                    }
                }
            } else if (n[0] != 0) {
                for (int y = q.minY(); y < q.maxY(); y++) {
                    for (int z = q.minZ(); z < q.maxZ(); z++) {
                        addSquare(squares, q, q.minX(), y, z, where);
                    }
                }
            } else {
                for (int x = q.minX(); x < q.maxX(); x++) {
                    for (int y = q.minY(); y < q.maxY(); y++) {
                        addSquare(squares, q, q.minZ(), x, y, where);
                    }
                }
            }
        }
    }

    private static void addSquare(LongOpenHashSet squares, Quad q, int plane, int a, int b, String where) {
        long key = (long) q.normal() << 48 | (long) (plane + 0x2000) << 32 | (long) (a + 0x2000) << 16 | b + 0x2000;
        if (!squares.add(key)) {
            fail(where + ": " + q + " overlaps another face of its plane and direction at " + a + ", " + b);
        }
    }

    /**
     * Meshes {@code chunks} and checks the canopy faces (material {@link LodMaterial#LEAVES}) against the slabs the
     * mesher chose ({@link NativeLodMesher.Grid}): each canopy cell's top covered exactly once at its height, each
     * floating slab's underside exactly once, and the walls on each side of each slab covering exactly the part of its
     * side the neighbouring slab leaves open (inside the sector) or its whole side (next to no canopy, across the
     * sector edge), each block once. Every canopy face has its cell's colour and light.
     */
    static LodMesh assertSealedCanopy(LodChunkData[] chunks, int sectorX, int sectorZ, int level) {
        NativeLodMesher.Grid g = NativeLodMesher.Grid.build(sectorX, sectorZ, chunks, level, COLORS);
        LodMesh mesh = NativeLodMesher.mesh(sectorX, sectorZ, chunks, level, COLORS);
        int size = g.size;
        int cell = g.cell;
        int[] topCover = new int[size * size];
        int[] underCover = new int[size * size];
        long[][] sides = new long[4][size * size];
        for (Quad q : quads(mesh)) {
            if (q.material() != LodMaterial.LEAVES) {
                continue;
            }
            if (!q.isWall()) {
                boolean up = q.normal() == NativeLodMesher.NORMAL_UP;
                int[] expected = up ? g.canopyTop : g.canopyUnderside;
                int[] cover = up ? topCover : underCover;
                for (int cz = q.minZ() / cell; cz < q.maxZ() / cell; cz++) {
                    for (int cx = q.minX() / cell; cx < q.maxX() / cell; cx++) {
                        int c = cz * size + cx;
                        assertEquals(expected[c], q.minY(), (up ? "canopy top" : "underside") + " of cell " + cx + ", " + cz);
                        assertCanopyLook(g, c, q);
                        cover[c]++;
                    }
                }
                continue;
            }
            NativeLodMesher.Side side = sideOf(q);
            int line = side.alongX ? q.minZ() : q.minX();
            int owner = switch (side) {
                case NORTH, WEST -> line / cell;
                case SOUTH, EAST -> line / cell - 1;
            };
            int from = side.alongX ? q.minX() : q.minZ();
            int to = side.alongX ? q.maxX() : q.maxZ();
            for (int a = from / cell; a < to / cell; a++) {
                int c = side.alongX ? owner * size + a : a * size + owner;
                assertTrue(g.canopyTop[c] != NativeLodMesher.EMPTY, side + " canopy wall on a cell without canopy: " + q);
                assertCanopyLook(g, c, q);
                for (int y = q.minY(); y < q.maxY(); y++) {
                    int bit = y - g.canopyBottom[c];
                    assertTrue(bit >= 0 && y < g.canopyTop[c], side + " canopy wall outside its slab: " + q);
                    assertEquals(0L, sides[side.ordinal()][c] & 1L << bit, side + " wall block " + y + " twice: " + q);
                    sides[side.ordinal()][c] |= 1L << bit;
                }
            }
        }
        for (int cz = 0; cz < size; cz++) {
            for (int cx = 0; cx < size; cx++) {
                int c = cz * size + cx;
                String where = "cell " + cx + ", " + cz + " at level " + level;
                int top = g.canopyTop[c];
                assertEquals(top == NativeLodMesher.EMPTY ? 0 : 1, topCover[c], "canopy top coverage of " + where);
                assertEquals(g.canopyUnderside[c] == NativeLodMesher.EMPTY ? 0 : 1, underCover[c], "underside coverage of " + where);
                if (top == NativeLodMesher.EMPTY) {
                    continue;
                }
                int bottom = g.canopyBottom[c];
                assertTrue(bottom < top, "a slab has thickness: " + where);
                for (NativeLodMesher.Side side : NativeLodMesher.Side.VALUES) {
                    int nx = cx + side.dx;
                    int nz = cz + side.dz;
                    int n = nx >= 0 && nx < size && nz >= 0 && nz < size ? nz * size + nx : -1;
                    long expected = 0;
                    for (int y = bottom; y < top; y++) {
                        boolean covered = n >= 0 && g.canopyTop[n] != NativeLodMesher.EMPTY && y >= g.canopyBottom[n] && y < g.canopyTop[n];
                        if (!covered) {
                            expected |= 1L << y - bottom;
                        }
                    }
                    assertEquals(expected, sides[side.ordinal()][c], side + " canopy walls of " + where);
                }
            }
        }
        return mesh;
    }

    private static void assertCanopyLook(NativeLodMesher.Grid g, int c, Quad q) {
        assertEquals(g.canopyColor[c], q.rgb(), "canopy colour: " + q);
        assertEquals(g.canopyLight[c], q.light(), "canopy light: " + q);
        assertEquals(0xFF, q.alpha(), "canopies are opaque: " + q);
    }

    /** Index {@code dz * cell + dx} of the cell's highest column, the first in row order among equals; -1 for none. */
    private static int highest(IntBinaryOperator height, int cell, int cellX, int cellZ) {
        int best = NONE;
        int bestIndex = -1;
        for (int dz = 0; dz < cell; dz++) {
            for (int dx = 0; dx < cell; dx++) {
                int h = height.applyAsInt(cellX * cell + dx, cellZ * cell + dz);
                if (h != NONE && h > best) {
                    best = h;
                    bestIndex = dz * cell + dx;
                }
            }
        }
        return bestIndex;
    }

    /** The lowest column across the sector edge on {@code side} along cell {@code k}; {@link #NONE} if all are void. */
    private static int borderLowest(IntBinaryOperator height, int cell, NativeLodMesher.Side side, int k) {
        int lowest = NONE;
        for (int d = 0; d < cell; d++) {
            int along = k * cell + d;
            int bx = side.alongX ? along : side.dx < 0 ? -1 : LodDataStore.SECTOR_BLOCKS;
            int bz = side.alongX ? side.dz < 0 ? -1 : LodDataStore.SECTOR_BLOCKS : along;
            int h = height.applyAsInt(bx, bz);
            if (h != NONE && (lowest == NONE || h < lowest)) {
                lowest = h;
            }
        }
        return lowest;
    }

    private static NativeLodMesher.Side sideOf(Quad q) {
        for (NativeLodMesher.Side side : NativeLodMesher.Side.VALUES) {
            if (side.normal == q.normal()) {
                return side;
            }
        }
        throw new AssertionError("not a wall normal: " + q);
    }

    /**
     * Meshes dry {@code chunks} at {@code level} and checks every face against the surface {@code height} and
     * {@code state} define: each cell's top is covered by exactly one top quad at its height and colour, each cell
     * edge with a lower neighbour carries exactly one wall from the cell's top exactly down to the neighbour, no other
     * wall exists, every quad winds outwards, and the mesh's y range is its vertices' range.
     */
    static LodMesh assertSealedGround(LodChunkData[] chunks, IntBinaryOperator height, IntBinaryOperator state, int level) {
        LodMesh mesh = NativeLodMesher.mesh(3, -2, chunks, level, COLORS);
        assertEquals(level, mesh.level());
        assertEquals(0, mesh.waterQuads(), "dry ground has no water");
        int cell = 1 << level;
        int size = LodDataStore.SECTOR_BLOCKS >> level;
        int[] tops = new int[size * size];
        int[] colors = new int[size * size];
        for (int cz = 0; cz < size; cz++) {
            for (int cx = 0; cx < size; cx++) {
                int c = cz * size + cx;
                int best = highest(height, cell, cx, cz);
                int bx = cx * cell + best % cell;
                int bz = cz * cell + best / cell;
                tops[c] = best < 0 ? NONE : height.applyAsInt(bx, bz);
                colors[c] = best < 0 ? 0 : COLORS.baseColor(state.applyAsInt(bx, bz));
            }
        }

        int[] covered = new int[size * size];
        int[][] wallBottom = new int[4][size * size];
        int[][] wallTop = new int[4][size * size];
        for (int s = 0; s < 4; s++) {
            Arrays.fill(wallBottom[s], NONE);
            Arrays.fill(wallTop[s], NONE);
        }
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (Quad q : quads(mesh)) {
            assertOutwardWinding(q);
            assertEquals(SKY, q.light(), "sky light, no block light");
            assertEquals(0xFF, q.alpha(), "opaque");
            minY = Math.min(minY, q.minY());
            maxY = Math.max(maxY, q.maxY());
            if (!q.isWall()) {
                assertEquals(NativeLodMesher.NORMAL_UP, q.normal(), "only upward tops");
                assertEquals(q.minY(), q.maxY(), "a top is flat");
                assertEquals(0, q.minX() % cell);
                assertEquals(0, q.maxX() % cell);
                assertEquals(0, q.minZ() % cell);
                assertEquals(0, q.maxZ() % cell);
                assertTrue(q.maxX() > q.minX() && q.maxZ() > q.minZ());
                for (int cz = q.minZ() / cell; cz < q.maxZ() / cell; cz++) {
                    for (int cx = q.minX() / cell; cx < q.maxX() / cell; cx++) {
                        int c = cz * size + cx;
                        assertEquals(tops[c], q.minY(), "top height of cell " + cx + ", " + cz);
                        assertEquals(colors[c], q.rgb(), "top colour of cell " + cx + ", " + cz);
                        covered[c]++;
                    }
                }
                continue;
            }
            NativeLodMesher.Side side = sideOf(q);
            int line = side.alongX ? q.minZ() : q.minX();
            assertEquals(line, side.alongX ? q.maxZ() : q.maxX(), "a wall is vertical");
            assertEquals(0, line % cell);
            int owner = switch (side) {
                case NORTH, WEST -> line / cell;
                case SOUTH, EAST -> line / cell - 1;
            };
            int from = side.alongX ? q.minX() : q.minZ();
            int to = side.alongX ? q.maxX() : q.maxZ();
            assertEquals(0, from % cell);
            assertEquals(0, to % cell);
            assertTrue(to > from && q.maxY() > q.minY());
            for (int a = from / cell; a < to / cell; a++) {
                int c = side.alongX ? owner * size + a : a * size + owner;
                assertEquals(NONE, wallTop[side.ordinal()][c], "one " + side + " wall per cell edge");
                assertEquals(colors[c], q.rgb(), side + " wall colour");
                wallBottom[side.ordinal()][c] = q.minY();
                wallTop[side.ordinal()][c] = q.maxY();
            }
        }

        for (int cz = 0; cz < size; cz++) {
            for (int cx = 0; cx < size; cx++) {
                int c = cz * size + cx;
                int top = tops[c];
                assertEquals(top == NONE ? 0 : 1, covered[c], "top coverage of cell " + cx + ", " + cz + " at level " + level);
                for (NativeLodMesher.Side side : NativeLodMesher.Side.VALUES) {
                    int expectedBottom = NONE;
                    if (top != NONE) {
                        int nx = cx + side.dx;
                        int nz = cz + side.dz;
                        int neighbour = nx >= 0 && nx < size && nz >= 0 && nz < size
                                ? tops[nz * size + nx]
                                : borderLowest(height, cell, side, side.alongX ? cx : cz);
                        int bottom = neighbour == NONE ? top - NativeLodMesher.VOID_SKIRT : neighbour;
                        if (bottom < top) {
                            expectedBottom = bottom;
                        }
                    }
                    String where = side + " wall of cell " + cx + ", " + cz + " at level " + level;
                    assertEquals(expectedBottom, wallBottom[side.ordinal()][c], where);
                    assertEquals(expectedBottom == NONE ? NONE : top, wallTop[side.ordinal()][c], where);
                }
            }
        }
        if (mesh.quadCount() > 0) {
            assertEquals(minY, mesh.minY());
            assertEquals(maxY, mesh.maxY());
        }
        return mesh;
    }
}
