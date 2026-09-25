package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import net.pryzma.perf.PrFrustumCulling;

/**
 * Frustum culling of boxes with non-finite bounds: they are clamped around the camera before the
 * plane test instead of turning it into {@code NaN} comparisons (see {@link PrFrustumCulling}).
 * Finite boxes take vanilla's path untouched.
 */
@Mixin(Frustum.class)
abstract class FrustumMixin {
    @Shadow private double camX;
    @Shadow private double camY;
    @Shadow private double camZ;

    @Shadow
    private boolean cubeInFrustum(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        throw new AssertionError();
    }

    @Inject(method = "isVisible", at = @At("HEAD"), cancellable = true)
    private void prNonFiniteBounds(AABB box, CallbackInfoReturnable<Boolean> cir) {
        if (PrFrustumCulling.isFinite(box)) {
            return;
        }
        if (PrFrustumCulling.hasNaN(box)) {
            cir.setReturnValue(true);
            return;
        }
        cir.setReturnValue(cubeInFrustum(
                PrFrustumCulling.clamp(box.minX, camX), PrFrustumCulling.clamp(box.minY, camY), PrFrustumCulling.clamp(box.minZ, camZ),
                PrFrustumCulling.clamp(box.maxX, camX), PrFrustumCulling.clamp(box.maxY, camY), PrFrustumCulling.clamp(box.maxZ, camZ)));
    }
}
