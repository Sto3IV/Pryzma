package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.level.block.RedStoneWireBlock;
import net.pryzma.color.PryzmaColormaps;

/** {@code redstone.png}: wire tint and dust particles both come from this method. */
@Mixin(RedStoneWireBlock.class)
public abstract class RedStoneWireBlockMixin {
    @Inject(method = "getColorForPower", at = @At("HEAD"), cancellable = true)
    private static void prRedstoneColor(int power, CallbackInfoReturnable<Integer> cir) {
        int color = PryzmaColormaps.redstone(power);
        if (color != -1) {
            cir.setReturnValue(color);
        }
    }
}
