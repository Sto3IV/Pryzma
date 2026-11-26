package net.pryzma.farlands.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.pryzma.farlands.gen.PrefillBuffers;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.material.MaterialRuleContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MaterialRuleContext.class)
public abstract class MaterialRuleContextMixin {
   @WrapOperation(
      method = "getDensitiesInChunk",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/level/levelgen/densityfunction/DensityBuffer;createUnpooled(I)Lnet/minecraft/world/level/levelgen/densityfunction/DensityBuffer;"
      ),
      require = 0
   )
   private DensityBuffer prFarlands$prefill(int size, Operation<DensityBuffer> original) {
      DensityBuffer pooled = PrefillBuffers.take(size);
      return pooled != null ? pooled : (DensityBuffer)original.call(new Object[]{size});
   }
}
