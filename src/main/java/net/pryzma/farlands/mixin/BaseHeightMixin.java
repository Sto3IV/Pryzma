package net.pryzma.farlands.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.pryzma.farlands.gen.BaseHeights;
import net.pryzma.farlands.work.WorkerPool;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = NoiseBasedChunkGenerator.class, priority = 2000)
public abstract class BaseHeightMixin {
   @WrapMethod(method = "getBaseHeight", require = 0)
   private int prFarlands$sharedHeights(int x, int z, Types type, LevelHeightAccessor level, RandomState random, Operation<Integer> original) {
      return !WorkerPool.onWorker()
         ? (Integer)original.call(new Object[]{x, z, type, level, random})
         : BaseHeights.get(random, type, x, z, () -> (Integer)original.call(new Object[]{x, z, type, level, random}));
   }
}
