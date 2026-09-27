package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.pryzma.PryzmaConfig;

/**
 * Firework Particles off also drops the sparks a firework adds directly, as 1.x did.
 */
@Mixin(ParticleEngine.class)
abstract class ParticleEngineMixin {
    /** FireworkParticles.SparkParticle is package-private; matched by name. */
    private static final String SPARK = "net.minecraft.client.particle.FireworkParticles$SparkParticle";

    @Inject(method = "add", at = @At("HEAD"), cancellable = true)
    private void prFireworkSparks(Particle particle, CallbackInfo ci) {
        if (!PryzmaConfig.prFireworkParticles && particle.getClass().getName().equals(SPARK)) {
            ci.cancel();
        }
    }
}
