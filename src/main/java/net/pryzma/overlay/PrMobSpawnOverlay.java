package net.pryzma.overlay;

import java.util.Arrays;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

/**
 * F7 Light Level / Mob Spawn Overlay, audited and refined against classic NEI and MoreOverlays:
 * <ul>
 *   <li>Yellow Cross: Hostile mobs spawn at night / when sky light falls (Block Light == 0, Sky Light &gt; 0).</li>
 *   <li>Red Cross: Hostile mobs spawn at all times (Block Light == 0, Sky Light == 0).</li>
 *   <li>No Cross: No hostile mob spawn (Block Light &gt; 0, invalid surface, or obstruction).</li>
 * </ul>
 * Features:
 * <ul>
 *   <li>Vanilla spawn validation via {@link SpawnPlacementTypes#ON_GROUND} and collision checks.</li>
 *   <li>Snow layer detection and dynamic angle-dependent depth bias to eliminate Z-fighting.</li>
 *   <li>Camera look vector frustum culling (210° cone) to eliminate out-of-view rasterization overhead.</li>
 *   <li>Asymmetric directional scanning and throttled updates (block move, yaw change &gt; 15°, periodic 1 Hz).</li>
 * </ul>
 */
public final class PrMobSpawnOverlay {
    private static final int HORIZONTAL_RADIUS = 18;
    private static final int VERTICAL_RADIUS = 8;
    private static final int UPDATE_INTERVAL = 20; // 1 Hz periodic refresh

    private static final AABB TEST_BB = new AABB(0.3D, 0.0D, 0.3D, 0.7D, 1.0D, 0.7D);
    private static final float CULL_COS = (float) Math.cos(Math.toRadians(105.0));

    private static long[] packedPositions = new long[2048];
    private static byte[] colors = new byte[2048]; // 1 = Yellow, 2 = Red
    private static int count = 0;
    private static int tickCounter = 0;
    private static boolean dirty = true;

