package net.pryzma.farlands.mixin.client;

import net.pryzma.farlands.client.FarlandsClient;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
   @Shadow
   private float depthFar;

   @Inject(method = "update", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Camera;depthFar:F", opcode = 181, shift = Shift.AFTER))
   private void prFarlands$extendFar(DeltaTracker deltaTracker, CallbackInfo ci) {
      FarlandsClient client = FarlandsClient.peek();
      if (client != null) {
         this.depthFar = client.extendFar(this.depthFar);
      }
   }
}
