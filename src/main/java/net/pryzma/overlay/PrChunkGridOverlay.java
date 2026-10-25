package net.pryzma.overlay;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * F9 chunk overlay, modelled on classic NotEnoughItems (NEI) with enhanced modern visibility.
 *
 * <ul>
 *   <li>Mode 1 - Chunk Boundaries & Center: 5x5 chunk lattice (player chunk + 8 inner circle + 16 outer wrapping)
 *       with tall red vertical lines at chunk corners and a prominent yellow vertical line at the chunk center (8, 8).</li>
 *   <li>Mode 2 - Chunk Boundaries & Block Grid: 5x5 chunk lattice corners retained; center line removed;
 *       green per-block grid cage (horizontal rims every Y block + vertical lines for every block 1..15)
 *       rendered around the player's current chunk.</li>
 * </ul>
 */
public final class PrChunkGridOverlay {
    /** 2 -> 5x5 chunks (player chunk + 8 inner + 16 outer = 25 chunks total). */
    private static final int CHUNK_RADIUS = 2;

    private static final float BORDER_PIXELS = 3.0F;
    private static final float CENTER_PIXELS = 3.0F;
    private static final float CAGE_PIXELS = 2.0F;

    /** Height span for the green block cage (32 blocks tall centered on player: +-16 blocks). */
    private static final float CAGE_HALF_HEIGHT = 16.0F;

    private static final float RED_R = 1.0F;
    private static final float RED_G = 0.15F;
    private static final float RED_B = 0.15F;

    private static final float YELLOW_R = 1.0F;
    private static final float YELLOW_G = 0.95F;
    private static final float YELLOW_B = 0.10F;

    private static final float GREEN_R = 0.10F;
    private static final float GREEN_G = 1.00F;
    private static final float GREEN_B = 0.20F;
    private static final float GREEN_A = 0.60F;

    private PrChunkGridOverlay() {
    }

    public static void render(PoseStack poseStack, VertexConsumer vc, int mode, Player player, Level level) {
        if (mode <= 0 || player == null || level == null) {
            return;
        }

        PoseStack.Pose pose = poseStack.last();
        ChunkPos center = player.chunkPosition();
        int worldMinY = level.getMinBuildHeight();
        int worldMaxY = level.getMaxBuildHeight();

        // Both Mode 1 and Mode 2 render the 5x5 chunk corner boundaries
        drawChunkCorners(vc, pose, center, worldMinY, worldMaxY);

        if (mode == 1) {
            // Mode 1: Chunk center vertical line
            drawCenterLine(vc, pose, center, worldMinY, worldMaxY);
        } else if (mode == 2) {
            // Mode 2: Removes chunk center line; adds green block grid cage
            drawBlockCage(vc, pose, center, player.getY(), worldMinY, worldMaxY);
        }
    }

    /**
     * Vertical red corner lines for the 5x5 chunk grid (36 vertical lines).
     * Symmetrical alpha falloff based on chunk ring distance from the player's chunk.
     */
    private static void drawChunkCorners(VertexConsumer vc, PoseStack.Pose pose, ChunkPos center,
                                         int worldMinY, int worldMaxY) {
        int baseX = center.getMinBlockX();
        int baseZ = center.getMinBlockZ();

        for (int i = -CHUNK_RADIUS; i <= CHUNK_RADIUS + 1; i++) {
            for (int j = -CHUNK_RADIUS; j <= CHUNK_RADIUS + 1; j++) {
                int di = (i <= 0) ? -i : (i - 1);
                int dj = (j <= 0) ? -j : (j - 1);
                int ring = Math.max(di, dj); // 0 = current chunk corners, 1 = inner circle, 2 = outer wrapping
                float alpha = (ring == 0) ? 1.0F : (ring == 1 ? 0.75F : 0.50F);

                float x = baseX + (i << 4);
                float z = baseZ + (j << 4);
                line(vc, pose, x, worldMinY, z, x, worldMaxY, z,
                        BORDER_PIXELS, 0.30F, RED_R, RED_G, RED_B, alpha);
            }
        }
    }

