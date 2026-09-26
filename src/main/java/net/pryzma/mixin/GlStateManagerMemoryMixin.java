package net.pryzma.mixin;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.platform.GlStateManager;

import net.pryzma.perf.PrDebugTracker;

/**
 * GPU memory for the F3 screen: buffer and 2D texture storage allocated through GlStateManager,
 * which is every vanilla buffer and texture, the render regions and the shader render targets.
 * Render thread.
 */
@Mixin(GlStateManager.class)
abstract class GlStateManagerMemoryMixin {
    @Inject(method = "_glBindBuffer(II)V", at = @At("HEAD"))
    private static void prBindBuffer(int target, int buffer, CallbackInfo ci) {
        PrDebugTracker.onBindBuffer(target, buffer);
    }

    @Inject(method = "_glBufferData(ILjava/nio/ByteBuffer;I)V", at = @At("HEAD"))
    private static void prBufferData(int target, ByteBuffer data, int usage, CallbackInfo ci) {
        PrDebugTracker.onBufferData(target, data.remaining());
    }

    @Inject(method = "_glBufferData(IJI)V", at = @At("HEAD"))
    private static void prBufferStorage(int target, long size, int usage, CallbackInfo ci) {
        PrDebugTracker.onBufferData(target, size);
    }

    @Inject(method = "_glDeleteBuffers(I)V", at = @At("HEAD"))
    private static void prDeleteBuffer(int buffer, CallbackInfo ci) {
        PrDebugTracker.onBufferDeleted(buffer);
    }

    /** Texture storage is allocated rarely, so the bound texture is read back instead of tracking every bind. */
    @Inject(method = "_texImage2D(IIIIIIIILjava/nio/IntBuffer;)V", at = @At("HEAD"))
    private static void prTexImage(int target, int level, int internalFormat, int width, int height, int border,
            int format, int type, IntBuffer pixels, CallbackInfo ci) {
        if (target == GL11.GL_TEXTURE_2D) {
            PrDebugTracker.onTexImage(GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D), level, internalFormat, width, height);
        }
    }

    @Inject(method = "_deleteTexture(I)V", at = @At("HEAD"))
    private static void prDeleteTexture(int texture, CallbackInfo ci) {
        PrDebugTracker.onTextureDeleted(texture);
    }

    @Inject(method = "_deleteTextures([I)V", at = @At("HEAD"))
    private static void prDeleteTextures(int[] textures, CallbackInfo ci) {
        for (int texture : textures) {
            PrDebugTracker.onTextureDeleted(texture);
        }
    }
}
