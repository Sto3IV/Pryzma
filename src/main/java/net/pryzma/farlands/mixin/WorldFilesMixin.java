package net.pryzma.farlands.mixin;

import net.pryzma.farlands.work.WorkerPool;
import java.nio.file.Path;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Util.class)
public abstract class WorldFilesMixin {
   @Inject(method = "safeReplaceFile", at = @At("HEAD"), cancellable = true, require = 0)
   private static void prFarlands$keepWorldFiles(Path target, Path fresh, Path backup, CallbackInfo ci) {
      if (WorkerPool.onWorker()) {
         ci.cancel();
      }
   }

   @Inject(method = "safeReplaceOrMoveFile", at = @At("HEAD"), cancellable = true, require = 0)
   private static void prFarlands$keepWorldFiles(Path target, Path fresh, Path backup, boolean noRollback, CallbackInfoReturnable<Boolean> cir) {
      if (WorkerPool.onWorker()) {
         cir.setReturnValue(true);
      }
   }
}
