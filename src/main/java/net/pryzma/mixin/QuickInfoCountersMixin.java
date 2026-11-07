package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.LevelRenderer;
import net.pryzma.gui.PrQuickInfoStats;

/** Counts the block entities drawn each frame, for Quick Info. */
@Mixin(LevelRenderer.class)
abstract class QuickInfoCountersMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void prResetBlockEntities(CallbackInfo ci) {
        PrQuickInfoStats.blockEntitiesRendered = 0;
    }

    @Inject(method = "submitBlockEntities", at = @At("HEAD"))
    private void prCountBlockEntity(CallbackInfo ci) {
        PrQuickInfoStats.blockEntitiesRendered++;
    }
}
