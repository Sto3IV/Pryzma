package net.pryzma.shader.gui.option;

import net.pryzma.shader.PryzmaShaders;
import net.pryzma.shader.pathways.colorspace.ColorSpace;
import net.pryzma.shader.pipeline.WorldRenderingPipeline;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.io.IOException;

public class ShaderVideoSettings {
	private static final Tooltip DISABLED_TOOLTIP = Tooltip.create(Component.translatable("options.pryzma.shadowDistance.disabled"));
	private static final Tooltip ENABLED_TOOLTIP = Tooltip.create(Component.translatable("options.pryzma.shadowDistance.enabled"));
	public static int shadowDistance = 32;
	public static ColorSpace colorSpace = ColorSpace.SRGB;
	public static final OptionInstance<Integer> RENDER_DISTANCE = new ShadowDistanceOption<>("options.pryzma.shadowDistance",
		mc -> {
			WorldRenderingPipeline pipeline = PryzmaShaders.getPipelineManager().getPipelineNullable();

			Tooltip tooltip;

			if (pipeline != null) {
				if (pipeline.getForcedShadowRenderDistanceChunksForDisplay().isPresent()) {
					tooltip = DISABLED_TOOLTIP;
				} else {
					tooltip = ENABLED_TOOLTIP;
				}
			} else {
				tooltip = ENABLED_TOOLTIP;
			}

			return tooltip;
		},
		(arg, d) -> {
			WorldRenderingPipeline pipeline = PryzmaShaders.getPipelineManager().getPipelineNullable();

			if (pipeline != null) {
				d = pipeline.getForcedShadowRenderDistanceChunksForDisplay().orElse(d);
			}

			if (d <= 0.0) {
				return Component.translatable("options.generic_value", Component.translatable("options.pryzma.shadowDistance"), "0 (disabled)");
			} else {
				return Component.translatable("options.generic_value",
					Component.translatable("options.pryzma.shadowDistance"),
					Component.translatable("options.chunks", d));
			}
		},
		new OptionInstance.IntRange(0, 32),
		getOverriddenShadowDistance(shadowDistance),
		integer -> {
			shadowDistance = integer;
			try {
				PryzmaShaders.getShaderConfig().save();
			} catch (IOException e) {
				PryzmaShaders.logger.fatal("Failed to save config!", e);
			}
		});

	public static int getOverriddenShadowDistance(int base) {
		return PryzmaShaders.getPipelineManager().getPipeline()
			.map(pipeline -> pipeline.getForcedShadowRenderDistanceChunksForDisplay().orElse(base))
			.orElse(base);
	}

	public static boolean isShadowDistanceSliderEnabled() {
		return PryzmaShaders.getPipelineManager().getPipeline()
			.map(pipeline -> pipeline.getForcedShadowRenderDistanceChunksForDisplay().isEmpty())
			.orElse(true);
	}
}
