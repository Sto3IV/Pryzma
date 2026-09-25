package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.pryzma.render.PrRenderRegionManager;

/**
 * Render Regions follow the view area's lifetime: the option is read when its sections are created
 * (every toggle rebuilds them), and the regions are deleted with its buffers.
 */
@Mixin(ViewArea.class)
abstract class ViewAreaRegionMixin {
    @Inject(method = "createSections", at = @At("HEAD"))
    private void prRegionsBegin(SectionRenderDispatcher dispatcher, CallbackInfo ci) {
        PrRenderRegionManager.beginViewArea();
    }

    @Inject(method = "releaseAllBuffers", at = @At("TAIL"))
    private void prRegionsRelease(CallbackInfo ci) {
        PrRenderRegionManager.releaseAll();
    }
}
