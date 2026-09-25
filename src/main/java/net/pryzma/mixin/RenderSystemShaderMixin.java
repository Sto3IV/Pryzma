package net.pryzma.mixin;

import java.util.function.Supplier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.renderer.ShaderInstance;
import net.pryzma.shader.PrShaders;

/**
 * Every shader the game selects while the world renders passes here: with a shader pack active
 * it is swapped for the pack's gbuffers program of the same purpose.
 */
@Mixin(value = RenderSystem.class, remap = false)
abstract class RenderSystemShaderMixin {
    @Shadow
    private static ShaderInstance shader;

    @Inject(method = "setShader", at = @At("TAIL"))
    private static void prShaderPack(Supplier<ShaderInstance> supplier, CallbackInfo ci) {
        if (PrShaders.rendering() && RenderSystem.isOnRenderThread()) {
            shader = PrShaders.substitute(shader);
        }
    }
}
