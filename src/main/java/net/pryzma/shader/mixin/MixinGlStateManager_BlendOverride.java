package net.pryzma.shader.mixin;

import com.mojang.renderpearl.backend.opengl.GlStateManager;
import net.pryzma.shader.gl.blending.BlendModeStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GlStateManager.class, remap = false)
public class MixinGlStateManager_BlendOverride {
	@Inject(method = "_disableBlend(I)V", at = @At("HEAD"), cancellable = true)
	private static void pryzma$blendDisableLock(int index, CallbackInfo ci) {
		if (BlendModeStorage.isBlendLocked()) {
			BlendModeStorage.deferBlendModeToggle(false);
			ci.cancel();
		}
	}

	@Inject(method = "_enableBlend(I)V", at = @At("HEAD"), cancellable = true)
	private static void pryzma$blendEnableLock(int index, CallbackInfo ci) {
		if (BlendModeStorage.isBlendLocked()) {
			BlendModeStorage.deferBlendModeToggle(true);
			ci.cancel();
		}
	}

	@Inject(method = "_blendFuncSeparate(IIII)V", at = @At("HEAD"), cancellable = true)
	private static void pryzma$blendFuncSeparateLock(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha, CallbackInfo ci) {
		if (BlendModeStorage.isBlendLocked()) {
			BlendModeStorage.deferBlendFunc(srcRgb, dstRgb, srcAlpha, dstAlpha);
			ci.cancel();
		}
	}
}
