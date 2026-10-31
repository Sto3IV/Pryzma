package net.pryzma.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;

import net.minecraft.client.CloudStatus;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.pryzma.PryzmaConfig;

/**
 * High-performance infinite cloud meshing engine and visual seam-free renderer.
 * <p>
 * Fixes the vanilla grid artifact where vertical wall edges share the exact same Y plane
 * as the bottom/top faces, causing bright lines across every 8x8 cell on modern GPUs.
 * Slices are inset by {@link #WALL_EPSILON} so the horizontal faces strictly occlude them.
 * In addition, when Infinite Clouds is enabled, extends cloud rendering to a 4.6 km circular horizon
 * with seamless LOD and atmospheric horizon fog coordination.
 */
public final class PrCloudRenderer {
    /**
     * Vertical inset epsilon in cloud-mesh units (0.02F = 2.0 cm in world space).
     * Strictly occludes vertical wall slices behind the bottom/top faces to eliminate Z-fighting
     * and dotted seam bleeding across all view angles and distances.
     */
    public static final float WALL_EPSILON = 0.02F;

    /** Near 3D Fancy volumetric radius (in cells of 8 units = 96 blocks; 16 cells = 1536 blocks). */
    public static final int NEAR_CELL_MIN = -16;
    public static final int NEAR_CELL_MAX = 16;

    /** Far horizon radius in cells (48 cells * 8 units * 12 blocks = 4608 blocks = 288 chunks). */
    public static final int FAR_CELL_RADIUS = 48;
    public static final int FAR_CELL_RADIUS_SQ = FAR_CELL_RADIUS * FAR_CELL_RADIUS;

    private static volatile boolean dirty = false;
    private static float savedFogStart;
    private static float savedFogEnd;
    private static boolean fogExtended = false;

    private PrCloudRenderer() {
    }

    public static void markDirty() {
        dirty = true;
    }

    public static boolean consumeDirty() {
        boolean was = dirty;
        dirty = false;
        return was;
    }

    public static void beforeRenderClouds() {
        if (!PryzmaConfig.prInfiniteClouds) {
            return;
        }
        float end = RenderSystem.getShaderFogEnd();
        // Only extend fog if not in thick fog / blindness / darkness / powder snow / lava
        if (end > 64.0F) {
            savedFogStart = RenderSystem.getShaderFogStart();
            savedFogEnd = end;
            fogExtended = true;
            RenderSystem.setShaderFogStart(Math.max(savedFogStart, 1500.0F));
            RenderSystem.setShaderFogEnd(Math.max(savedFogEnd, 4200.0F));
        }
    }

    public static void afterRenderClouds() {
        if (fogExtended) {
            fogExtended = false;
            RenderSystem.setShaderFogStart(savedFogStart);
            RenderSystem.setShaderFogEnd(savedFogEnd);
        }
    }

