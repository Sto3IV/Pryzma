package net.pryzma.farlands.gen;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunctions.HolderHolder;
import net.minecraft.world.level.levelgen.densityfunction.generator.NoiseFunction;
import net.minecraft.world.level.levelgen.densityfunction.op.BinaryFunction;
import net.minecraft.world.level.levelgen.densityfunction.op.InterpolatedFunction;
import net.minecraft.world.level.levelgen.densityfunction.op.RangeChoiceFunction;
import net.minecraft.world.level.levelgen.densityfunction.op.SplineFunction;
import net.minecraft.world.level.levelgen.densityfunction.op.BinaryFunction.Type;

final class StripCaves implements DfRewriteRule {
   private static final int Y_AXIS = DensityFunction.axesFrom(Axis.Y);
   private boolean found;
   private final boolean outside;
   private final boolean shape;
   private final Map<DensityFunction, Boolean> shaped = new IdentityHashMap<>();
   private final Map<DensityFunction, Boolean> noisy = new IdentityHashMap<>();

   private StripCaves(boolean outside, boolean shape) {
      this.outside = outside;
      this.shape = shape;
   }

   static DensityFunction apply(DensityFunction function) {
      return apply(function, false, false);
   }

   static DensityFunction outsideCells(DensityFunction function) {
      return apply(function, true, false);
   }

   static DensityFunction terrainShape(DensityFunction function) {
      return apply(function, false, true);
   }

   private static DensityFunction apply(DensityFunction function, boolean outside, boolean shape) {
      StripCaves rule = new StripCaves(outside, shape);

      try {
         DensityFunction result = rule.rewrite(function);
         return rule.found ? result : function;
      } catch (RuntimeException e) {
         return function;
      }
   }

   public DensityFunction rewrite(DensityFunction function) {
      DensityFunction current = unwrap(function);
      if (this.outside && current instanceof InterpolatedFunction) {
         return function;
      }

      if (this.shape && current instanceof RangeChoiceFunction choice) {
         DensityFunction input = this.rewrite(choice.input());
         DensityFunction inRange = this.rewrite(choice.whenInRange());
         if (unwrap(input) == unwrap(inRange)) {
            this.found = true;
            return input;
         }
      }

      if (current instanceof BinaryFunction binary && binary.type() == Type.MIN) {
         boolean left = this.shaped(binary.left());
         boolean right = this.shaped(binary.right());
         if (left && !right && this.carves(binary.right())) {
            this.found = true;
            return this.rewrite(binary.left());
         }

         if (right && !left && this.carves(binary.left())) {
            this.found = true;
            return this.rewrite(binary.right());
         }
      }

      DensityFunction rewritten = current.rewriteChildren(this);
      return rewritten == current ? function : rewritten;
   }

   private boolean shaped(DensityFunction function) {
      return this.contains(function, this.shaped, SplineFunction.class::isInstance);
   }

   private boolean carves(DensityFunction function) {
      return (function.domainAxes() & Y_AXIS) != 0 && this.contains(function, this.noisy, NoiseFunction.class::isInstance);
   }

   private boolean contains(DensityFunction function, Map<DensityFunction, Boolean> memo, Predicate<DensityFunction> test) {
      DensityFunction current = unwrap(function);
      Boolean known = memo.get(current);
      if (known != null) {
         return known;
      }

      memo.put(current, Boolean.FALSE);
      boolean[] any = new boolean[]{test.test(current)};
      if (!any[0]) {
         current.rewriteChildren(child -> {
            if (!any[0] && this.contains(child, memo, test)) {
               any[0] = true;
            }

            return child;
         });
      }

      memo.put(current, any[0]);
      return any[0];
   }

   private static DensityFunction unwrap(DensityFunction function) {
      DensityFunction current = function;

      while (current instanceof HolderHolder) {
         HolderHolder holder = (HolderHolder)current;
         current = (DensityFunction)holder.function().value();
      }

      return current;
   }
}
