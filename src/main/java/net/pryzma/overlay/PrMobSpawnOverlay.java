package net.pryzma.overlay;

import java.util.Arrays;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * F7 Light Level / Mob Spawn Overlay:
 * <ul>
 *   <li>Yellow Cross: Hostile mobs spawn at night / when sky light falls (Block Light == 0, Sky Light &gt; 0).</li>
 *   <li>Red Cross: Hostile mobs spawn at all times (Block Light == 0, Sky Light == 0).</li>
 *   <li>No Cross: No hostile mob spawn (Block Light &gt; 0).</li>
 * </ul>
 */
public final class PrMobSpawnOverlay {
    private static final int HORIZONTAL_RADIUS = 18;
    private static final int VERTICAL_RADIUS = 8;
    private static final int UPDATE_INTERVAL = 4; // 5 Hz update rate
    /** On-screen thickness of the X strokes, in framebuffer pixels. */
    private static final float X_PIXELS = 3.0f;

    private static long[] packedPositions = new long[2048];
    private static byte[] colors = new byte[2048]; // 1 = Yellow, 2 = Red
    private static int count = 0;
    private static int tickCounter = 0;
    private static boolean dirty = true;
    private static long lastPlayerBlock = Long.MIN_VALUE;

    private static final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private static final BlockPos.MutableBlockPos above = new BlockPos.MutableBlockPos();

    private PrMobSpawnOverlay() {
    }

    public static void markDirty() {
        dirty = true;
    }

    public static void clear() {
        count = 0;
        dirty = true;
    }

    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            count = 0;
            return;
        }

        long playerBlock = mc.player.blockPosition().asLong();
        if (!dirty && playerBlock == lastPlayerBlock && (++tickCounter % UPDATE_INTERVAL) != 0) {
            return;
        }

        lastPlayerBlock = playerBlock;
        dirty = false;
        rebuild(mc);
    }

    private static void rebuild(Minecraft mc) {
        Level level = mc.level;
        BlockPos playerPos = mc.player.blockPosition();

        int px = playerPos.getX();
        int py = playerPos.getY();
        int pz = playerPos.getZ();

        int minY = Math.max(level.getMinBuildHeight(), py - VERTICAL_RADIUS);
        int maxY = Math.min(level.getMaxBuildHeight() - 2, py + VERTICAL_RADIUS);

        count = 0;

        for (int x = px - HORIZONTAL_RADIUS; x <= px + HORIZONTAL_RADIUS; x++) {
            for (int z = pz - HORIZONTAL_RADIUS; z <= pz + HORIZONTAL_RADIUS; z++) {
                for (int y = maxY; y >= minY; y--) {
                    cursor.set(x, y, z);
                    BlockState floor = level.getBlockState(cursor);

                    if (!floor.isFaceSturdy(level, cursor, Direction.UP)) {
                        continue;
                    }

                    above.set(x, y + 1, z);
                    BlockState aboveState = level.getBlockState(above);

                    if (!aboveState.getCollisionShape(level, above).isEmpty()) {
                        continue;
                    }

                    if (!aboveState.getFluidState().isEmpty()) {
                        continue;
                    }

                    int blockLight = level.getBrightness(LightLayer.BLOCK, above);
                    if (blockLight > 0) {
                        continue;
                    }

                    int skyLight = level.getBrightness(LightLayer.SKY, above);
                    byte color = skyLight > 0 ? (byte) 1 : (byte) 2;

                    addMarker(above.asLong(), color);
                }
            }
        }
    }

    private static void addMarker(long packedPos, byte color) {
        if (count == packedPositions.length) {
            int newCap = packedPositions.length * 2;
            packedPositions = Arrays.copyOf(packedPositions, newCap);
            colors = Arrays.copyOf(colors, newCap);
        }
        packedPositions[count] = packedPos;
        colors[count] = color;
        count++;
    }

    /**
     * Draws NEI's full-face X as two flat quads lying on the top face of the floor block.
     *
     * <p>Quads instead of lines: {@code glLineWidth} cannot be relied on, and the old code passed
     * {@code (0, 1, 0)} as the normal of a diagonal segment, which is the direction vector vanilla's
     * line shader uses to widen the line - so the crosses were widened along an axis unrelated to
     * the segment and looked uneven on every GPU, not just AMD.</p>
     */
    public static void render(PoseStack poseStack, VertexConsumer vc) {
        if (count == 0) {
            return;
        }

        PoseStack.Pose pose = poseStack.last();
        // NEI draws corner to corner across the whole face; 0.49 keeps the stroke inside the block.
        final float half = 0.49f;
        final float lift = 0.02f;

        for (int i = 0; i < count; i++) {
            long p = packedPositions[i];
            int x = BlockPos.getX(p);
            int y = BlockPos.getY(p);
            int z = BlockPos.getZ(p);

            float cx = x + 0.5f;
            float cy = y + lift;
            float cz = z + 0.5f;

            float r = 1.0f;
            float g = (colors[i] == 1) ? 0.95f : 0.15f;
            float b = (colors[i] == 1) ? 0.10f : 0.15f;

            float halfWidth = PrOverlayGeometry.halfWidth(
                    X_PIXELS, PrOverlayGeometry.distance(cx, cy, cz), 0.004f, 0.08f);

            PrOverlayGeometry.flat(vc, pose,
                    cx - half, cy, cz - half, cx + half, cy, cz + half, halfWidth, r, g, b, 1.0f);
            PrOverlayGeometry.flat(vc, pose,
                    cx - half, cy, cz + half, cx + half, cy, cz - half, halfWidth, r, g, b, 1.0f);
        }
    }
}
