package net.pryzma.shader.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.backend.opengl.GlTexture;
import net.pryzma.shader.gl.GLDebug;
import net.pryzma.shader.targets.Blaze3dRenderTargetExt;
import org.lwjgl.opengl.GL43C;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Allows PryzmaShaders to detect when the depth texture was re-created, so we can re-attach it
 * to the shader framebuffers. See DeferredWorldRenderingPipeline and RenderTargets.
 */
@Mixin(RenderTarget.class)
public class MixinRenderTarget implements Blaze3dRenderTargetExt {
	@Unique
	private int pryzma$depthBufferVersion;
	@Unique
	private int pryzma$colorBufferVersion;

	@Inject(method = "destroyBuffers()V", at = @At("HEAD"))
	private void pryzma$onDestroyBuffers(CallbackInfo ci) {
		pryzma$depthBufferVersion++;
		pryzma$colorBufferVersion++;
	}

	@Inject(method = "createBuffers(II)V", at = @At(value = "RETURN"))
	private void nameDepthBuffer(int i, int j, CallbackInfo ci) {
		RenderTarget self = (RenderTarget) (Object) this;
		if (self.getDepthTexture() instanceof GlTexture glDepth) {
			GLDebug.nameObject(GL43C.GL_TEXTURE, glDepth.glId(), "Main depth texture");
		}
		if (self.getColorTexture() instanceof GlTexture glColor) {
			GLDebug.nameObject(GL43C.GL_TEXTURE, glColor.glId(), "Main color texture");
		}
	}

	@Override
	public int pryzma$getDepthBufferVersion() {
		return pryzma$depthBufferVersion;
	}

	@Override
	public int pryzma$getColorBufferVersion() {
		return pryzma$colorBufferVersion;
	}
}
