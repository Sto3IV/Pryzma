package net.pryzma.mixin;

import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.pryzma.color.PryzmaColormaps;

/** Custom fog and fluid fog colormaps. */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
    @Inject(method = "computeFogColor", at = @At("TAIL"))
    private void prCustomFogColor(Camera camera, float partialTicks, ClientLevel level, int renderDistanceChunks,
            float bossColorModifier, Vector4f fogColor, CallbackInfo ci) {
        FogType fluid = camera.getFluidInCamera();
        Vec3 pos = camera.position();
        if (fluid == FogType.WATER) {
            Vec3 color = PryzmaColormaps.underwaterColor(level, pos.x, pos.y, pos.z);
            if (color != null) {
                fogColor.set((float) color.x, (float) color.y, (float) color.z, fogColor.w);
            }
        } else if (fluid == FogType.LAVA) {
            Vec3 color = PryzmaColormaps.underlavaColor(level, pos.x, pos.y, pos.z);
            if (color != null) {
                fogColor.set((float) color.x, (float) color.y, (float) color.z, fogColor.w);
            }
        } else {
            Vec3 orig = new Vec3(fogColor.x, fogColor.y, fogColor.z);
            Vec3 custom = PryzmaColormaps.fogColor(orig, level, pos.x, pos.y, pos.z);
            if (custom != orig && custom != null) {
                fogColor.set((float) custom.x, (float) custom.y, (float) custom.z, fogColor.w);
            }
        }
    }
}
