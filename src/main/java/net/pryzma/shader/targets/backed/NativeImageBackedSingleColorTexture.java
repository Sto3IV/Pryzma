package net.pryzma.shader.targets.backed;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ARGB;

public class NativeImageBackedSingleColorTexture extends DynamicTexture {
	public NativeImageBackedSingleColorTexture(int red, int green, int blue, int alpha) {
		super(() -> "pryzma_single_color", create(ARGB.toABGR(ARGB.color(alpha, red, green, blue))));
	}

	public NativeImageBackedSingleColorTexture(int rgba) {
		this(rgba >> 24 & 0xFF, rgba >> 16 & 0xFF, rgba >> 8 & 0xFF, rgba & 0xFF);
	}

	private static NativeImage create(int color) {
		NativeImage image = new NativeImage(NativeImage.Format.RGBA, 1, 1, false);

		image.setPixelABGR(0, 0, color);

		return image;
	}
}