    /**
     * Vertical golden yellow line marking the exact center of the current chunk at (minX + 8, minZ + 8).
     */
    private static void drawCenterLine(VertexConsumer vc, PoseStack.Pose pose, ChunkPos center,
                                       int worldMinY, int worldMaxY) {
        float x = center.getMinBlockX() + 8.0F;
        float z = center.getMinBlockZ() + 8.0F;
        line(vc, pose, x, worldMinY, z, x, worldMaxY, z,
                CENTER_PIXELS, 0.30F, YELLOW_R, YELLOW_G, YELLOW_B, 1.0F);
    }

    /**
     * Classic NEI per-block cage around the player's current chunk:
     * 32 blocks tall centered on the player, with horizontal rims every Y block
     * and vertical lines for every block coordinate 1..15 along the walls.
     */
    private static void drawBlockCage(VertexConsumer vc, PoseStack.Pose pose, ChunkPos center,
                                      double playerY, int worldMinY, int worldMaxY) {
        Span span = span(playerY, worldMinY, worldMaxY, CAGE_HALF_HEIGHT);
        float x0 = center.getMinBlockX();
        float z0 = center.getMinBlockZ();
        float x1 = x0 + 16.0F;
        float z1 = z0 + 16.0F;

        // Horizontal rims every 1 block Y
        for (int y = Mth.floor(span.bottom()); y <= span.top(); y++) {
            float fy = y;
            line(vc, pose, x0, fy, z0, x1, fy, z0, CAGE_PIXELS, 0.30F, GREEN_R, GREEN_G, GREEN_B, GREEN_A);
            line(vc, pose, x1, fy, z0, x1, fy, z1, CAGE_PIXELS, 0.30F, GREEN_R, GREEN_G, GREEN_B, GREEN_A);
            line(vc, pose, x1, fy, z1, x0, fy, z1, CAGE_PIXELS, 0.30F, GREEN_R, GREEN_G, GREEN_B, GREEN_A);
            line(vc, pose, x0, fy, z1, x0, fy, z0, CAGE_PIXELS, 0.30F, GREEN_R, GREEN_G, GREEN_B, GREEN_A);
        }

        // Vertical lines for every block 1..15
        for (int h = 1; h <= 15; h++) {
            float x = x0 + h;
            float z = z0 + h;
            line(vc, pose, x, span.bottom(), z0, x, span.top(), z0, CAGE_PIXELS, 0.30F, GREEN_R, GREEN_G, GREEN_B, GREEN_A);
            line(vc, pose, x, span.bottom(), z1, x, span.top(), z1, CAGE_PIXELS, 0.30F, GREEN_R, GREEN_G, GREEN_B, GREEN_A);
            line(vc, pose, x0, span.bottom(), z, x0, span.top(), z, CAGE_PIXELS, 0.30F, GREEN_R, GREEN_G, GREEN_B, GREEN_A);
            line(vc, pose, x1, span.bottom(), z, x1, span.top(), z, CAGE_PIXELS, 0.30F, GREEN_R, GREEN_G, GREEN_B, GREEN_A);
        }
    }

    private static void line(VertexConsumer vc, PoseStack.Pose pose,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float pixels, float maxHalfWidth,
                             float r, float g, float b, float a) {
        float mx = (x1 + x2) * 0.5F;
        float my = (y1 + y2) * 0.5F;
        float mz = (z1 + z2) * 0.5F;
        float halfWidth = PrOverlayGeometry.halfWidth(
                pixels, PrOverlayGeometry.distance(mx, my, mz), 0.0035F, maxHalfWidth);
        PrOverlayGeometry.billboard(vc, pose, x1, y1, z1, x2, y2, z2, halfWidth, r, g, b, a);
    }

    private static Span span(double playerY, int worldMinY, int worldMaxY, float halfHeight) {
        float bottom = Mth.floor(playerY) - halfHeight;
        float top = bottom + halfHeight * 2.0F;
        if (bottom < worldMinY) {
            bottom = worldMinY;
            top = worldMinY + halfHeight * 2.0F;
        }
        if (top > worldMaxY) {
            top = worldMaxY;
            bottom = Math.max(worldMinY, worldMaxY - halfHeight * 2.0F);
        }
        return new Span(bottom, top);
    }

    private record Span(float bottom, float top) {
    }
}
