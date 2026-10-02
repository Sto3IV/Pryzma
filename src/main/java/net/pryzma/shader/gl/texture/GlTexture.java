package net.pryzma.shader.gl.texture;

import com.mojang.blaze3d.platform.GlStateManager;
import net.pryzma.shader.gl.GlResource;
import net.pryzma.shader.gl.ShaderRenderSystem;
import net.pryzma.shader.shaderpack.texture.TextureFilteringData;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.function.IntSupplier;

public class GlTexture extends GlResource implements TextureAccess {
	private final TextureType target;

	public GlTexture(TextureType target, int sizeX, int sizeY, int sizeZ, int internalFormat, int format, int pixelType, byte[] pixels, TextureFilteringData filteringData) {
		super(GlStateManager._genTexture());
		ShaderRenderSystem.bindTextureForSetup(target.getGlType(), getGlId());

		TextureUploadHelper.resetTextureUploadState();

		ByteBuffer buffer = MemoryUtil.memAlloc(pixels.length);
		buffer.put(pixels);
		buffer.flip();
		target.apply(this.getGlId(), sizeX, sizeY, sizeZ, internalFormat, format, pixelType, buffer);
		MemoryUtil.memFree(buffer);

		int texture = this.getGlId();

		ShaderRenderSystem.texParameteri(texture, target.getGlType(), GL11C.GL_TEXTURE_MIN_FILTER, filteringData.shouldBlur() ? GL11C.GL_LINEAR : GL11C.GL_NEAREST);
		ShaderRenderSystem.texParameteri(texture, target.getGlType(), GL11C.GL_TEXTURE_MAG_FILTER, filteringData.shouldBlur() ? GL11C.GL_LINEAR : GL11C.GL_NEAREST);
		ShaderRenderSystem.texParameteri(texture, target.getGlType(), GL11C.GL_TEXTURE_WRAP_S, filteringData.shouldClamp() ? GL13C.GL_CLAMP_TO_EDGE : GL13C.GL_REPEAT);

		if (sizeY > 0) {
			ShaderRenderSystem.texParameteri(texture, target.getGlType(), GL11C.GL_TEXTURE_WRAP_T, filteringData.shouldClamp() ? GL13C.GL_CLAMP_TO_EDGE : GL13C.GL_REPEAT);
		}

		if (sizeZ > 0) {
			ShaderRenderSystem.texParameteri(texture, target.getGlType(), GL30C.GL_TEXTURE_WRAP_R, filteringData.shouldClamp() ? GL13C.GL_CLAMP_TO_EDGE : GL13C.GL_REPEAT);
		}

		ShaderRenderSystem.texParameteri(texture, target.getGlType(), GL20C.GL_TEXTURE_MAX_LEVEL, 0);
		ShaderRenderSystem.texParameteri(texture, target.getGlType(), GL20C.GL_TEXTURE_MIN_LOD, 0);
		ShaderRenderSystem.texParameteri(texture, target.getGlType(), GL20C.GL_TEXTURE_MAX_LOD, 0);
		ShaderRenderSystem.texParameterf(texture, target.getGlType(), GL20C.GL_TEXTURE_LOD_BIAS, 0.0F);

		ShaderRenderSystem.bindTextureForSetup(target.getGlType(), 0);

		this.target = target;
	}

	public TextureType getTarget() {
		return target;
	}

	public void bind(int unit) {
		ShaderRenderSystem.bindTextureToUnit(target.getGlType(), unit, getGlId());
	}

	@Override
	public TextureType getType() {
		return target;
	}

	@Override
	public IntSupplier getTextureId() {
		return this::getGlId;
	}

	@Override
	protected void destroyInternal() {
		GlStateManager._deleteTexture(getGlId());
	}
}
