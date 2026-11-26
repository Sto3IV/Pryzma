package net.pryzma.farlands.mixin;

import net.pryzma.farlands.work.WorkerPool;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NbtIo.class)
public abstract class NbtFilesMixin {
   @Inject(method = "writeCompressed(Lnet/minecraft/nbt/CompoundTag;Ljava/nio/file/Path;)V", at = @At("HEAD"), cancellable = true, require = 0)
   private static void prFarlands$keepCompressed(CompoundTag tag, Path file, CallbackInfo ci) {
      if (WorkerPool.onWorker()) {
         ci.cancel();
      }
   }

   @Inject(method = "write(Lnet/minecraft/nbt/CompoundTag;Ljava/nio/file/Path;)V", at = @At("HEAD"), cancellable = true, require = 0)
   private static void prFarlands$keepPlain(CompoundTag tag, Path file, CallbackInfo ci) {
      if (WorkerPool.onWorker()) {
         ci.cancel();
      }
   }
}
