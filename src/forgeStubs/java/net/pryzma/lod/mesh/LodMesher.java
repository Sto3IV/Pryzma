package net.pryzma.lod.mesh;

import net.pryzma.lod.config.LodConfig;
import net.pryzma.lod.data.LodChunk;
import net.pryzma.lod.data.LodColumn;
import net.pryzma.lod.data.LodMaterial;
import net.pryzma.lod.data.LodRegion;

/**
 * 2.5D Greedy Mesher for LOD terrain and fluid layers.
 * Groups horizontal runs of matching material/elevation and builds vertical skirts.
 * Vertices are positioned relative to the region origin [0..256] for precision.
 */
public final class LodMesher {

    public record MeshResult(LodMeshBuilder opaqueMesh, LodMeshBuilder waterMesh) {
    }

    public static MeshResult buildRegionMesh(LodRegion region) {
        LodMeshBuilder opaque = new LodMeshBuilder(4096);
        LodMeshBuilder water = new LodMeshBuilder(1024);

        boolean skirts = LodConfig.isEnableSkirts();
        boolean renderWater = LodConfig.isRenderWater();
        int regionMinX = region.getMinBlockX();
        int regionMinZ = region.getMinBlockZ();

        for (LodChunk chunk : region.getChunks().values()) {
            meshChunk(chunk, regionMinX, regionMinZ, opaque, water, skirts, renderWater);
        }

        return new MeshResult(opaque, water);
    }

