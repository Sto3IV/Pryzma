package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.CloudStatus;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.world.phys.Vec3;
import net.pryzma.PryzmaConfig;
import net.pryzma.render.PrCloudRenderer;

/**
 * CloudRenderer optimization and Infinite Clouds expansion.
 */
@Mixin(CloudRenderer.class)
abstract class CloudRendererMixin {
    @Shadow
    public abstract void markForRebuild();

    @Inject(method = "prepare(ILnet/minecraft/client/CloudStatus;FILnet/minecraft/world/phys/Vec3;JF)V", at = @At("HEAD"))
    private void prCheckDirty(int color, CloudStatus cloudStatus, float bottomY, int range, Vec3 cameraPosition,
            long gameTime, float partialTicks, CallbackInfo ci) {
        if (PrCloudRenderer.consumeDirty()) {
            this.markForRebuild();
        }
    }

    @ModifyVariable(method = "prepare(ILnet/minecraft/client/CloudStatus;FILnet/minecraft/world/phys/Vec3;JF)V", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int prInfiniteCloudRange(int range) {
        if (PryzmaConfig.prInfiniteClouds) {
            return Math.max(range, 64);
        }
        return range;
    }
}
