package net.pryzma.shader.vertices;

import com.mojang.blaze3d.vertex.VertexConsumer;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.pryzma.shader.shaderpack.materialmap.WorldRenderingSettings;

/**
 * The per-block context of the extended terrain format: {@code block.properties} id ({@code mc_Entity}),
 * light emission and section-local position ({@code at_midBlock}). Every path that meshes a chunk block
 * through {@code renderBatched} brackets it with {@link #begin} and {@link BlockSensitiveBufferBuilder#endBlock},
 * so a block replayed out of the vertex order (decorator LOD) carries exactly what its in-order mesh would.
 */
public final class BlockContext {
    private BlockContext() {
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
