package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.color.block.BlockColors;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.pryzma.color.PryzmaColormaps;

/** Block palettes, pine and birch leaves, lily pads and stems from custom colormaps. */
@Mixin(BlockColors.class)
public abstract class BlockColorsMixin {
    @Inject(method = "getColor(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;I)I",
            at = @At("HEAD"), cancellable = true)
    private void prCustomBlockColor(BlockState state, BlockAndTintGetter level, BlockPos pos, int tintIndex,
            CallbackInfoReturnable<Integer> cir) {
        int color = PryzmaColormaps.blockColor(state, level, pos);
        if (color != -1) {
            cir.setReturnValue(color);
        }
    }
}
