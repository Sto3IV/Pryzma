package net.pryzma.mixin;

import java.util.function.Predicate;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import net.minecraft.client.Camera;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.culling.Frustum;
import net.pryzma.PryzmaConfig;
import net.pryzma.shader.PrShaderPipeline;
import net.pryzma.shader.PrShaders;

/**
 * Firework Particles off also drops the sparks a firework adds directly, as 1.x did; and a
 * shader pack draws particles with its particle program.
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

    @WrapMethod(method = "render(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;Ljava/util/function/Predicate;)V")
    private void prShaderParticles(LightTexture light, Camera camera, float partialTick, Frustum frustum,
            Predicate<ParticleRenderType> filter, Operation<Void> original) {
        PrShaderPipeline.Phase previous = PrShaders.phase();
        PrShaders.phase(PrShaderPipeline.Phase.PARTICLES);
        try {
            original.call(light, camera, partialTick, frustum, filter);
        } finally {
            PrShaders.phase(previous);
        }
    }
}
