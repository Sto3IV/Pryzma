package net.pryzma.shader.pbr;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.pryzma.shader.PryzmaShaders;
import net.pryzma.shader.gl.ShaderRenderSystem;
import net.pryzma.shader.gl.state.StateUpdateNotifiers;
import net.pryzma.shader.gl.texture.TextureType;
import net.pryzma.shader.pipeline.WorldRenderingPipeline;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.jetbrains.annotations.Nullable;

public class TextureTracker {
	public static final TextureTracker INSTANCE = new TextureTracker();

	private static Runnable bindTextureListener;

	static {
		StateUpdateNotifiers.bindTextureNotifier = listener -> bindTextureListener = listener;
	}

	private final Int2ObjectMap<AbstractTexture> textures = new Int2ObjectOpenHashMap<>();
	private final int[] shaderTextures = new int[16];

	private boolean lockBindCallback;

	private TextureTracker() {
	}

	public static int getTextureId(AbstractTexture texture) {
		if (texture == null) {
			return 0;
		}
		if (texture.getTexture() instanceof com.mojang.renderpearl.backend.opengl.GlTexture glTex) {
			return glTex.glId();
		}
		return 0;
	}

	public int getShaderTexture(int unit) {
		return unit >= 0 && unit < shaderTextures.length ? shaderTextures[unit] : 0;
	}

	public void trackTexture(int id, AbstractTexture texture) {
		textures.put(id, texture);
	}

	@Nullable
	public AbstractTexture getTexture(int id) {
		return textures.get(id);
	}

	public void onSetShaderTexture(int unit, int id) {
		if (unit >= 0 && unit < shaderTextures.length) {
			shaderTextures[unit] = id;
		}
		if (lockBindCallback) {
			return;
		}
		if (unit == 0) {
			lockBindCallback = true;
			if (bindTextureListener != null) {
				bindTextureListener.run();
			}
			WorldRenderingPipeline pipeline = PryzmaShaders.getPipelineManager().getPipelineNullable();
			if (pipeline != null) {
				pipeline.onSetShaderTexture(id);
			}
			// Reset texture state
			ShaderRenderSystem.bindTextureToUnit(TextureType.TEXTURE_2D.getGlType(), 0, id);
			lockBindCallback = false;
		}
	}

	public void onDeleteTexture(int id) {
		textures.remove(id);
	}
}
