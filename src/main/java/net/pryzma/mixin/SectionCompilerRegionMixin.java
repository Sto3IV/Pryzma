package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.pryzma.perf.PrDebugTracker;

/**
 * Tracks chunk section compilation for F3 and Quick Info counters.
 */
@Mixin(SectionCompiler.class)
abstract class SectionCompilerRegionMixin {
    @Inject(method = "compile", at = @At("RETURN"))
    private void prOnSectionCompiled(CallbackInfoReturnable<SectionCompiler.Results> cir) {
        PrDebugTracker.onSectionCompiled();
    }
}
