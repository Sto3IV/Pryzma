package net.pryzma.shader.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.platform.GlStateManager;
import net.pryzma.shader.gl.state.GlBindingCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skips framebuffer and program binds that would not change GL state; see {@link GlBindingCache}. Only the GL
 * call itself is conditional: vanilla's render thread assertion and every other mixin's injections into these
 * methods still run, and no CallbackInfo is allocated per bind (a cancellable inject would allocate one on
 * every draw).
 */
@Mixin(GlStateManager.class)
public class MixinGlStateManager_FramebufferBinding {
	@WrapWithCondition(method = "_glBindFramebuffer(II)V", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL30;glBindFramebuffer(II)V"), remap = false)
	private static boolean iris$avoidRedundantBind(int target, int framebuffer) {
		return GlBindingCache.INSTANCE.bindFramebuffer(target, framebuffer);
	}

	@WrapWithCondition(method = "_glUseProgram", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL20;glUseProgram(I)V"), remap = false)
	private static boolean iris$avoidRedundantBind2(int program) {
		return GlBindingCache.INSTANCE.useProgram(program);
	}

	@Inject(method = "glDeleteProgram", at = @At("HEAD"), remap = false)
	private static void iris$trackProgramDelete(int program, CallbackInfo ci) {
		GlBindingCache.INSTANCE.deleteProgram(program);
	}

	@Inject(method = "_glDeleteFramebuffers(I)V", at = @At("HEAD"), remap = false)
	private static void iris$trackFramebufferDelete(int framebuffer, CallbackInfo ci) {
		GlBindingCache.INSTANCE.deleteFramebuffer(framebuffer);
	}
}
