package net.pryzma.shader.shaderpack.properties;

import net.pryzma.shader.PryzmaShaders;

public enum ParticleRenderingSettings {
	UNSET,
	BEFORE,
	MIXED,
	AFTER;

	public static ParticleRenderingSettings fromString(String name) {
		try {
			return ParticleRenderingSettings.valueOf(name);
		} catch (IllegalArgumentException e) {
			PryzmaShaders.logger.error("Invalid particle rendering settings! " + name);
			return ParticleRenderingSettings.UNSET;
		}
	}
}
