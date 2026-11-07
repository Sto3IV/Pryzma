package net.pryzma.shader.mixin;

import com.mojang.renderpearl.backend.opengl.GlStateManager;
import org.joml.Vector4i;

public final class GlStateManagerAccessor {
	private GlStateManagerAccessor() {}

	public static GlStateManager.TextureState[] getTEXTURES() {
		return GlStateManager.TEXTURES;
	}

	public static GlStateManager.BlendState getBLEND() {
		return GlStateManager.BLEND;
	}

	public static int getActiveTexture() {
		return GlStateManager.activeTexture;
	}

	public static int getBoundTexture(int unit) {
		GlStateManager.TextureState[] textures = GlStateManager.TEXTURES;
		if (textures != null && unit >= 0 && unit < textures.length && textures[unit] != null) {
			return textures[unit].binding;
		}
		return 0;
	}

	public static Vector4i getBlendFunc() {
		if (GlStateManager.BLEND_ENABLE != null && GlStateManager.BLEND_ENABLE.length > 0 && GlStateManager.BLEND_ENABLE[0]) {
			GlStateManager.BlendState blend = GlStateManager.BLEND;
			return new Vector4i(blend.srcRgb, blend.dstRgb, blend.srcAlpha, blend.dstAlpha);
		}
		return new Vector4i(0, 0, 0, 0);
	}
}