    private static void meshChunk(LodChunk chunk, int regionMinX, int regionMinZ,
                                  LodMeshBuilder opaque, LodMeshBuilder water,
                                  boolean skirts, boolean renderWater) {
        int baseBlockX = chunk.chunkX << 4;
        int baseBlockZ = chunk.chunkZ << 4;
        int relChunkX = baseBlockX - regionMinX;
        int relChunkZ = baseBlockZ - regionMinZ;

        boolean[] visited = new boolean[256];

        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int idx = (z << 4) | x;
                if (visited[idx]) {
                    continue;
                }

                long col = chunk.getColumn(x, z);
                int ty = LodColumn.getTerrainY(col);
                int mat = LodColumn.getMaterial(col);
                int sky = LodColumn.getSkyLight(col);
                int blk = LodColumn.getBlockLight(col);
                int color = LodMaterial.getBaseColor(mat) | 0xFF000000;

                // Greedy expansion along X-axis
                int endX = x + 1;
                while (endX < 16) {
                    int nextIdx = (z << 4) | endX;
                    if (visited[nextIdx]) break;
                    long nextCol = chunk.getColumn(endX, z);
                    if (LodColumn.getTerrainY(nextCol) != ty ||
                            LodColumn.getMaterial(nextCol) != mat ||
                            LodColumn.getSkyLight(nextCol) != sky ||
                            LodColumn.getBlockLight(nextCol) != blk) {
                        break;
                    }
                    endX++;
                }

                for (int mx = x; mx < endX; mx++) {
                    visited[(z << 4) | mx] = true;
                }

                float fx0 = relChunkX + x;
                float fx1 = relChunkX + endX;
                float fz0 = relChunkZ + z;
                float fz1 = relChunkZ + z + 1;
                float fy = ty;

                float u0 = 0.0f;
                float v0 = 0.0f;
                float u1 = (float) (endX - x);
                float v1 = 1.0f;

                // Horizontal top terrain quad (normal: 0, 1, 0)
                opaque.addQuad(
                        fx0, fy, fz0, u0, v0,
                        fx0, fy, fz1, u0, v1,
                        fx1, fy, fz1, u1, v1,
                        fx1, fy, fz0, u1, v0,
                        0.0f, 1.0f, 0.0f,
                        color, sky, blk
                );

                // Vertical skirts along borders to hide gaps
                if (skirts) {
                    addSkirts(chunk, x, z, endX, ty, relChunkX, relChunkZ, color, sky, blk, opaque);
                }

                // Water layer
                if (renderWater && LodColumn.hasWater(col)) {
                    int wy = LodColumn.getWaterY(col);
                    if (wy > -64) {
                        int waterColor = 0x882A59A8; // translucent water
                        water.addQuad(
                                fx0, (float) wy + 0.9f, fz0, u0, v0,
                                fx0, (float) wy + 0.9f, fz1, u0, v1,
                                fx1, (float) wy + 0.9f, fz1, u1, v1,
                                fx1, (float) wy + 0.9f, fz0, u1, v0,
                                0.0f, 1.0f, 0.0f,
                                waterColor, sky, blk
                        );
                    }
                }

                x = endX - 1; // Advance greedy loop
            }
        }
    }

    private static void addSkirts(LodChunk chunk, int startX, int z, int endX, int ty,
                                  int relChunkX, int relChunkZ, int color, int sky, int blk,
                                  LodMeshBuilder opaque) {
        // South edge (z + 1)
        if (z < 15) {
            for (int x = startX; x < endX; x++) {
                long neighborCol = chunk.getColumn(x, z + 1);
                int nty = LodColumn.getTerrainY(neighborCol);
                if (nty < ty) {
                    float fx0 = relChunkX + x;
                    float fx1 = relChunkX + x + 1;
                    float fz = relChunkZ + z + 1;
                    float vLen = (float) (ty - nty);
                    opaque.addQuad(
                            fx0, ty, fz, 0.0f, 0.0f,
                            fx1, ty, fz, 1.0f, 0.0f,
                            fx1, nty, fz, 1.0f, vLen,
                            fx0, nty, fz, 0.0f, vLen,
                            0.0f, 0.0f, 1.0f, // normal facing south
                            color, sky, blk
                    );
                }
            }
        }

        // North edge (z - 1)
        if (z > 0) {
            for (int x = startX; x < endX; x++) {
                long neighborCol = chunk.getColumn(x, z - 1);
                int nty = LodColumn.getTerrainY(neighborCol);
                if (nty < ty) {
                    float fx0 = relChunkX + x;
                    float fx1 = relChunkX + x + 1;
                    float fz = relChunkZ + z;
                    float vLen = (float) (ty - nty);
                    opaque.addQuad(
                            fx1, ty, fz, 1.0f, 0.0f,
                            fx0, ty, fz, 0.0f, 0.0f,
                            fx0, nty, fz, 0.0f, vLen,
                            fx1, nty, fz, 1.0f, vLen,
                            0.0f, 0.0f, -1.0f, // normal facing north
                            color, sky, blk
                    );
                }
            }
        }

        // West edge (startX - 1)
        if (startX > 0) {
            long neighborCol = chunk.getColumn(startX - 1, z);
            int nty = LodColumn.getTerrainY(neighborCol);
            if (nty < ty) {
                float fx = relChunkX + startX;
                float fz0 = relChunkZ + z;
                float fz1 = relChunkZ + z + 1;
                float vLen = (float) (ty - nty);
                opaque.addQuad(
                        fx, ty, fz1, 1.0f, 0.0f,
                        fx, ty, fz0, 0.0f, 0.0f,
                        fx, nty, fz0, 0.0f, vLen,
                        fx, nty, fz1, 1.0f, vLen,
                        -1.0f, 0.0f, 0.0f, // normal facing west
                        color, sky, blk
                );
            }
        }

        // East edge (endX)
        if (endX < 16) {
            long neighborCol = chunk.getColumn(endX, z);
            int nty = LodColumn.getTerrainY(neighborCol);
            if (nty < ty) {
                float fx = relChunkX + endX;
                float fz0 = relChunkZ + z;
                float fz1 = relChunkZ + z + 1;
                float vLen = (float) (ty - nty);
                opaque.addQuad(
                        fx, ty, fz0, 0.0f, 0.0f,
                        fx, ty, fz1, 1.0f, 0.0f,
                        fx, nty, fz1, 1.0f, vLen,
                        fx, nty, fz0, 0.0f, vLen,
                        1.0f, 0.0f, 0.0f, // normal facing east
                        color, sky, blk
                );
            }
        }
    }
}
