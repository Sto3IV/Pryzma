package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.multiplayer.ClientLevel;
import net.pryzma.PryzmaConfig;
import net.pryzma.render.PrRenderHooks;

@Mixin(ClientLevel.class)
abstract class ClientLevelWeatherMixin {
    @Inject(method = "tickWeatherEffects", at = @At("HEAD"), cancellable = true)
    private void prWeatherSplash(CallbackInfo ci) {
        if (!PryzmaConfig.prRainSplash || PrRenderHooks.isRainOff()) {
            ci.cancel();
        }
    }
}
