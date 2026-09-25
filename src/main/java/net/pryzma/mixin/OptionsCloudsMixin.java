package net.pryzma.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.client.CloudStatus;
import net.minecraft.client.Options;
import net.pryzma.PryzmaConfig;

/** Clouds: Default keeps the vanilla setting, Fast, Fancy and Off override it. */
@Mixin(Options.class)
abstract class OptionsCloudsMixin {
    @ModifyReturnValue(method = "getCloudsType", at = @At("RETURN"))
    private CloudStatus prClouds(CloudStatus vanilla) {
        if (vanilla == CloudStatus.OFF) {
            // Below four chunks of render distance vanilla never draws clouds.
            return PryzmaConfig.prClouds == 3 ? CloudStatus.OFF : vanilla;
        }
        return switch (PryzmaConfig.prClouds) {
            case 1 -> CloudStatus.FAST;
            case 2 -> CloudStatus.FANCY;
            case 3 -> CloudStatus.OFF;
            default -> vanilla;
        };
    }
}
