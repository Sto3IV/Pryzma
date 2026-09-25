package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.pipeline.RenderTarget;

import net.minecraft.client.Minecraft;
import net.pryzma.shader.PrShaders;

/** While a shader pack renders the world, code that binds the game's main framebuffer gets the gbuffers. */
@Mixin(value = RenderTarget.class, remap = false)
abstract class RenderTargetShaderMixin {
    @Inject(method = "bindWrite", at = @At("HEAD"), cancellable = true)
    private void prShaderGbuffers(boolean setViewport, CallbackInfo ci) {
        if ((Object) this == Minecraft.getInstance().getMainRenderTarget() && PrShaders.redirectMainTarget()) {
            ci.cancel();
        }
    }
}
