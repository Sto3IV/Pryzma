package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.BlockAndLightGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.pryzma.light.PrDynamicLights;

/**
 * Injects dynamic lighting into block and coordinate light sampling.
 */
@Mixin(LightCoordsUtil.class)
abstract class LightCoordsUtilMixin {
    @Inject(method = "getLightCoords(Lnet/minecraft/util/LightCoordsUtil$BrightnessGetter;Lnet/minecraft/world/level/BlockAndLightGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I",
            at = @At("RETURN"), cancellable = true)
    private static void prDynamicLightBlockState(LightCoordsUtil.BrightnessGetter getter, BlockAndLightGetter level,
            BlockState state, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (PrDynamicLights.hasSources()) {
            cir.setReturnValue(PrDynamicLights.getCombinedLight(pos, cir.getReturnValue()));
        }
    }

    @Inject(method = "getLightCoords(Lnet/minecraft/world/level/BlockAndLightGetter;Lnet/minecraft/core/BlockPos;)I",
            at = @At("RETURN"), cancellable = true)
    private static void prDynamicLightPos(BlockAndLightGetter level, BlockPos pos,
            CallbackInfoReturnable<Integer> cir) {
        if (PrDynamicLights.hasSources()) {
            cir.setReturnValue(PrDynamicLights.getCombinedLight(pos, cir.getReturnValue()));
        }
    }
}
