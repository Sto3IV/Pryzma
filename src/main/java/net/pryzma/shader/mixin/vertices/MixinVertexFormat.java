package net.pryzma.shader.mixin.vertices;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.pryzma.shader.PryzmaShaders;
import net.pryzma.shader.vertices.ImmediateState;
import net.pryzma.shader.vertices.ShaderVertexFormats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ensures that the correct state for the extended vertex format is set up when needed.
 */
@Mixin(VertexFormat.class)
public class MixinVertexFormat {
	@Inject(method = "setupBufferState", at = @At("HEAD"), cancellable = true)
	private void pryzma$onSetupBufferState(CallbackInfo ci) {
		if (PryzmaShaders.isPackInUseQuick() && ImmediateState.renderWithExtendedVertexFormat) {
			if ((Object) this == DefaultVertexFormat.BLOCK) {
				ShaderVertexFormats.TERRAIN.setupBufferState();

				ci.cancel();
			} else if ((Object) this == DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP) {
				ShaderVertexFormats.GLYPH.setupBufferState();

				ci.cancel();
			} else if ((Object) this == DefaultVertexFormat.NEW_ENTITY) {
				ShaderVertexFormats.ENTITY.setupBufferState();

				ci.cancel();
			}
		}
	}

	@Inject(method = "clearBufferState", at = @At("HEAD"), cancellable = true)
	private void pryzma$onClearBufferState(CallbackInfo ci) {
		if (PryzmaShaders.isPackInUseQuick() && ImmediateState.renderWithExtendedVertexFormat) {
			if ((Object) this == DefaultVertexFormat.BLOCK) {
				ShaderVertexFormats.TERRAIN.clearBufferState();

				ci.cancel();
			} else if ((Object) this == DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP) {
				ShaderVertexFormats.GLYPH.clearBufferState();

				ci.cancel();
			} else if ((Object) this == DefaultVertexFormat.NEW_ENTITY) {
				ShaderVertexFormats.ENTITY.clearBufferState();

				ci.cancel();
			}
		}
	}
}
