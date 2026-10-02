package net.pryzma.shader.mixin.texture;

import com.mojang.blaze3d.platform.GlStateManager;
import net.pryzma.shader.pbr.TextureInfoCache;
import net.pryzma.shader.pbr.TextureTracker;
import net.pryzma.shader.pbr.texture.PBRTextureManager;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.IntBuffer;

@Mixin(GlStateManager.class)
public class MixinGlStateManager {
	@Inject(method = "_texImage2D(IIIIIIIILjava/nio/IntBuffer;)V", at = @At("TAIL"), remap = false)
	private static void pryzma$onTexImage2D(int target, int level, int internalformat, int width, int height, int border,
										  int format, int type, @Nullable IntBuffer pixels, CallbackInfo ci) {
		TextureInfoCache.INSTANCE.onTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
	}

	@Inject(method = "_deleteTexture(I)V", at = @At("TAIL"), remap = false)
	private static void pryzma$onDeleteTexture(int id, CallbackInfo ci) {
		pryzma$onDeleteTexture(id);
	}

	@Inject(method = "_deleteTextures([I)V", at = @At("TAIL"), remap = false)
	private static void pryzma$onDeleteTextures(int[] ids, CallbackInfo ci) {
		for (int id : ids) {
			pryzma$onDeleteTexture(id);
		}
	}

	@Unique
	private static void pryzma$onDeleteTexture(int id) {
		TextureTracker.INSTANCE.onDeleteTexture(id);
		TextureInfoCache.INSTANCE.onDeleteTexture(id);
		PBRTextureManager.INSTANCE.onDeleteTexture(id);
	}
}
