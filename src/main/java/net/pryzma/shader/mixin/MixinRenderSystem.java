package net.pryzma.shader.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.systems.RenderSystem;
import net.pryzma.shader.PryzmaShaders;
import net.pryzma.shader.gl.GLDebug;
import net.pryzma.shader.gl.ShaderRenderSystem;
import net.pryzma.shader.pbr.TextureTracker;
import net.pryzma.shader.samplers.ShaderSamplers;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderSystem.class)
public class MixinRenderSystem {
	@Inject(method = "initRenderer", at = @At("RETURN"), remap = false)
	private static void pryzma$onRendererInit(int debugVerbosity, boolean alwaysFalse, CallbackInfo ci) {
		PryzmaShaders.duringRenderSystemInit();
		GLDebug.reloadDebugState();
		ShaderRenderSystem.initRenderer();
		ShaderSamplers.initRenderer();
		PryzmaShaders.onRenderSystemInit();
	}

	@Inject(method = "_setShaderTexture(ILnet/minecraft/resources/Identifier;)V", at = @At("RETURN"), remap = false)
	private static void _setShaderTexture(int unit, Identifier resourceLocation, CallbackInfo ci, @Local AbstractTexture tex) {
		int id = tex != null && tex.getTexture() instanceof com.mojang.renderpearl.backend.opengl.GlTexture glTex ? glTex.glId() : 0;
		TextureTracker.INSTANCE.onSetShaderTexture(unit, id);
	}

	@Inject(method = "_setShaderTexture(II)V", at = @At("RETURN"), remap = false)
	private static void _setShaderTexture(int unit, int glId, CallbackInfo ci) {
		TextureTracker.INSTANCE.onSetShaderTexture(unit, glId);
	}
}
