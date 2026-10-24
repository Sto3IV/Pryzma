package net.pryzma.overlay;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * F9 Chunk Boundaries Grid Overlay:
 * <ul>
 *   <li>Mode 1: Current Chunk (16x16)</li>
 *   <li>Mode 2: 3x3 Chunks Area surrounding the player</li>
 * </ul>
 * Features:
 * <ul>
 *   <li>Red vertical corner lines from world min Y to max Y.</li>
 *   <li>Yellow horizontal perimeter at player floor level.</li>
 *   <li>Alternating Green and Cyan-Blue horizontal borders every 16 blocks vertically (sub-chunk edges).</li>
 * </ul>
 */
public final class PrChunkGridOverlay {
    private PrChunkGridOverlay() {
    }

    public static void render(PoseStack poseStack, VertexConsumer vc, int mode, Player player, Level level) {
        if (mode <= 0 || player == null || level == null) {
            return;
        }

        PoseStack.Pose pose = poseStack.last();
        ChunkPos centerChunk = player.chunkPosition();
        int radius = (mode == 1) ? 0 : 1;

        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();
        float playerFloorY = Mth.floor(player.getY()) + 0.02f;

        for (int cx = centerChunk.x - radius; cx <= centerChunk.x + radius; cx++) {
            for (int cz = centerChunk.z - radius; cz <= centerChunk.z + radius; cz++) {
                drawChunk(poseStack, vc, pose, cx, cz, minY, maxY, playerFloorY);
            }
        }
    }

    private static void drawChunk(PoseStack poseStack, VertexConsumer vc, PoseStack.Pose pose,
                                  int cx, int cz, int minY, int maxY, float playerFloorY) {
        float x0 = (float) (cx << 4);
        float z0 = (float) (cz << 4);
        float x1 = x0 + 16.0f;
        float z1 = z0 + 16.0f;

        // Red vertical corner lines
        line(vc, pose, x0, minY, z0, x0, maxY, z0, 1.0f, 0.15f, 0.15f, 1.0f);
        line(vc, pose, x1, minY, z0, x1, maxY, z0, 1.0f, 0.15f, 0.15f, 1.0f);
        line(vc, pose, x0, minY, z1, x0, maxY, z1, 1.0f, 0.15f, 0.15f, 1.0f);
        line(vc, pose, x1, minY, z1, x1, maxY, z1, 1.0f, 0.15f, 0.15f, 1.0f);

        // Yellow horizontal perimeter at player level
        rect(vc, pose, x0, z0, x1, z1, playerFloorY, 1.0f, 0.95f, 0.15f, 1.0f);

        // Sub-chunk horizontal grid lines every 16 blocks Y
        int firstY = Math.floorDiv(minY + 15, 16) * 16;
        for (int y = firstY; y <= maxY; y += 16) {
            boolean green = ((y >> 4) & 1) == 0;
            float r = green ? 0.15f : 0.15f;
            float g = green ? 1.00f : 0.75f;
            float b = green ? 0.25f : 1.00f;
            rect(vc, pose, x0, z0, x1, z1, (float) y + 0.01f, r, g, b, 1.0f);
        }
    }

    private static void rect(VertexConsumer vc, PoseStack.Pose pose,
                             float x0, float z0, float x1, float z1, float y,
                             float r, float g, float b, float a) {
        line(vc, pose, x0, y, z0, x1, y, z0, r, g, b, a);
        line(vc, pose, x1, y, z0, x1, y, z1, r, g, b, a);
        line(vc, pose, x1, y, z1, x0, y, z1, r, g, b, a);
        line(vc, pose, x0, y, z1, x0, y, z0, r, g, b, a);
    }

    private static void line(VertexConsumer vc, PoseStack.Pose pose,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float r, float g, float b, float a) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float nx = 0.0f;
        float ny = 1.0f;
        float nz = 0.0f;
        if (len > 1.0E-5F) {
            nx = dx / len;
            ny = dy / len;
            nz = dz / len;
        }

        vc.addVertex(pose, x1, y1, z1).setColor(r, g, b, a).setNormal(pose, nx, ny, nz);
        vc.addVertex(pose, x2, y2, z2).setColor(r, g, b, a).setNormal(pose, nx, ny, nz);
    }
}
