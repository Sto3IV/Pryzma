package net.pryzma.farlands.mixin.client;

import net.pryzma.farlands.FarlandsSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.client.renderer.chunk.SectionRenderDispatcher$RenderSection")
public abstract class RenderSectionMixin {
   @Inject(method = "getVisibility", at = @At("HEAD"), cancellable = true, require = 0)
   private void prFarlands$noFogFade(long now, long fadeDuration, CallbackInfoReturnable<Float> cir) {
      if (FarlandsSettings.enabled) {
         cir.setReturnValue(1.0F);
      }
   }
}
