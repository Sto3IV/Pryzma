package net.pryzma.farlands.mixin;

import net.pryzma.farlands.gen.EffectiveDensity;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = NoiseBasedChunkGenerator.class, priority = 2000)
public abstract class EffectiveDensityMixin {
   @ModifyArg(
      method = "doFill",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/world/level/levelgen/densityfunction/DensitySamplerSet;get(Lnet/minecraft/world/level/levelgen/densityfunction/DensityFunction;)Lnet/minecraft/world/level/levelgen/densityfunction/DensitySampler$Bound;"
      ),
      require = 0
   )
   private DensityFunction prFarlands$effectiveDensity(DensityFunction density) {
      EffectiveDensity.seen(density);
      return density;
   }
}
