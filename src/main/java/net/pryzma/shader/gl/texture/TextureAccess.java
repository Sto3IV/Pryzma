package net.pryzma.shader.gl.texture;

import java.util.function.IntSupplier;

public interface TextureAccess {
	TextureType getType();

	IntSupplier getTextureId();
}
