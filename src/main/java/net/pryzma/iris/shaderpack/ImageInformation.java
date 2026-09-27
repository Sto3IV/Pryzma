package net.pryzma.iris.shaderpack;

import net.pryzma.iris.gl.texture.InternalTextureFormat;
import net.pryzma.iris.gl.texture.PixelFormat;
import net.pryzma.iris.gl.texture.PixelType;
import net.pryzma.iris.gl.texture.TextureType;

public record ImageInformation(String name, String samplerName, TextureType target, PixelFormat format,
							   InternalTextureFormat internalTextureFormat,
							   PixelType type, int width, int height, int depth, boolean clear, boolean isRelative,
							   float relativeWidth, float relativeHeight) {
}
