package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.pryzma.render.PrRenderRegionManager;

/** Render Regions: a section's buffers follow it into the region at each new origin, construction included. */
@Mixin(SectionRenderDispatcher.RenderSection.class)
abstract class RenderSectionRegionMixin {
    @Inject(method = "setOrigin", at = @At("TAIL"))
    private void prRegionAssign(int x, int y, int z, CallbackInfo ci) {
        PrRenderRegionManager.assign((SectionRenderDispatcher.RenderSection) (Object) this, x, z);
    }
}
