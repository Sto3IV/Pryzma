package net.pryzma.mixin;

import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.pryzma.shader.PrShaderMacros;
import net.pryzma.shader.PrShaderPipeline;
import net.pryzma.shader.PrShaders;

/**
 * The shader pack pipeline around the hand: it draws into the gbuffers with its depth compressed
 * into OptiFine's hand range ({@code MC_HAND_DEPTH}) instead of over a cleared depth buffer, and
 * the composite and final passes run once it is drawn.
 */
@Mixin(GameRenderer.class)
abstract class GameRendererShaderMixin {
    @WrapWithCondition(method = "renderLevel", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;clear(IZ)V"))
    private boolean prShaderKeepDepth(int mask, boolean checkError) {
        return !PrShaders.rendering();
    }

    @WrapMethod(method = "renderItemInHand")
    private void prShaderHand(Camera camera, float partialTick, Matrix4f projection, Operation<Void> original) {
        PrShaders.beginHand();
        try {
            original.call(camera, partialTick, projection);
        } finally {
            PrShaders.phase(PrShaderPipeline.Phase.NONE);
        }
    }

    @ModifyArg(method = "renderItemInHand", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;resetProjectionMatrix(Lorg/joml/Matrix4f;)V"))
    private Matrix4f prShaderHandDepth(Matrix4f projection) {
        return PrShaders.rendering() ? new Matrix4f().scale(1.0F, 1.0F, PrShaderMacros.HAND_DEPTH).mul(projection) : projection;
    }

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void prShaderFinish(DeltaTracker delta, CallbackInfo ci) {
        PrShaders.finishFrame();
    }
}
