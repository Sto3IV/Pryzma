package net.pryzma.overlay;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * F9 chunk overlay, modelled on classic NotEnoughItems (NEI) with crisp 1-pixel lines:
 * <ul>
 *   <li>Mode 1 - Chunk Boundaries &amp; Center: 5x5 chunk lattice (player chunk + 8 inner circle + 16 outer wrapping)
 *       with tall red vertical lines at chunk corners and a yellow vertical line at the chunk center (8, 8).</li>
 *   <li>Mode 2 - Chunk Boundaries &amp; Block Grid: 5x5 chunk lattice corners retained; center line removed;
 *       green per-block grid cage (horizontal rims every Y block + vertical lines for every block 1..15)
 *       rendered around the player's current chunk.</li>
 * </ul>
 */
public final class PrChunkGridOverlay {
    /** 2 -&gt; 5x5 chunks (player chunk + 8 inner + 16 outer = 25 chunks total). */
    private static final int CHUNK_RADIUS = 2;

    /** 128 blocks tall (NEI: +-64 blocks around the player). */
    private static final float BORDER_HALF_HEIGHT = 64.0F;
    /** 32 blocks tall for the green cage (NEI: +-16 blocks around the player). */
    private static final float CAGE_HALF_HEIGHT = 16.0F;
    private static final float VERTICAL_SEGMENT = 16.0F;

    private static final float RED_R = 1.0F;
    private static final float RED_G = 0.15F;
    private static final float RED_B = 0.15F;

    private static final float YELLOW_R = 1.0F;
    private static final float YELLOW_G = 0.95F;
    private static final float YELLOW_B = 0.10F;

    private static final float GREEN_R = 0.0F;
    private static final float GREEN_G = 1.0F;
    private static final float GREEN_B = 0.0F;
    private static final float GREEN_A = 0.6F;

    private PrChunkGridOverlay() {
    }

    public static void render(BufferBuilder buffer, PoseStack.Pose pose,
                              double cameraX, double cameraY, double cameraZ,
                              int mode, Player player, Level level) {
        if (mode <= 0 || player == null || level == null) {
            return;
        }

        ChunkPos center = player.chunkPosition();
        int worldMinY = level.getMinBuildHeight();
        int worldMaxY = level.getMaxBuildHeight();

        // 5x5 chunk boundary lattice
        drawChunkCorners(buffer, pose, cameraX, cameraY, cameraZ, center, player.getY(), worldMinY, worldMaxY);

        if (mode == 1) {
            // Mode 1: Center vertical line in the player's chunk
            drawCenterLine(buffer, pose, cameraX, cameraY, cameraZ, center, player.getY(), worldMinY, worldMaxY);
        } else if (mode == 2) {
            // Mode 2: Green block grid around the player's chunk
            drawBlockCage(buffer, pose, cameraX, cameraY, cameraZ, center, player.getY(), worldMinY, worldMaxY);
        }
    }

    private static void drawChunkCorners(BufferBuilder buffer, PoseStack.Pose pose,
                                         double cameraX, double cameraY, double cameraZ,
                                         ChunkPos center, double playerY, int worldMinY, int worldMaxY) {
        Span span = span(playerY, worldMinY, worldMaxY, BORDER_HALF_HEIGHT);
        int baseX = center.getMinBlockX();
        int baseZ = center.getMinBlockZ();

        for (int i = -CHUNK_RADIUS; i <= CHUNK_RADIUS + 1; i++) {
            for (int j = -CHUNK_RADIUS; j <= CHUNK_RADIUS + 1; j++) {
                int di = (i <= 0) ? -i : (i - 1);
                int dj = (j <= 0) ? -j : (j - 1);
                int ring = Math.max(di, dj);
                float alpha = (ring == 0) ? 1.0F : (ring == 1 ? 0.75F : 0.45F);

                double x = baseX + (i << 4);
                double z = baseZ + (j << 4);
                verticalLine(buffer, pose, cameraX, cameraY, cameraZ, x, span.bottom(), span.top(), z, RED_R, RED_G, RED_B, alpha);
            }
        }
    }

