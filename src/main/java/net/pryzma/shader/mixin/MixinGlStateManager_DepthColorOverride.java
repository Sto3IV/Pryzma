package net.pryzma.shader.mixin;

import com.mojang.renderpearl.backend.opengl.GlStateManager;
import net.pryzma.shader.gl.blending.DepthColorStorage;
import net.pryzma.shader.vertices.ImmediateState;
import org.lwjgl.opengl.GL43C;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GlStateManager.class)
public class MixinGlStateManager_DepthColorOverride {
	@Inject(method = "_colorMask(I)V", at = @At("HEAD"), cancellable = true, remap = false)
	private static void pryzma$colorMaskLock(int mask, CallbackInfo ci) {
		if (DepthColorStorage.isDepthColorLocked()) {
			boolean red = (mask & 1) != 0;
			boolean green = (mask & 2) != 0;
			boolean blue = (mask & 4) != 0;
			boolean alpha = (mask & 8) != 0;
			DepthColorStorage.deferColorMask(red, green, blue, alpha);
			ci.cancel();
		}
	}

	@Inject(method = "_colorMask(II)V", at = @At("HEAD"), cancellable = true, remap = false)
	private static void pryzma$colorMaskLockIndexed(int index, int mask, CallbackInfo ci) {
		if (DepthColorStorage.isDepthColorLocked()) {
			boolean red = (mask & 1) != 0;
			boolean green = (mask & 2) != 0;
			boolean blue = (mask & 4) != 0;
			boolean alpha = (mask & 8) != 0;
			DepthColorStorage.deferColorMask(red, green, blue, alpha);
			ci.cancel();
		}
	}

	@Inject(method = "_depthMask", at = @At("HEAD"), cancellable = true, remap = false)
	private static void pryzma$depthMaskLock(boolean enable, CallbackInfo ci) {
		if (DepthColorStorage.isDepthColorLocked()) {
			DepthColorStorage.deferDepthEnable(enable);
			ci.cancel();
		}
	}

	@Inject(method = "_drawElements", at = @At("HEAD"), cancellable = true, remap = false)
	private static void pryzma$modify(int mode, int count, int type, long indices, CallbackInfo ci) {
		if (mode == GL43C.GL_TRIANGLES && ImmediateState.usingTessellation) {
			GL43C.glDrawElements(GL43C.GL_PATCHES, count, type, indices);
			ci.cancel();
		}
	}

	@Inject(method = "_glUseProgram", at = @At("TAIL"), remap = false)
	private static void pryzma$resetTessellation(int pInt0, CallbackInfo ci) {
		ImmediateState.usingTessellation = false;
	}
}
