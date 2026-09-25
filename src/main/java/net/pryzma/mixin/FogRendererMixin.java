package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.pryzma.color.PryzmaColormaps;

/** Fog colormap, Nether and End fog colours, and underwater / under-lava fog colormaps. */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
    @Shadow private static float fogRed;
    @Shadow private static float fogGreen;
    @Shadow private static float fogBlue;

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