    private static void drawCenterLine(BufferBuilder buffer, PoseStack.Pose pose,
                                       double cameraX, double cameraY, double cameraZ,
                                       ChunkPos center, double playerY, int worldMinY, int worldMaxY) {
        Span span = span(playerY, worldMinY, worldMaxY, BORDER_HALF_HEIGHT);
        double x = center.getMinBlockX() + 8.0D;
        double z = center.getMinBlockZ() + 8.0D;
        verticalLine(buffer, pose, cameraX, cameraY, cameraZ, x, span.bottom(), span.top(), z, YELLOW_R, YELLOW_G, YELLOW_B, 1.0F);
    }

    private static void drawBlockCage(BufferBuilder buffer, PoseStack.Pose pose,
                                      double cameraX, double cameraY, double cameraZ,
                                      ChunkPos center, double playerY, int worldMinY, int worldMaxY) {
        Span span = span(playerY, worldMinY, worldMaxY, CAGE_HALF_HEIGHT);
        double x0 = center.getMinBlockX();
        double z0 = center.getMinBlockZ();
        double x1 = x0 + 16.0D;
        double z1 = z0 + 16.0D;

        // Horizontal rims every 1 block Y
        for (int y = Mth.floor(span.bottom()); y <= span.top(); y++) {
            double fy = y;
            line(buffer, pose, cameraX, cameraY, cameraZ, x0, fy, z0, x1, fy, z0, GREEN_R, GREEN_G, GREEN_B, GREEN_A);
            line(buffer, pose, cameraX, cameraY, cameraZ, x1, fy, z0, x1, fy, z1, GREEN_R, GREEN_G, GREEN_B, GREEN_A);
            line(buffer, pose, cameraX, cameraY, cameraZ, x1, fy, z1, x0, fy, z1, GREEN_R, GREEN_G, GREEN_B, GREEN_A);
            line(buffer, pose, cameraX, cameraY, cameraZ, x0, fy, z1, x0, fy, z0, GREEN_R, GREEN_G, GREEN_B, GREEN_A);
        }

        // Vertical lines for every block 1..15
        for (int h = 1; h <= 15; h++) {
            double x = x0 + h;
            double z = z0 + h;
            verticalLine(buffer, pose, cameraX, cameraY, cameraZ, x, span.bottom(), span.top(), z0, GREEN_R, GREEN_G, GREEN_B, GREEN_A);
            verticalLine(buffer, pose, cameraX, cameraY, cameraZ, x, span.bottom(), span.top(), z1, GREEN_R, GREEN_G, GREEN_B, GREEN_A);
            verticalLine(buffer, pose, cameraX, cameraY, cameraZ, x0, span.bottom(), span.top(), z, GREEN_R, GREEN_G, GREEN_B, GREEN_A);
            verticalLine(buffer, pose, cameraX, cameraY, cameraZ, x1, span.bottom(), span.top(), z, GREEN_R, GREEN_G, GREEN_B, GREEN_A);
        }
    }

    private static void verticalLine(BufferBuilder buffer, PoseStack.Pose pose,
                                     double cameraX, double cameraY, double cameraZ,
                                     double x, double minY, double maxY, double z,
                                     float r, float g, float b, float a) {
        for (double y = minY; y < maxY; y += VERTICAL_SEGMENT) {
            double yNext = Math.min(y + VERTICAL_SEGMENT, maxY);
            line(buffer, pose, cameraX, cameraY, cameraZ, x, y, z, x, yNext, z, r, g, b, a);
        }
    }

    private static void line(BufferBuilder buffer, PoseStack.Pose pose,
                             double cameraX, double cameraY, double cameraZ,
                             double x1, double y1, double z1,
                             double x2, double y2, double z2,
                             float r, float g, float b, float a) {
        buffer.addVertex(pose, (float) (x1 - cameraX), (float) (y1 - cameraY), (float) (z1 - cameraZ))
                .setColor(r, g, b, a);
        buffer.addVertex(pose, (float) (x2 - cameraX), (float) (y2 - cameraY), (float) (z2 - cameraZ))
                .setColor(r, g, b, a);
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
