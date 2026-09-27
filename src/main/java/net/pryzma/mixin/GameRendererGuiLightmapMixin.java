package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.pryzma.lightmap.PrGuiLightmap;

/**
 * Marks the GUI part of the frame (HUD, overlay, screen, toasts): from the creation of its GuiGraphics until its
 * final flush, where the batched GUI draws are submitted. See {@link PrGuiLightmap}.
 */
@Mixin(GameRenderer.class)
abstract class GameRendererGuiLightmapMixin {
    /** A frame that threw inside the GUI must not leave the next frame's world on the white lightmap. */
    @Inject(method = "render", at = @At("HEAD"))
    private void prGuiLightmapReset(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        PrGuiLightmap.endGui();
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Lighting;setupFor3DItems()V"))
    private void prGuiLightmapBegin(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        PrGuiLightmap.beginGui();
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;flush()V", shift = At.Shift.AFTER))
    private void prGuiLightmapEnd(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        PrGuiLightmap.endGui();
    }
}
