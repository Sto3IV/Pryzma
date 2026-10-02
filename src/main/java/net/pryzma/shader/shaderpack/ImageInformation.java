package net.pryzma.shader.shaderpack;

import net.pryzma.shader.gl.texture.InternalTextureFormat;
import net.pryzma.shader.gl.texture.PixelFormat;
import net.pryzma.shader.gl.texture.PixelType;
import net.pryzma.shader.gl.texture.TextureType;

public record ImageInformation(String name, String samplerName, TextureType target, PixelFormat format,
							   InternalTextureFormat internalTextureFormat,
							   PixelType type, int width, int height, int depth, boolean clear, boolean isRelative,
							   float relativeWidth, float relativeHeight) {
}
