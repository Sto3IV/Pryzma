package net.pryzma.shader.fantastic;

import net.pryzma.shader.PryzmaShaders;
import net.minecraft.client.GraphicsPreset;
import net.minecraft.client.OptionInstance;

public enum SupportedGraphicsMode {
	FAST,
	FANCY;

	public static SupportedGraphicsMode fromVanilla(OptionInstance<GraphicsPreset> status) {
		return switch (status.get()) {
			case FAST -> FAST;
			case FANCY -> FANCY;
			case FABULOUS, CUSTOM -> {
				PryzmaShaders.logger.warn("Detected Fabulous/Custom Graphics being used somehow, changing to Fancy!");
				status.set(GraphicsPreset.FANCY);
				yield FANCY;
			}
		};
	}

	public GraphicsPreset toVanilla() {
		return switch (this) {
			case FAST -> GraphicsPreset.FAST;
			case FANCY -> GraphicsPreset.FANCY;
		};
	}
}
