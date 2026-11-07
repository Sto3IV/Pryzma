package net.pryzma.sky;

import java.util.Locale;
import java.util.Optional;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.BlendFactor;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;

import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

/** OptiFine sky layer blend modes, with OptiFine's exact blend factors and brightness channel. */
public enum PrSkyBlend {
    ALPHA(new BlendFunction(BlendFactor.SRC_ALPHA, BlendFactor.ONE_MINUS_SRC_ALPHA), false),
    ADD(new BlendFunction(BlendFactor.SRC_ALPHA, BlendFactor.ONE), false),
    SUBTRACT(new BlendFunction(BlendFactor.ONE_MINUS_DST_COLOR, BlendFactor.ZERO), true),
    MULTIPLY(new BlendFunction(BlendFactor.DST_COLOR, BlendFactor.ONE_MINUS_SRC_ALPHA), true),
    DODGE(new BlendFunction(BlendFactor.ONE, BlendFactor.ONE), true),
    BURN(new BlendFunction(BlendFactor.ZERO, BlendFactor.ONE_MINUS_SRC_COLOR), true),
    SCREEN(new BlendFunction(BlendFactor.ONE, BlendFactor.ONE_MINUS_SRC_COLOR), true),
    OVERLAY(new BlendFunction(BlendFactor.DST_COLOR, BlendFactor.SRC_COLOR), true),
    REPLACE(null, false);

    private final BlendFunction blendFunction;
    /** Brightness scales RGB (true) or alpha (false). */
    private final boolean rgbBrightness;
    private RenderPipeline pipeline;

    PrSkyBlend(BlendFunction blendFunction, boolean rgbBrightness) {
        this.blendFunction = blendFunction;
        this.rgbBrightness = rgbBrightness;
    }

    public boolean isRgbBrightness() {
        return rgbBrightness;
    }

    public BlendFunction blendFunction() {
        return blendFunction;
    }

    public RenderPipeline pipeline() {
        return pipeline;
    }

    public static void registerPipelines(RegisterRenderPipelinesEvent event) {
        for (PrSkyBlend blend : values()) {
            ColorTargetState colorTarget = blend.blendFunction != null
                    ? new ColorTargetState(Optional.of(blend.blendFunction), GpuFormat.RGBA8_UNORM, 15)
                    : ColorTargetState.DEFAULT;

            RenderPipeline p = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
                    .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                    .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                    .withLocation(Identifier.fromNamespaceAndPath("pryzma", "pipeline/sky_" + blend.name().toLowerCase(Locale.ROOT)))
                    .withVertexShader("core/position_tex")
                    .withFragmentShader("core/position_tex")
                    .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                    .withColorTargetState(colorTarget)
                    .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
                    .withPrimitiveTopology(PrimitiveTopology.QUADS)
                    .build();

            blend.pipeline = p;
            event.registerPipeline(p);
        }
    }

    public static PrSkyBlend parse(String s) {
        if (s == null) {
            return ADD;
        }
        return switch (s.trim().toLowerCase(Locale.ROOT)) {
            case "alpha" -> ALPHA;
            case "subtract" -> SUBTRACT;
            case "multiply" -> MULTIPLY;
            case "dodge" -> DODGE;
            case "burn" -> BURN;
            case "screen" -> SCREEN;
            case "overlay" -> OVERLAY;
            case "replace" -> REPLACE;
            default -> ADD;
        };
    }
}