    public static MeshData buildClouds(Tesselator tesselator, double camX, double camY, double camZ, Vec3 cloudColor, CloudStatus cloudStatus) {
        float l = (float) Mth.floor(camX) * 0.00390625F;
        float m = (float) Mth.floor(camZ) * 0.00390625F;
        float n = (float) cloudColor.x;
        float o = (float) cloudColor.y;
        float p = (float) cloudColor.z;
        float q = n * 0.9F;
        float r = o * 0.9F;
        float s = p * 0.9F;
        float t = n * 0.7F;
        float u = o * 0.7F;
        float v = p * 0.7F;
        float w = n * 0.8F;
        float x = o * 0.8F;
        float y = p * 0.8F;

        BufferBuilder bufferBuilder = tesselator.begin(Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
        float z = (float) Math.floor(camY / 4.0) * 4.0F;

        boolean infinite = PryzmaConfig.prInfiniteClouds;

        if (cloudStatus == CloudStatus.FANCY) {
            int cellMinX = infinite ? -FAR_CELL_RADIUS : -3;
            int cellMaxX = infinite ? FAR_CELL_RADIUS : 4;
            int cellMinZ = infinite ? -FAR_CELL_RADIUS : -3;
            int cellMaxZ = infinite ? FAR_CELL_RADIUS : 4;

            float bottomY = z + 0.0F;
            float topY = z + 4.0F;
            float wallBottom = z + 0.0F + WALL_EPSILON;
            float wallTop = z + 4.0F - WALL_EPSILON;

            boolean drawBottom = z > -5.0F;
            boolean drawTop = z <= 5.0F;

            for (int aa = cellMinX; aa <= cellMaxX; aa++) {
                for (int ab = cellMinZ; ab <= cellMaxZ; ab++) {
                    if (infinite && (aa * aa + ab * ab > FAR_CELL_RADIUS_SQ)) {
                        continue;
                    }

                    float ac = (float) (aa * 8);
                    float ad = (float) (ab * 8);

                    // Horizontal faces: bottom and top
                    if (drawBottom) {
                        bufferBuilder.addVertex(ac + 0.0F, bottomY, ad + 8.0F)
                                .setUv((ac + 0.0F) * 0.00390625F + l, (ad + 8.0F) * 0.00390625F + m)
                                .setColor(t, u, v, 0.8F)
                                .setNormal(0.0F, -1.0F, 0.0F);
                        bufferBuilder.addVertex(ac + 8.0F, bottomY, ad + 8.0F)
                                .setUv((ac + 8.0F) * 0.00390625F + l, (ad + 8.0F) * 0.00390625F + m)
                                .setColor(t, u, v, 0.8F)
                                .setNormal(0.0F, -1.0F, 0.0F);
                        bufferBuilder.addVertex(ac + 8.0F, bottomY, ad + 0.0F)
                                .setUv((ac + 8.0F) * 0.00390625F + l, (ad + 0.0F) * 0.00390625F + m)
                                .setColor(t, u, v, 0.8F)
                                .setNormal(0.0F, -1.0F, 0.0F);
                        bufferBuilder.addVertex(ac + 0.0F, bottomY, ad + 0.0F)
                                .setUv((ac + 0.0F) * 0.00390625F + l, (ad + 0.0F) * 0.00390625F + m)
                                .setColor(t, u, v, 0.8F)
                                .setNormal(0.0F, -1.0F, 0.0F);
                    }

                    if (drawTop) {
                        bufferBuilder.addVertex(ac + 0.0F, topY, ad + 8.0F)
                                .setUv((ac + 0.0F) * 0.00390625F + l, (ad + 8.0F) * 0.00390625F + m)
                                .setColor(n, o, p, 0.8F)
                                .setNormal(0.0F, 1.0F, 0.0F);
                        bufferBuilder.addVertex(ac + 8.0F, topY, ad + 8.0F)
                                .setUv((ac + 8.0F) * 0.00390625F + l, (ad + 8.0F) * 0.00390625F + m)
                                .setColor(n, o, p, 0.8F)
                                .setNormal(0.0F, 1.0F, 0.0F);
                        bufferBuilder.addVertex(ac + 8.0F, topY, ad + 0.0F)
                                .setUv((ac + 8.0F) * 0.00390625F + l, (ad + 0.0F) * 0.00390625F + m)
                                .setColor(n, o, p, 0.8F)
                                .setNormal(0.0F, 1.0F, 0.0F);
                        bufferBuilder.addVertex(ac + 0.0F, topY, ad + 0.0F)
                                .setUv((ac + 0.0F) * 0.00390625F + l, (ad + 0.0F) * 0.00390625F + m)
                                .setColor(n, o, p, 0.8F)
                                .setNormal(0.0F, 1.0F, 0.0F);
                    }

                    // 3D vertical volumetric slices: only in the near zone
                    boolean isNear = !infinite || (aa >= NEAR_CELL_MIN && aa <= NEAR_CELL_MAX && ab >= NEAR_CELL_MIN && ab <= NEAR_CELL_MAX);
                    if (isNear) {
                        if (aa > -1) {
                            for (int ae = 0; ae < 8; ae++) {
                                float sx = ac + (float) ae;
                                bufferBuilder.addVertex(sx, wallBottom, ad + 8.0F)
                                        .setUv((sx + 0.5F) * 0.00390625F + l, (ad + 8.0F) * 0.00390625F + m)
                                        .setColor(q, r, s, 0.8F)
                                        .setNormal(-1.0F, 0.0F, 0.0F);
                                bufferBuilder.addVertex(sx, wallTop, ad + 8.0F)
                                        .setUv((sx + 0.5F) * 0.00390625F + l, (ad + 8.0F) * 0.00390625F + m)
                                        .setColor(q, r, s, 0.8F)
                                        .setNormal(-1.0F, 0.0F, 0.0F);
                                bufferBuilder.addVertex(sx, wallTop, ad + 0.0F)
                                        .setUv((sx + 0.5F) * 0.00390625F + l, (ad + 0.0F) * 0.00390625F + m)
                                        .setColor(q, r, s, 0.8F)
                                        .setNormal(-1.0F, 0.0F, 0.0F);
                                bufferBuilder.addVertex(sx, wallBottom, ad + 0.0F)
                                        .setUv((sx + 0.5F) * 0.00390625F + l, (ad + 0.0F) * 0.00390625F + m)
                                        .setColor(q, r, s, 0.8F)
                                        .setNormal(-1.0F, 0.0F, 0.0F);
                            }
                        }

                        if (aa <= 1) {
                            for (int ae = 0; ae < 8; ae++) {
                                float sx = ac + (float) ae + 1.0F;
                                bufferBuilder.addVertex(sx, wallBottom, ad + 8.0F)
                                        .setUv((ac + (float) ae + 0.5F) * 0.00390625F + l, (ad + 8.0F) * 0.00390625F + m)
                                        .setColor(q, r, s, 0.8F)
                                        .setNormal(1.0F, 0.0F, 0.0F);
                                bufferBuilder.addVertex(sx, wallTop, ad + 8.0F)
                                        .setUv((ac + (float) ae + 0.5F) * 0.00390625F + l, (ad + 8.0F) * 0.00390625F + m)
                                        .setColor(q, r, s, 0.8F)
                                        .setNormal(1.0F, 0.0F, 0.0F);
                                bufferBuilder.addVertex(sx, wallTop, ad + 0.0F)
                                        .setUv((ac + (float) ae + 0.5F) * 0.00390625F + l, (ad + 0.0F) * 0.00390625F + m)
                                        .setColor(q, r, s, 0.8F)
                                        .setNormal(1.0F, 0.0F, 0.0F);
                                bufferBuilder.addVertex(sx, wallBottom, ad + 0.0F)
                                        .setUv((ac + (float) ae + 0.5F) * 0.00390625F + l, (ad + 0.0F) * 0.00390625F + m)
                                        .setColor(q, r, s, 0.8F)
                                        .setNormal(1.0F, 0.0F, 0.0F);
                            }
                        }

                        if (ab > -1) {
                            for (int ae = 0; ae < 8; ae++) {
                                float sz = ad + (float) ae;
                                bufferBuilder.addVertex(ac + 0.0F, wallTop, sz)
                                        .setUv((ac + 0.0F) * 0.00390625F + l, (sz + 0.5F) * 0.00390625F + m)
                                        .setColor(w, x, y, 0.8F)
                                        .setNormal(0.0F, 0.0F, -1.0F);
                                bufferBuilder.addVertex(ac + 8.0F, wallTop, sz)
                                        .setUv((ac + 8.0F) * 0.00390625F + l, (sz + 0.5F) * 0.00390625F + m)
                                        .setColor(w, x, y, 0.8F)
                                        .setNormal(0.0F, 0.0F, -1.0F);
                                bufferBuilder.addVertex(ac + 8.0F, wallBottom, sz)
                                        .setUv((ac + 8.0F) * 0.00390625F + l, (sz + 0.5F) * 0.00390625F + m)
                                        .setColor(w, x, y, 0.8F)
                                        .setNormal(0.0F, 0.0F, -1.0F);
                                bufferBuilder.addVertex(ac + 0.0F, wallBottom, sz)
                                        .setUv((ac + 0.0F) * 0.00390625F + l, (sz + 0.5F) * 0.00390625F + m)
                                        .setColor(w, x, y, 0.8F)
                                        .setNormal(0.0F, 0.0F, -1.0F);
                            }
                        }

                        if (ab <= 1) {
                            for (int ae = 0; ae < 8; ae++) {
                                float sz = ad + (float) ae + 1.0F;
                                bufferBuilder.addVertex(ac + 0.0F, wallTop, sz)
                                        .setUv((ac + 0.0F) * 0.00390625F + l, (ad + (float) ae + 0.5F) * 0.00390625F + m)
                                        .setColor(w, x, y, 0.8F)
                                        .setNormal(0.0F, 0.0F, 1.0F);
                                bufferBuilder.addVertex(ac + 8.0F, wallTop, sz)
                                        .setUv((ac + 8.0F) * 0.00390625F + l, (ad + (float) ae + 0.5F) * 0.00390625F + m)
                                        .setColor(w, x, y, 0.8F)
                                        .setNormal(0.0F, 0.0F, 1.0F);
                                bufferBuilder.addVertex(ac + 8.0F, wallBottom, sz)
                                        .setUv((ac + 8.0F) * 0.00390625F + l, (ad + (float) ae + 0.5F) * 0.00390625F + m)
                                        .setColor(w, x, y, 0.8F)
                                        .setNormal(0.0F, 0.0F, 1.0F);
                                bufferBuilder.addVertex(ac + 0.0F, wallBottom, sz)
                                        .setUv((ac + 0.0F) * 0.00390625F + l, (ad + (float) ae + 0.5F) * 0.00390625F + m)
                                        .setColor(w, x, y, 0.8F)
                                        .setNormal(0.0F, 0.0F, 1.0F);
                            }
                        }
                    }
                }
            }
        } else {
            // Fast clouds mode (flat single quads)
            int range = infinite ? 384 : 32;
            int rangeSq = range * range;
            for (int af = -range; af < range; af += 32) {
                for (int ag = -range; ag < range; ag += 32) {
                    if (infinite && (af * af + ag * ag > rangeSq)) {
                        continue;
                    }
                    bufferBuilder.addVertex((float) af, z, (float) (ag + 32))
                            .setUv((float) af * 0.00390625F + l, (float) (ag + 32) * 0.00390625F + m)
                            .setColor(n, o, p, 0.8F)
                            .setNormal(0.0F, -1.0F, 0.0F);
                    bufferBuilder.addVertex((float) (af + 32), z, (float) (ag + 32))
                            .setUv((float) (af + 32) * 0.00390625F + l, (float) (ag + 32) * 0.00390625F + m)
                            .setColor(n, o, p, 0.8F)
                            .setNormal(0.0F, -1.0F, 0.0F);
                    bufferBuilder.addVertex((float) (af + 32), z, (float) ag)
                            .setUv((float) (af + 32) * 0.00390625F + l, (float) ag * 0.00390625F + m)
                            .setColor(n, o, p, 0.8F)
                            .setNormal(0.0F, -1.0F, 0.0F);
                    bufferBuilder.addVertex((float) af, z, (float) ag)
                            .setUv((float) af * 0.00390625F + l, (float) ag * 0.00390625F + m)
                            .setColor(n, o, p, 0.8F)
                            .setNormal(0.0F, -1.0F, 0.0F);
                }
            }
        }

        return bufferBuilder.buildOrThrow();
    }
}
