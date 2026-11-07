package net.pryzma.shader.targets.backed;

import com.mojang.blaze3d.platform.NativeImage;
import net.pryzma.shader.gl.texture.TextureAccess;
import net.pryzma.shader.gl.texture.TextureType;
import net.pryzma.shader.pbr.TextureTracker;
import net.minecraft.client.renderer.texture.DynamicTexture;

import java.util.Objects;
import java.util.Random;
import java.util.function.IntSupplier;

public class NativeImageBackedNoiseTexture extends DynamicTexture implements TextureAccess {
	public NativeImageBackedNoiseTexture(int size) {
		super(() -> "pryzma_noise_texture", create(size));
		upload();
	}

	private static NativeImage create(int size) {
		NativeImage image = new NativeImage(NativeImage.Format.RGBA, size, size, false);
		Random random = new Random(0);

		for (int x = 0; x < size; x++) {
			for (int y = 0; y < size; y++) {
				int color = random.nextInt() | (255 << 24);

				image.setPixel(x, y, color);
			}
		}

		return image;
	}

	public int getId() {
		return TextureTracker.getTextureId(this);
	}

	@Override
	public void upload() {
		super.upload();
	}

	@Override
	public TextureType getType() {
		return TextureType.TEXTURE_2D;
	}

	@Override
	public IntSupplier getTextureId() {
		return this::getId;
	}
}
