package net.pryzma.shader.gl.texture;

import net.pryzma.shader.gl.ShaderRenderSystem;
import org.lwjgl.opengl.GL32C;

import java.nio.ByteBuffer;
import java.util.Optional;

public enum TextureType {
	TEXTURE_1D(GL32C.GL_TEXTURE_1D),
	TEXTURE_2D(GL32C.GL_TEXTURE_2D),
	TEXTURE_3D(GL32C.GL_TEXTURE_3D),
	TEXTURE_RECTANGLE(GL32C.GL_TEXTURE_RECTANGLE);

	private final int glType;

	TextureType(int glType) {
		this.glType = glType;
	}

	public static Optional<TextureType> fromString(String name) {
		try {
			return Optional.of(TextureType.valueOf(name));
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}

	public int getGlType() {
		return glType;
	}

	public void apply(int texture, int sizeX, int sizeY, int sizeZ, int internalFormat, int format, int pixelType, ByteBuffer pixels) {
		switch (this) {
			case TEXTURE_1D:
				ShaderRenderSystem.texImage1D(texture, getGlType(), 0, internalFormat, sizeX, 0, format, pixelType, pixels);
				break;
			case TEXTURE_2D, TEXTURE_RECTANGLE:
				ShaderRenderSystem.texImage2D(texture, getGlType(), 0, internalFormat, sizeX, sizeY, 0, format, pixelType, pixels);
				break;
			case TEXTURE_3D:
				ShaderRenderSystem.texImage3D(texture, getGlType(), 0, internalFormat, sizeX, sizeY, sizeZ, 0, format, pixelType, pixels);
				break;
		}
	}
}
