package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.level.biome.Biome;
import net.pryzma.color.PryzmaColormaps;

/**
 * Swamp colours: the swamp grass/foliage colormaps, or plains colours while "Swamp Colors" is off.
 * These run inside vanilla's biome tint resolvers, so the results land in vanilla's tint caches.
 */
@Mixin(Biome.class)
public abstract class BiomeMixin {
    @Inject(method = "getGrassColor", at = @At("HEAD"), cancellable = true)
    private void prGrassColor(double x, double z, CallbackInfoReturnable<Integer> cir) {
        int color = PryzmaColormaps.grass((Biome) (Object) this, x, z);
        if (color != -1) {
            cir.setReturnValue(color);
        }
    }

    @Inject(method = "getFoliageColor", at = @At("HEAD"), cancellable = true)
    private void prFoliageColor(CallbackInfoReturnable<Integer> cir) {
        int color = PryzmaColormaps.foliage((Biome) (Object) this);
        if (color != -1) {
            cir.setReturnValue(color);
        }
    }

    @Inject(method = "getWaterColor", at = @At("HEAD"), cancellable = true)
    private void prWaterColor(CallbackInfoReturnable<Integer> cir) {
        int color = PryzmaColormaps.biomeWater((Biome) (Object) this);
        if (color != -1) {
            cir.setReturnValue(color);
        }
    }
}
