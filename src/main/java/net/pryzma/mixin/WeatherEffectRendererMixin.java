package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.renderpearl.api.commands.RenderPass;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.state.level.WeatherRenderState;
import net.minecraft.world.phys.Vec3;
import net.pryzma.render.PrRenderHooks;

@Mixin(WeatherEffectRenderer.class)
abstract class WeatherEffectRendererMixin {
    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void prWeatherOff(ClientLevel level, float partialTicks, Vec3 cameraPos, WeatherRenderState state, CallbackInfo ci) {
        if (PrRenderHooks.isRainOff()) {
            state.radius = 0;
            state.intensity = 0.0F;
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void prRenderOff(WeatherRenderState state, RenderPass renderPass, CallbackInfo ci) {
        if (PrRenderHooks.isRainOff()) {
            ci.cancel();
        }
    }
}
