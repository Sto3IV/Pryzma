package net.pryzma.overlay;

import java.util.OptionalDouble;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.RenderType;

/**
 * Custom RenderTypes for Pryzma overlays.
 * <p>
 * Uses {@link #POSITION_COLOR_SHADER} with {@link VertexFormat.Mode#DEBUG_LINES} instead of the
 * flawed vanilla {@code RenderType.lines()} (which manually divides by {@code w} in
 * {@code rendertype_lines.vsh} and causes severe line distortion, ribbons, and sky streaks
 * when lines cross the camera plane or when viewing near-vertically).
 * <p>
 * Under this pipeline, the GPU hardware rasterizer performs proper clip-space frustum
 * clipping before perspective division, guaranteeing perfectly stable 1-pixel line rendering
 * at any viewing angle.
 */
public final class PrRenderTypes extends RenderType {
    public static final RenderType OVERLAY_LINES = create(
            "pr_overlay_lines",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.DEBUG_LINES,
            1536,
            false,
            false,
            CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setLineState(new LineStateShard(OptionalDouble.of(1.0D)))
                    .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setOutputState(ITEM_ENTITY_TARGET)
                    .setWriteMaskState(COLOR_DEPTH_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false)
    );

    private PrRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                          boolean affectsCrumbling, boolean sortOnUpload, Runnable setupState, Runnable clearState) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
    }
}
