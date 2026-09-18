package net.pryzma.lod;

import net.pryzma.lod.data.LodChunk;
import net.pryzma.lod.data.LodColumn;
import net.pryzma.lod.data.LodMaterial;
import net.pryzma.lod.data.LodRegion;
import net.pryzma.lod.data.LodWorldStorage;
import net.pryzma.lod.intake.LodIntake;
import net.pryzma.lod.mesh.LodMesher;
import net.pryzma.neoforge.ClientLevelTransformer;
import net.pryzma.neoforge.LevelRendererTransformer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class LodSubsystemTest {

    @BeforeEach
    void setup() {
        LodWorldStorage.get().clear();
    }

    @Test
    void testLodColumnPacking() {
        long packed = LodColumn.pack(64, -128, LodMaterial.GRASS, 42, 15, 0, 0);
        assertEquals(64, LodColumn.getTerrainY(packed));
        assertEquals(-128, LodColumn.getWaterY(packed));
        assertEquals(LodMaterial.GRASS, LodColumn.getMaterial(packed));
        assertEquals(42, LodColumn.getBiomeId(packed));
        assertEquals(15, LodColumn.getSkyLight(packed));
        assertEquals(0, LodColumn.getBlockLight(packed));
        assertFalse(LodColumn.hasWater(packed));
    }

    @Test
    void testLodColumnNegativeElevationAndWater() {
        long packed = LodColumn.pack(-45, 62, LodMaterial.WATER, 10, 14, 8, LodColumn.FLAG_HAS_WATER);
        assertEquals(-45, LodColumn.getTerrainY(packed));
        assertEquals(62, LodColumn.getWaterY(packed));
        assertEquals(LodMaterial.WATER, LodColumn.getMaterial(packed));
        assertEquals(10, LodColumn.getBiomeId(packed));
        assertEquals(14, LodColumn.getSkyLight(packed));
        assertEquals(8, LodColumn.getBlockLight(packed));
        assertTrue(LodColumn.hasWater(packed));
    }

    @Test
    void testLodChunkBoundsAndDirty() {
        LodChunk chunk = new LodChunk(2, -3);
        assertEquals(2, chunk.chunkX);
        assertEquals(-3, chunk.chunkZ);

        long col1 = LodColumn.pack(10, -128, LodMaterial.DIRT, 1, 15, 0, 0);
        long col2 = LodColumn.pack(120, -128, LodMaterial.STONE, 1, 15, 0, 0);

        chunk.setColumn(0, 0, col1);
        chunk.setColumn(15, 15, col2);

        assertEquals(10, chunk.getMinY());
        assertEquals(120, chunk.getMaxY());
        assertTrue(chunk.isDirty());

        chunk.clearDirty();
        assertFalse(chunk.isDirty());
    }

    @Test
    void testLodRegionCoordinates() {
        // Chunk (35, -20) belongs to Region (2, -2)
        int rx = LodRegion.chunkToRegionCoord(35);
        int rz = LodRegion.chunkToRegionCoord(-20);
        assertEquals(2, rx);
        assertEquals(-2, rz);

        LodRegion region = new LodRegion(rx, rz);
        assertEquals(512, region.getMinBlockX()); // (2 * 16) * 16 = 512
        assertEquals(-512, region.getMinBlockZ()); // (-2 * 16) * 16 = -512
    }

    @Test
    void testLodGreedyMeshingReduction() {
        LodRegion region = new LodRegion(0, 0);
        LodChunk chunk = new LodChunk(0, 0);

        // Fill entire 16x16 chunk with identical flat grass terrain at y=70
        long flatCol = LodColumn.pack(70, -128, LodMaterial.GRASS, 1, 15, 0, 0);
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                chunk.setColumn(x, z, flatCol);
            }
        }
        region.putChunk(chunk);

        LodMesher.MeshResult result = LodMesher.buildRegionMesh(region);

        // Without greedy meshing, 256 columns would need 256 * 6 = 1536 vertices.
        // With greedy meshing along X, 16 full-row quads = 16 * 6 = 96 vertices!
        assertTrue(result.opaqueMesh().getVertexCount() <= 96,
                "Greedy meshing should compress flat chunk to 96 vertices, actual: " + result.opaqueMesh().getVertexCount());
    }

    @Test
    void testLodIntakePipeline() throws InterruptedException {
        short[] heights = new short[256];
        for (int i = 0; i < 256; i++) {
            heights[i] = (short) (64 + (i % 16));
        }
        LodIntake.submit(10, 20, heights, null, null, 5, null);

        // Allow async executor to drain
        Thread.sleep(100);

        LodRegion region = LodWorldStorage.get().getRegion(
                LodRegion.chunkToRegionCoord(10),
                LodRegion.chunkToRegionCoord(20)
        );

        assertNotNull(region, "Region should be created in LodWorldStorage by LodIntake");
        LodChunk chunk = region.getChunk(10, 20);
        assertNotNull(chunk, "Chunk (10, 20) should be present in region");
        assertEquals(64, LodColumn.getTerrainY(chunk.getColumn(0, 0)));
        assertEquals(79, LodColumn.getTerrainY(chunk.getColumn(15, 0)));
    }

    @Test
    void testLodGreedyMeshingWithSkirts() {
        LodRegion region = new LodRegion(0, 0);
        LodChunk chunk = new LodChunk(0, 0);

        // Half chunk at Y=64, half at Y=70 (forming a 6-block vertical cliff)
        long lowCol = LodColumn.pack(64, -128, LodMaterial.STONE, 1, 15, 0, 0);
        long highCol = LodColumn.pack(70, -128, LodMaterial.GRASS, 1, 15, 0, 0);

        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 8; x++) {
                chunk.setColumn(x, z, lowCol);
            }
            for (int x = 8; x < 16; x++) {
                chunk.setColumn(x, z, highCol);
            }
        }
        region.putChunk(chunk);

        LodMesher.MeshResult result = LodMesher.buildRegionMesh(region);
        assertNotNull(result);
        assertNotNull(result.opaqueMesh());
        assertTrue(result.opaqueMesh().getVertexCount() > 0, "Mesh should contain vertices including cliff skirts");
    }

    @Test
    void testClientLevelTransformerLodHook() {
        org.objectweb.asm.tree.ClassNode node = new org.objectweb.asm.tree.ClassNode();
        node.name = "net/minecraft/client/multiplayer/ClientLevel";
        org.objectweb.asm.tree.MethodNode method = new org.objectweb.asm.tree.MethodNode(
                org.objectweb.asm.Opcodes.ACC_PUBLIC,
                "onChunkLoaded",
                "(Lnet/minecraft/world/level/ChunkPos;)V",
                null,
                null
        );
        method.instructions.add(new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.RETURN));
        node.methods.add(method);

        assertTrue(ClientLevelTransformer.injectLodIntake(node), "Should inject LodIntake hook into onChunkLoaded");
        assertFalse(ClientLevelTransformer.injectLodIntake(node), "Subsequent injection should be idempotent (false)");
    }

    @Test
    void testLevelRendererTransformerResetHook() {
        org.objectweb.asm.tree.ClassNode node = new org.objectweb.asm.tree.ClassNode();
        node.name = "net/minecraft/client/renderer/LevelRenderer";
        org.objectweb.asm.tree.MethodNode method = new org.objectweb.asm.tree.MethodNode(
                org.objectweb.asm.Opcodes.ACC_PUBLIC,
                "setLevel",
                "(Lnet/minecraft/client/multiplayer/ClientLevel;)V",
                null,
                null
        );
        method.instructions.add(new org.objectweb.asm.tree.InsnNode(org.objectweb.asm.Opcodes.RETURN));
        node.methods.add(method);

        assertTrue(LevelRendererTransformer.inject(node), "Should inject LodWorldRenderer.reset into setLevel");
    }

    @Test
    void testLevelRendererTransformerRenderSectionLayerHook() throws Exception {
        try (var in = getClass().getResourceAsStream("/srg/net/minecraft/client/renderer/LevelRenderer.class")) {
            if (in != null) {
                var cr = new org.objectweb.asm.ClassReader(in);
                var node = new org.objectweb.asm.tree.ClassNode();
                cr.accept(node, 0);

                assertTrue(LevelRendererTransformer.inject(node), "LevelRendererTransformer must inject into LevelRenderer");
                var analyzer = new org.objectweb.asm.tree.analysis.Analyzer<>(new org.objectweb.asm.tree.analysis.BasicVerifier());
                for (var m : node.methods) {
                    try {
                        analyzer.analyze(node.name, m);
                    } catch (org.objectweb.asm.tree.analysis.AnalyzerException e) {
                        throw new AssertionError(node.name + " " + m.name + m.desc, e);
                    }
                }

                var rsl = node.methods.stream()
                        .filter(m -> "renderSectionLayer".equals(m.name) && m.desc.startsWith("(Lnet/minecraft/client/renderer/RenderType;"))
                        .findFirst()
                        .orElseThrow();
                boolean callsLodRender = false;
                for (var insn : rsl.instructions) {
                    if (insn instanceof org.objectweb.asm.tree.MethodInsnNode mi
                            && "net/pryzma/lod/render/LodWorldRenderer".equals(mi.owner)
                            && "renderSectionLayer".equals(mi.name)) {
                        callsLodRender = true;
                        break;
                    }
                }
                assertTrue(callsLodRender, "renderSectionLayer must invoke LodWorldRenderer.renderSectionLayer");
            }
        }
    }
}

