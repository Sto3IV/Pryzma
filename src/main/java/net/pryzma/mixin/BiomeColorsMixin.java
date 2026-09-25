package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.pryzma.color.PryzmaColormaps;

/** The water colormap. Water fluid, cauldrons and water particles all read this method. */
@Mixin(BiomeColors.class)
public abstract class BiomeColorsMixin {
    @Inject(method = "getAverageWaterColor", at = @At("HEAD"), cancellable = true)
    private static void prWaterColor(BlockAndTintGetter level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        int color = PryzmaColormaps.waterColor(level, pos);
        if (color != -1) {
            cir.setReturnValue(color);
        }
    }
}
