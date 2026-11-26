package net.pryzma.farlands.gen;

import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunctions.HolderHolder;
import net.minecraft.world.level.levelgen.densityfunction.op.InterpolatedFunction;

final class StripInterpolation implements DfRewriteRule {
   int cellXz;
   int cellY;
   boolean consistent = true;
   boolean found;

   public DensityFunction rewrite(DensityFunction function) {
      DensityFunction current = function;

      while (current instanceof HolderHolder) {
         HolderHolder holder = (HolderHolder)current;
         current = (DensityFunction)holder.function().value();
      }

      if (current instanceof InterpolatedFunction interpolated) {
         this.record(interpolated.cellSizeXz(), interpolated.cellSizeY());
         return this.rewrite(interpolated.input());
      } else {
         return current.rewriteChildren(this);
      }
   }

   private void record(int xz, int y) {
      if (!this.found) {
         this.found = true;
         this.cellXz = xz;
         this.cellY = y;
      } else {
         if (xz != this.cellXz || y != this.cellY) {
            this.consistent = false;
         }

         this.cellXz = Math.max(this.cellXz, xz);
         this.cellY = Math.max(this.cellY, y);
      }
   }
}
