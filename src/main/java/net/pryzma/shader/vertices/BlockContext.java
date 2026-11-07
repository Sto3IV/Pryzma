package net.pryzma.shader.vertices;

import com.mojang.blaze3d.vertex.VertexConsumer;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.pryzma.shader.shaderpack.materialmap.WorldRenderingSettings;

/**
 * The per-block context of the extended terrain format: {@code block.properties} id ({@code mc_Entity}),
 * light emission and section-local position ({@code at_midBlock}). Every path that meshes a chunk block
 * brackets it with context so a block carries its shader material and coordinates.
 */
public final class BlockContext {
    private BlockContext() {
    }

    public static final class State {
        public int block = -1;
        public byte renderType = -1;
        public byte emission = 0;
        public int localX;
        public int localY;
        public int localZ;
        public boolean active = false;
    }

    private static final ThreadLocal<State> CURRENT = ThreadLocal.withInitial(State::new);

    public static State current() {
        return CURRENT.get();
    }

    public static void set(int block, byte renderType, byte emission, int localX, int localY, int localZ) {
        State s = CURRENT.get();
        s.block = block;
        s.renderType = renderType;
        s.emission = emission;
        s.localX = localX;
        s.localY = localY;
        s.localZ = localZ;
        s.active = true;
    }

    public static void clear() {
        State s = CURRENT.get();
        s.active = false;
        s.block = -1;
        s.renderType = -1;
        s.emission = 0;
    }

    /**
     * Begins {@code state}'s context on {@code consumer} and returns the builder to end it on, or
     * {@code null} when chunks are meshed without the extended format. Runs on chunk workers.
     */
    @SuppressWarnings("deprecation")
    public static BlockSensitiveBufferBuilder begin(VertexConsumer consumer, BlockState state, BlockPos pos) {
        if (!WorldRenderingSettings.INSTANCE.shouldUseExtendedVertexFormat() || !(consumer instanceof BlockSensitiveBufferBuilder builder)) {
            return null;
        }
        Object2IntMap<BlockState> ids = WorldRenderingSettings.INSTANCE.getBlockStateIds();
        if (ids == null) {
            return null;
        }
        builder.beginBlock(ids.getInt(state), (byte) 0, (byte) state.getLightEmission(), pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
        return builder;
    }
}
