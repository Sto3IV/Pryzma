package net.pryzma.mixin;

import java.util.concurrent.Executor;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.ParticleOptions;
import net.pryzma.PryzmaConfig;
import net.pryzma.perf.PrChunkWorkers;
import net.pryzma.render.PrRenderHooks;

/** Rain and Snow, Rain Splash, Cloud Height, the particle switches and the chunk worker pool. */
@Mixin(LevelRenderer.class)
abstract class LevelRendererOptionsMixin {
    @Inject(method = "renderSnowAndRain", at = @At("HEAD"), cancellable = true)
    private void prRainOff(LightTexture lightTexture, float partialTick, double camX, double camY, double camZ, CallbackInfo ci) {
        if (PrRenderHooks.isRainOff()) {
            ci.cancel();
        }
    }

    /** The rain radius: 5 blocks Fast, 10 Fancy. */
    @ModifyExpressionValue(method = "renderSnowAndRain",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;useFancyGraphics()Z"))
    private boolean prRainRadius(boolean fancy) {
        return PrRenderHooks.isRainFancy(fancy);
    }

    @Inject(method = "tickRain", at = @At("HEAD"), cancellable = true)
    private void prRainSplash(CallbackInfo ci) {
        if (!PryzmaConfig.prRainSplash || PrRenderHooks.isRainOff()) {
            ci.cancel();
        }
    }

    /** 1.x halves the splash rate once more whenever rain is not Fancy. */
    @ModifyExpressionValue(method = "tickRain",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getRainLevel(F)F"))
    private float prRainSplashRate(float rain) {
        return PrRenderHooks.isRainFancy(net.minecraft.client.Minecraft.useFancyGraphics()) ? rain : rain / 2.0F;
    }

    /** Cloud Height raises the clouds by up to 128 blocks. */
    @ModifyExpressionValue(method = "renderClouds",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/DimensionSpecialEffects;getCloudHeight()F"))
    private float prCloudHeight(float height) {
        return Float.isNaN(height) ? height : height + (float) (PryzmaConfig.prCloudsHeight * 128.0);
    }

    @Inject(method = "addParticleInternal(Lnet/minecraft/core/particles/ParticleOptions;ZZDDDDDD)Lnet/minecraft/client/particle/Particle;",
            at = @At("HEAD"), cancellable = true)
    private void prParticleSwitches(ParticleOptions options, boolean force, boolean decreased, double x, double y, double z,
            double xSpeed, double ySpeed, double zSpeed, CallbackInfoReturnable<Particle> cir) {
        if (!PrRenderHooks.isParticleEnabled(options)) {
            cir.setReturnValue(null);
        }
    }

    /** Trees: leaf transparency follows the Trees option rather than the graphics mode. */
    @ModifyArg(method = "allChanged", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemBlockRenderTypes;setFancy(Z)V"))
    private boolean prTreesFancy(boolean fancy) {
        return PrRenderHooks.isTreesFancy(fancy);
    }

    /** Chunk Updates: section builds run on the Pryzma worker pool. */
    @ModifyArg(method = "allChanged", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher;<init>(Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/client/renderer/LevelRenderer;Ljava/util/concurrent/Executor;Lnet/minecraft/client/renderer/RenderBuffers;Lnet/minecraft/client/renderer/block/BlockRenderDispatcher;Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher;)V"),
            index = 2)
    private Executor prChunkWorkers(Executor vanilla) {
        return PrChunkWorkers.executor();
    }
}
