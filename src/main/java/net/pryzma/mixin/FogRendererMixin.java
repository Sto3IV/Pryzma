package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.util.CubicSampler;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.pryzma.Pryzma;
import net.pryzma.color.PryzmaColormaps;
import net.pryzma.render.PrBiomeCache;
import net.pryzma.render.PrBiomeLattice;

/** Fog colormap, Nether and End fog colours, and underwater / under-lava fog colormaps. */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
    @Shadow private static float fogRed;
    @Shadow private static float fogGreen;
    @Shadow private static float fogBlue;

    @Unique private static final PrBiomeLattice PR_FOG_LATTICE = new PrBiomeLattice();
    @Unique private static Class<?> prExpectedFogFetcherClass;
    @Unique private static boolean prFogFallbackLogged;

    @WrapOperation(method = "setupColor", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/CubicSampler;gaussianSampleVec3(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/util/CubicSampler$Vec3Fetcher;)Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 prFogLattice(Vec3 pos, CubicSampler.Vec3Fetcher fetcher, Operation<Vec3> original,
            @Local(argsOnly = true) ClientLevel level) {
        if (!com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread()) {
            return original.call(pos, fetcher);
        }
        if (prExpectedFogFetcherClass == null) {
            prExpectedFogFetcherClass = fetcher.getClass();
        } else if (fetcher.getClass() != prExpectedFogFetcherClass) {
            if (!prFogFallbackLogged) {
                prFogFallbackLogged = true;
                Pryzma.LOGGER.info("[Pryzma] Third-party mod modified fog Vec3Fetcher ({}); falling back to vanilla sampler.", fetcher.getClass().getName());
            }
            return original.call(pos, fetcher);
        }
        return PR_FOG_LATTICE.sample(pos, fetcher, level.getBiomeManager(), PrBiomeCache.epoch());
    }

    @ModifyExpressionValue(method = "setupColor", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/CubicSampler;gaussianSampleVec3(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/util/CubicSampler$Vec3Fetcher;)Lnet/minecraft/world/phys/Vec3;"))
    private static Vec3 prCustomFogColor(Vec3 original, @Local(argsOnly = true) Camera camera,
            @Local(argsOnly = true) ClientLevel level) {
        Vec3 pos = camera.getPosition();
        return PryzmaColormaps.fogColor(original, level, pos.x, pos.y, pos.z);
    }

    @Inject(method = "setupColor", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;getMinBuildHeight()I"))
    private static void prCustomFluidFog(Camera camera, float partialTicks, ClientLevel level, int renderDistanceChunks,
            float bossColorModifier, CallbackInfo ci) {
        FogType fluid = camera.getFluidInCamera();
        Vec3 color = null;
        Vec3 pos = camera.getPosition();
        if (fluid == FogType.WATER) {
            color = PryzmaColormaps.underwaterColor(level, pos.x, pos.y, pos.z);
        } else if (fluid == FogType.LAVA) {
            color = PryzmaColormaps.underlavaColor(level, pos.x, pos.y, pos.z);
        }
        if (color != null) {
            fogRed = (float) color.x;
            fogGreen = (float) color.y;
            fogBlue = (float) color.z;
        }
    }
}
