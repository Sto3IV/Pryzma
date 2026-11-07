package net.pryzma.shader.pbr;

import com.mojang.renderpearl.backend.opengl.GlStateManager;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL20C;

import java.nio.ByteBuffer;

public class TextureInfoCache {
	public static final TextureInfoCache INSTANCE = new TextureInfoCache();

	private final Int2ObjectMap<TextureInfo> cache = new Int2ObjectOpenHashMap<>();

	private TextureInfoCache() {

	}

	public TextureInfo getInfo(int id) {
		TextureInfo info = cache.get(id);
		if (info == null) {
			info = new TextureInfo(id);
			cache.put(id, info);
		}
		return info;
	}

	public void onTexImage2D(int target, int level, int internalformat, int width, int height, int border,
							 int format, int type, @Nullable ByteBuffer pixels) {
		if (level == 0) {
			int id = GlStateManager._getInteger(GL20C.GL_TEXTURE_BINDING_2D);
			TextureInfo info = getInfo(id);
			info.internalFormat = internalformat;
			info.width = width;
			info.height = height;
		}
	}

	public void onDeleteTexture(int id) {
		cache.remove(id);
	}

	public static class TextureInfo {
		private final int id;
		private int internalFormat = -1;
		private int width = -1;
		private int height = -1;

		private TextureInfo(int id) {
			this.id = id;
		}

		public int getId() {
			return id;
		}

		public int getInternalFormat() {
			return internalFormat;
		}

		public int getWidth() {
			return width;
		}

		public int getHeight() {
			return height;
		}
	}
}
