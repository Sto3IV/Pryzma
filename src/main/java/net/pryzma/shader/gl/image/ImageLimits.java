package net.pryzma.shader.gl.image;

import net.pryzma.shader.gl.ShaderRenderSystem;

public class ImageLimits {
	private static ImageLimits instance;
	private final int maxImageUnits;

	private ImageLimits() {
		this.maxImageUnits = ShaderRenderSystem.getMaxImageUnits();
	}

	public static ImageLimits get() {
		if (instance == null) {
			instance = new ImageLimits();
		}

		return instance;
	}

	public int getMaxImageUnits() {
		return maxImageUnits;
	}
}
