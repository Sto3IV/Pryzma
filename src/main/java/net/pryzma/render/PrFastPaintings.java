package net.pryzma.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.decoration.Painting;

/**
 * Fast Paintings (Pryzma 1.x): a painting drawn as one box of six quads lit from its centre,
 * instead of six quads for every 16x16 tile, each lit separately.
 */
public final class PrFastPaintings {
    private static final float DEPTH = 0.03125F;

    private PrFastPaintings() {
    }

    public static void render(PoseStack poseStack, VertexConsumer consumer, Painting painting, int width, int height,
            TextureAtlasSprite front, TextureAtlasSprite back) {
        PoseStack.Pose pose = poseStack.last();
        float right = -width / 2.0F + width;
        float left = -width / 2.0F;
        float top = -height / 2.0F + height;
        float bottom = -height / 2.0F;

        Direction direction = painting.getDirection();
        int x = painting.getBlockX();
        int z = painting.getBlockZ();
        if (direction == Direction.NORTH || direction == Direction.SOUTH) {
            x = Mth.floor(painting.getX());
        } else {
            z = Mth.floor(painting.getZ());
        }
        int light = LevelRenderer.getLightColor(painting.level(), new BlockPos(x, Mth.floor(painting.getY()), z));

        float bu0 = back.getU0();
        float bu1 = back.getU1();
        float bv0 = back.getV0();
        float bv1 = back.getV1();
        float edgeV1 = back.getV(0.0625F);
        float edgeU1 = back.getU(0.0625F);

        // Front, facing -Z.
        vertex(pose, consumer, right, bottom, front.getU0(), front.getV1(), -DEPTH, 0, 0, -1, light);
        vertex(pose, consumer, left, bottom, front.getU1(), front.getV1(), -DEPTH, 0, 0, -1, light);
        vertex(pose, consumer, left, top, front.getU1(), front.getV0(), -DEPTH, 0, 0, -1, light);
        vertex(pose, consumer, right, top, front.getU0(), front.getV0(), -DEPTH, 0, 0, -1, light);
        // Back, facing +Z.
        vertex(pose, consumer, right, top, bu1, bv0, DEPTH, 0, 0, 1, light);
        vertex(pose, consumer, left, top, bu0, bv0, DEPTH, 0, 0, 1, light);
        vertex(pose, consumer, left, bottom, bu0, bv1, DEPTH, 0, 0, 1, light);
        vertex(pose, consumer, right, bottom, bu1, bv1, DEPTH, 0, 0, 1, light);
        // Top edge, facing +Y.
        vertex(pose, consumer, right, top, bu0, bv0, -DEPTH, 0, 1, 0, light);
        vertex(pose, consumer, left, top, bu1, bv0, -DEPTH, 0, 1, 0, light);
        vertex(pose, consumer, left, top, bu1, edgeV1, DEPTH, 0, 1, 0, light);
        vertex(pose, consumer, right, top, bu0, edgeV1, DEPTH, 0, 1, 0, light);
        // Bottom edge, facing -Y.
        vertex(pose, consumer, right, bottom, bu0, bv0, DEPTH, 0, -1, 0, light);
        vertex(pose, consumer, left, bottom, bu1, bv0, DEPTH, 0, -1, 0, light);
        vertex(pose, consumer, left, bottom, bu1, edgeV1, -DEPTH, 0, -1, 0, light);
        vertex(pose, consumer, right, bottom, bu0, edgeV1, -DEPTH, 0, -1, 0, light);
        // Right edge, facing -X.
        vertex(pose, consumer, right, top, edgeU1, bv0, DEPTH, -1, 0, 0, light);
        vertex(pose, consumer, right, bottom, edgeU1, bv1, DEPTH, -1, 0, 0, light);
        vertex(pose, consumer, right, bottom, bu0, bv1, -DEPTH, -1, 0, 0, light);
        vertex(pose, consumer, right, top, bu0, bv0, -DEPTH, -1, 0, 0, light);
        // Left edge, facing +X.
        vertex(pose, consumer, left, top, edgeU1, bv0, -DEPTH, 1, 0, 0, light);
        vertex(pose, consumer, left, bottom, edgeU1, bv1, -DEPTH, 1, 0, 0, light);
        vertex(pose, consumer, left, bottom, bu0, bv1, DEPTH, 1, 0, 0, light);
        vertex(pose, consumer, left, top, bu0, bv0, DEPTH, 1, 0, 0, light);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer consumer, float x, float y, float u, float v, float z,
            int nx, int ny, int nz, int light) {
        consumer.addVertex(pose, x, y, z)
                .setColor(-1)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }
}