    private static int lastPlayerX = Integer.MIN_VALUE;
    private static int lastPlayerY = Integer.MIN_VALUE;
    private static int lastPlayerZ = Integer.MIN_VALUE;
    private static float lastPlayerYaw = Float.NaN;

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
        lastPlayerX = Integer.MIN_VALUE;
        lastPlayerY = Integer.MIN_VALUE;
        lastPlayerZ = Integer.MIN_VALUE;
        lastPlayerYaw = Float.NaN;
    }

    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            count = 0;
            return;
        }

        BlockPos playerPos = mc.player.blockPosition();
        int px = playerPos.getX();
        int py = playerPos.getY();
        int pz = playerPos.getZ();
        float yaw = mc.player.getYRot();

        boolean moved = (px != lastPlayerX || py != lastPlayerY || pz != lastPlayerZ);
        boolean rotated = Float.isNaN(lastPlayerYaw) || Math.abs(yaw - lastPlayerYaw) > 15.0F;
        boolean periodic = (++tickCounter % UPDATE_INTERVAL) == 0;

        if (!dirty && !moved && !rotated && !periodic) {
            return;
        }

        lastPlayerX = px;
        lastPlayerY = py;
        lastPlayerZ = pz;
        lastPlayerYaw = yaw;
        dirty = false;
        rebuild(mc);
    }

    private static void rebuild(Minecraft mc) {
        Level level = mc.level;
        Player player = mc.player;
        if (level == null || player == null) {
            count = 0;
            return;
        }

        BlockPos playerPos = player.blockPosition();
        int px = playerPos.getX();
        int py = playerPos.getY();
        int pz = playerPos.getZ();

        int minY = Math.max(level.getMinBuildHeight(), py - VERTICAL_RADIUS);
        int maxY = Math.min(level.getMaxBuildHeight() - 2, py + VERTICAL_RADIUS);

        int rangeNorth = HORIZONTAL_RADIUS;
        int rangeSouth = HORIZONTAL_RADIUS;
        int rangeEast = HORIZONTAL_RADIUS;
        int rangeWest = HORIZONTAL_RADIUS;

        // Direction-based asymmetric range to optimize performance behind player
        if (HORIZONTAL_RADIUS > 6) {
            switch (player.getDirection()) {
                case NORTH -> rangeSouth = 6;
                case SOUTH -> rangeNorth = 6;
                case WEST -> rangeEast = 6;
                case EAST -> rangeWest = 6;
            }
        }

        count = 0;

        for (int x = px - rangeWest; x <= px + rangeEast; x++) {
            for (int z = pz - rangeNorth; z <= pz + rangeSouth; z++) {
                for (int y = maxY; y >= minY; y--) {
                    cursor.set(x, y, z);

                    if (level.isEmptyBlock(cursor) || level.containsAnyLiquid(new AABB(cursor))) {
                        continue;
                    }

                    above.set(x, y + 1, z);
                    BlockState floor = level.getBlockState(cursor);

                    if (!floor.isFaceSturdy(level, cursor, Direction.UP)
                            && !SpawnPlacementTypes.ON_GROUND.isSpawnPositionOk(level, above, EntityType.ZOMBIE)) {
                        continue;
                    }

                    if (!checkCollision(above, level)) {
                        continue;
                    }

                    int blockLight = level.getBrightness(LightLayer.BLOCK, above);
                    if (blockLight > 0) {
                        continue;
                    }

                    int skyLight = level.getBrightness(LightLayer.SKY, above);
                    byte color = (skyLight > 0) ? (byte) 1 : (byte) 2;

                    addMarker(above.asLong(), color);
                }
            }
        }
    }

    private static boolean checkCollision(BlockPos pos, Level world) {
        BlockState state = world.getBlockState(pos);
        if (state.isCollisionShapeFullBlock(world, pos)) {
            return false;
        }
        if (world.isEmptyBlock(pos)) {
            return true;
        }
        AABB bb = TEST_BB.move(pos.getX(), pos.getY(), pos.getZ());
        return !world.getBlockCollisions(null, bb).iterator().hasNext() && !world.containsAnyLiquid(bb);
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

    public static void render(BufferBuilder buffer, PoseStack.Pose pose, Camera camera,
                              double cameraX, double cameraY, double cameraZ,
                              Player player, Level level) {
        if (count == 0) {
            return;
        }

        Vector3f look = camera.getLookVector();
        double playerY = player.getY();

        for (int i = 0; i < count; i++) {
            long p = packedPositions[i];
            int x = BlockPos.getX(p);
            int y = BlockPos.getY(p);
            int z = BlockPos.getZ(p);

            // Frustum facing cull: discard crosses behind the camera view cone
            double dx = (x + 0.5D) - cameraX;
            double dy = (y + 0.5D) - cameraY;
            double dz = (z + 0.5D) - cameraZ;
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq > 1e-4D) {
                double invLen = 1.0D / Math.sqrt(distSq);
                double dot = (dx * look.x + dy * look.y + dz * look.z) * invLen;
                if (dot < CULL_COS) {
                    continue;
                }
            }

            // Snow layer & dynamic height offset to eliminate Z-fighting with floor polygon
            above.set(x, y, z);
            BlockState stateAtAbove = level.getBlockState(above);
            float renderY;
            if (stateAtAbove.is(BlockTags.SNOW)) {
                float snowOffset = 0.125F;
                if (y > playerY) {
                    renderY = (float) (y + snowOffset + 0.005F);
                } else {
                    renderY = (float) (y + snowOffset + 0.005F + 0.01F * -(y - playerY - 1.0D));
                }
            } else {
                if (y > playerY) {
                    renderY = (float) (y + 0.005F);
                } else {
                    renderY = (float) (y + 0.005F + 0.01F * -(y - playerY - 1.0D));
                }
            }

            float r = 1.0F;
            float g = (colors[i] == 1) ? 0.95F : 0.15F;
            float b = (colors[i] == 1) ? 0.10F : 0.15F;
            float a = 1.0F;

            double x0 = x;
            double x1 = x + 1.0D;
            double z0 = z;
            double z1 = z + 1.0D;

            // Diagonal line 1: (x0, z0) to (x1, z1)
            line(buffer, pose, cameraX, cameraY, cameraZ, x0, renderY, z0, x1, renderY, z1, r, g, b, a);
            // Diagonal line 2: (x1, z0) to (x0, z1)
            line(buffer, pose, cameraX, cameraY, cameraZ, x1, renderY, z0, x0, renderY, z1, r, g, b, a);
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
}
