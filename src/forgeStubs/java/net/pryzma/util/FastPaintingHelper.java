package net.pryzma.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.decoration.Painting;
import net.pryzma.Config;

public final class FastPaintingHelper {

    private FastPaintingHelper() {}

    public static boolean renderPainting(
            PoseStack poseStack,
            VertexConsumer consumer,
            Painting painting,
            int width,
            int height,
            TextureAtlasSprite paintingSprite,
            TextureAtlasSprite backSprite
    ) {
        if (!Config.isFastPaintings()) {
            return false;
        }

        if (paintingSprite == null || backSprite == null) {
            return false;
        }

        PoseStack.Pose pose = poseStack.last();
        float f = (float)(-width) / 2.0F;
        float f1 = (float)(-height) / 2.0F;
        float f15 = f + (float)width;
        float f16 = f;
        float f17 = f1 + (float)height;
        float f18 = f1;

        Direction direction = painting.getDirection();
        int centerX = painting.getBlockX();
        int centerZ = painting.getBlockZ();
        if (direction == Direction.NORTH || direction == Direction.SOUTH) {
            centerX = Mth.floor(painting.getX());
        } else if (direction == Direction.WEST || direction == Direction.EAST) {
            centerZ = Mth.floor(painting.getZ());
        }
        int centerY = Mth.floor(painting.getY());

        int lightCoords = LevelRenderer.getLightColor(painting.level(), new BlockPos(centerX, centerY, centerZ));

        float frontU0 = paintingSprite.getU0();
        float frontU1 = paintingSprite.getU1();
        float frontV0 = paintingSprite.getV0();
        float frontV1 = paintingSprite.getV1();

        float backU0 = backSprite.getU0();
        float backU1 = backSprite.getU1();
        float backV0 = backSprite.getV0();
        float backV1 = backSprite.getV1();

        float topBottomU0 = backSprite.getU0();
        float topBottomU1 = backSprite.getU1();
        float topBottomV0 = backSprite.getV0();
        float topBottomV1 = backSprite.getV(0.0625F);

        float leftRightU0 = backSprite.getU0();
        float leftRightU1 = backSprite.getU(0.0625F);
        float leftRightV0 = backSprite.getV0();
        float leftRightV1 = backSprite.getV1();

        // 1. Front face (Facing -Z, normal: 0, 0, -1)
        vertex(pose, consumer, f15, f18, frontU0, frontV1, -0.03125F, 0, 0, -1, lightCoords);
        vertex(pose, consumer, f16, f18, frontU1, frontV1, -0.03125F, 0, 0, -1, lightCoords);
        vertex(pose, consumer, f16, f17, frontU1, frontV0, -0.03125F, 0, 0, -1, lightCoords);
        vertex(pose, consumer, f15, f17, frontU0, frontV0, -0.03125F, 0, 0, -1, lightCoords);

        // 2. Back face (Facing +Z, normal: 0, 0, 1)
        vertex(pose, consumer, f15, f17, backU1, backV0, 0.03125F, 0, 0, 1, lightCoords);
        vertex(pose, consumer, f16, f17, backU0, backV0, 0.03125F, 0, 0, 1, lightCoords);
        vertex(pose, consumer, f16, f18, backU0, backV1, 0.03125F, 0, 0, 1, lightCoords);
        vertex(pose, consumer, f15, f18, backU1, backV1, 0.03125F, 0, 0, 1, lightCoords);

        // 3. Top edge (Facing +Y, normal: 0, 1, 0)
        vertex(pose, consumer, f15, f17, topBottomU0, topBottomV0, -0.03125F, 0, 1, 0, lightCoords);
        vertex(pose, consumer, f16, f17, topBottomU1, topBottomV0, -0.03125F, 0, 1, 0, lightCoords);
        vertex(pose, consumer, f16, f17, topBottomU1, topBottomV1, 0.03125F, 0, 1, 0, lightCoords);
        vertex(pose, consumer, f15, f17, topBottomU0, topBottomV1, 0.03125F, 0, 1, 0, lightCoords);

        // 4. Bottom edge (Facing -Y, normal: 0, -1, 0)
        vertex(pose, consumer, f15, f18, topBottomU0, topBottomV0, 0.03125F, 0, -1, 0, lightCoords);
        vertex(pose, consumer, f16, f18, topBottomU1, topBottomV0, 0.03125F, 0, -1, 0, lightCoords);
        vertex(pose, consumer, f16, f18, topBottomU1, topBottomV1, -0.03125F, 0, -1, 0, lightCoords);
        vertex(pose, consumer, f15, f18, topBottomU0, topBottomV1, -0.03125F, 0, -1, 0, lightCoords);

        // 5. Right edge (Facing -X, normal: -1, 0, 0)
        vertex(pose, consumer, f15, f17, leftRightU1, leftRightV0, 0.03125F, -1, 0, 0, lightCoords);
        vertex(pose, consumer, f15, f18, leftRightU1, leftRightV1, 0.03125F, -1, 0, 0, lightCoords);
        vertex(pose, consumer, f15, f18, leftRightU0, leftRightV1, -0.03125F, -1, 0, 0, lightCoords);
        vertex(pose, consumer, f15, f17, leftRightU0, leftRightV0, -0.03125F, -1, 0, 0, lightCoords);

        // 6. Left edge (Facing +X, normal: 1, 0, 0)
        vertex(pose, consumer, f16, f17, leftRightU1, leftRightV0, -0.03125F, 1, 0, 0, lightCoords);
        vertex(pose, consumer, f16, f18, leftRightU1, leftRightV1, -0.03125F, 1, 0, 0, lightCoords);
        vertex(pose, consumer, f16, f18, leftRightU0, leftRightV1, 0.03125F, 1, 0, 0, lightCoords);
        vertex(pose, consumer, f16, f17, leftRightU0, leftRightV0, 0.03125F, 1, 0, 0, lightCoords);

        return true;
    }

    private static void vertex(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            float x,
            float y,
            float u,
            float v,
            float z,
            int normalX,
            int normalY,
            int normalZ,
            int lightCoords
    ) {
        consumer.addVertex(pose, x, y, z)
                .setColor(-1)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(lightCoords)
                .setNormal(pose, (float)normalX, (float)normalY, (float)normalZ);
    }
}
