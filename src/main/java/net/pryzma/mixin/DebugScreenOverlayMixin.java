package net.pryzma.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import net.pryzma.gui.PrDebugOverlay;
import net.pryzma.render.PrF3RenderCache;

/**
 * Optimizes the F3 debug screen overlay by implementing a 10 Hz render cache (RenderCache),
 * eliminating per-frame raycasting, 3D terrain noise evaluations, string allocations,
 * and font measuring loops, while preserving dynamic frame-time and profiler charts.
 */
@Mixin(DebugScreenOverlay.class)
abstract class DebugScreenOverlayMixin {
    @Shadow
    private Font font;

    @Shadow
    private HitResult block;

    @Shadow
    private HitResult liquid;

    @Inject(method = "render", at = @At("HEAD"))
    private void prOnRenderHead(GuiGraphics guiGraphics, CallbackInfo ci) {
        PrF3RenderCache.recordRenderTick();
    }

    /**
     * Throttles entity raycasting when debug text is cached.
     */
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;pick(DFZ)Lnet/minecraft/world/phys/HitResult;"))
    private HitResult prConditionalPick(Entity entity, double distance, float partialTicks, boolean hitFluids) {
        if (PrF3RenderCache.isCached()) {
            return hitFluids ? this.liquid : this.block;
        }
        return entity.pick(distance, partialTicks, hitFluids);
    }

    /**
     * Fast-path: renders the left column from cache, skipping getGameInformation() and 3D noise router evaluation.
     */
    @Inject(method = "drawGameInformation", at = @At("HEAD"), cancellable = true)
    private void prDrawGameInformation(GuiGraphics guiGraphics, CallbackInfo ci) {
        if (PrF3RenderCache.drawLeftCached(guiGraphics, this.font)) {
            ci.cancel();
        }
    }

    /**
     * Fast-path: renders the right column from cache, skipping getSystemInformation().
     */
    @Inject(method = "drawSystemInformation", at = @At("HEAD"), cancellable = true)
    private void prDrawSystemInformation(GuiGraphics guiGraphics, CallbackInfo ci) {
        if (PrF3RenderCache.drawRightCached(guiGraphics, this.font)) {
            ci.cancel();
        }
    }

    /**
     * Captures and caches the built lines during 100 ms update ticks.
     */
    @Inject(method = "renderLines", at = @At("HEAD"), cancellable = true)
    private void prRenderLines(GuiGraphics guiGraphics, List<String> list, boolean left, CallbackInfo ci) {
        if (left) {
            PrF3RenderCache.cacheAndRenderLeft(guiGraphics, this.font, list);
        } else {
            PrF3RenderCache.cacheAndRenderRight(guiGraphics, this.font, list);
        }
        ci.cancel();
    }

    @Inject(method = "getGameInformation()Ljava/util/List;", at = @At("RETURN"))
    private void prGameInformation(CallbackInfoReturnable<List<String>> cir) {
        PrDebugOverlay.left(cir.getReturnValue());
    }

    @Inject(method = "getSystemInformation()Ljava/util/List;", at = @At("RETURN"))
    private void prSystemInformation(CallbackInfoReturnable<List<String>> cir) {
        PrDebugOverlay.right(cir.getReturnValue());
    }
}
