package net.pryzma.overlay;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

/**
 * Render type for world-space overlay geometry (F7 / F9).
 *
 * <p>Overlay geometry is drawn as explicit quads instead of {@code RenderType.lines()} because
 * line width is not portable: {@code glLineWidth} is clamped to 1.0 on most core profiles, and the
 * screen-space widening of vanilla lines depends on the bound {@code rendertype_lines} program
 * (which shader packs may replace). Quads rasterise identically everywhere.</p>
 *
 * <p>Compared to {@link RenderType#debugQuads()} this type adds polygon-offset layering (no
 * z-fighting with the block face an overlay sits on) and disables depth writes (an overlay must
 * not occlude the world it annotates).</p>
 */
public final class PrOverlayRenderType {
    public static final RenderType OVERLAY_QUADS = RenderType.create(
            "pryzma_overlay_quads",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            16384,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLayeringState(RenderStateShard.POLYGON_OFFSET_LAYERING)
                    .createCompositeState(false)
    );

    private PrOverlayRenderType() {
    }
}
