package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.CloudStatus;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.pryzma.PryzmaConfig;

/**
 * Clouds: Below 4 chunks render distance clouds are never drawn.
 * Fast, Fancy and Off override vanilla; Default follows cloudStatus setting.
 */
@Mixin(Options.class)
abstract class OptionsCloudsMixin {
    @Shadow
    public abstract int getEffectiveRenderDistance();

    @Shadow
    public abstract OptionInstance<CloudStatus> cloudStatus();

    @Inject(method = "getCloudStatus", at = @At("HEAD"), cancellable = true)
    private void prCloudsType(CallbackInfoReturnable<CloudStatus> cir) {
        if (this.getEffectiveRenderDistance() < 4) {
            cir.setReturnValue(CloudStatus.OFF);
            return;
        }
        CloudStatus status = switch (PryzmaConfig.prClouds) {
            case 1 -> CloudStatus.FAST;
            case 2 -> CloudStatus.FANCY;
            case 3 -> CloudStatus.OFF;
            default -> this.cloudStatus().get();
        };
        cir.setReturnValue(status);
    }
}
