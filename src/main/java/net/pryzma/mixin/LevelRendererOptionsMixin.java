package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.TracingExecutor;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.particles.ParticleOptions;
import net.pryzma.perf.PrChunkWorkers;
import net.pryzma.render.PrRenderHooks;

/** Particle switches, leaf fancy rendering and the chunk worker pool. */
@Mixin(LevelRenderer.class)
abstract class LevelRendererOptionsMixin {
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

    /** Chunk Updates: section builds run on the Pryzma worker pool, wherever the dispatcher is created. */
    @ModifyArg(method = "setLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher;<init>(Lnet/minecraft/TracingExecutor;Lnet/minecraft/client/renderer/RenderBuffers;Lnet/minecraft/client/renderer/chunk/SectionCompiler;Ljava/util/function/Consumer;)V"),
            index = 0)
    private TracingExecutor prChunkWorkers(TracingExecutor vanilla) {
        return new TracingExecutor(PrChunkWorkers.pool());
    }
}
