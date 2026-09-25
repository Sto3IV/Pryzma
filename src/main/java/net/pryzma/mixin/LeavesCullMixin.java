package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.pryzma.render.PrSmartLeaves;

/** Trees: Smart leaves hide the faces between two plain-cube leaves of the same kind. */
@Mixin(BlockBehaviour.class)
abstract class LeavesCullMixin {
    @Inject(method = "skipRendering", at = @At("HEAD"), cancellable = true)
    private void prSmartLeaves(BlockState state, BlockState adjacent, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof LeavesBlock && PrSmartLeaves.culls(state, adjacent)) {
            cir.setReturnValue(true);
        }
    }
}
