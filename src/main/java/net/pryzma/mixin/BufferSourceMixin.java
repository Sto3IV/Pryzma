package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.pryzma.entity.model.PrCemRender;
import net.pryzma.item.PrCitGlint;

/**
 * Buffer requests of the main buffer source: CEM parts with their own texture draw only in the
 * pass of the model's own render type, and glint requests of items with custom enchantment
 * layers are answered with those layers, which are drawn right after the vanilla glints.
 */
@Mixin(MultiBufferSource.BufferSource.class)
abstract class BufferSourceMixin {
    @Inject(method = "getBuffer", at = @At("HEAD"), cancellable = true)
    private void prTrackRenderType(RenderType type, CallbackInfoReturnable<VertexConsumer> cir) {
        if (PrCitGlint.bypassing()) {
            return;
        }
        PrCemRender.onBufferRequested(type);
        VertexConsumer layers = PrCitGlint.redirect((MultiBufferSource.BufferSource) (Object) this, type);
        if (layers != null) {
            cir.setReturnValue(layers);
        }
    }

    @Inject(method = "endBatch(Lnet/minecraft/client/renderer/RenderType;)V", at = @At("RETURN"))
    private void prFlushGlintLayers(RenderType type, CallbackInfo ci) {
        if (type == RenderType.entityGlintDirect()) {
            PrCitGlint.flush((MultiBufferSource.BufferSource) (Object) this);
        }
    }
}
