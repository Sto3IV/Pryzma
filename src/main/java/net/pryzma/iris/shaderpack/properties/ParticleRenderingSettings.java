package net.pryzma.iris.shaderpack.properties;

import net.pryzma.iris.Iris;

public enum ParticleRenderingSettings {
	UNSET,
	BEFORE,
	MIXED,
	AFTER;

	public static ParticleRenderingSettings fromString(String name) {
		try {
			return ParticleRenderingSettings.valueOf(name);
		} catch (IllegalArgumentException e) {
			Iris.logger.error("Invalid particle rendering settings! " + name);
			return ParticleRenderingSettings.UNSET;
		}
	}
}
