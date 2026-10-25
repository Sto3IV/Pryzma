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

    public static void render(PoseStack poseStack, VertexConsumer vc) {
        if (count == 0) {
            return;
        }

        PoseStack.Pose pose = poseStack.last();
        final float offset = 0.45f;
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
            float a = 1.0f;

            // Diagonal line 1: (cx - offset, cz - offset) to (cx + offset, cz + offset)
            line(vc, pose, cx - offset, cy, cz - offset, cx + offset, cy, cz + offset, r, g, b, a);

            // Diagonal line 2: (cx - offset, cz + offset) to (cx + offset, cz - offset)
            line(vc, pose, cx - offset, cy, cz + offset, cx + offset, cy, cz - offset, r, g, b, a);
        }
    }

    private static void line(VertexConsumer vc, PoseStack.Pose pose,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float r, float g, float b, float a) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float nx = (len > 1.0E-5F) ? (dx / len) : 0.0f;
        float ny = (len > 1.0E-5F) ? (dy / len) : 1.0f;
        float nz = (len > 1.0E-5F) ? (dz / len) : 0.0f;

        vc.addVertex(pose, x1, y1, z1).setColor(r, g, b, a).setNormal(pose, nx, ny, nz);
        vc.addVertex(pose, x2, y2, z2).setColor(r, g, b, a).setNormal(pose, nx, ny, nz);
    }
}
