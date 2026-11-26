package net.pryzma.farlands.gen;

import java.util.Arrays;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.BlockColumn;
import org.jspecify.annotations.Nullable;

final class SurfaceColumn implements BlockColumn {
   private static final BlockState AIR = Blocks.AIR.defaultBlockState();
   private final int minY;
   private final @Nullable BlockState[] states;
   private int low = Integer.MAX_VALUE;
   private int high = Integer.MIN_VALUE;
   private int top;
   private int fluid;
   private BlockState solid = AIR;
   private BlockState liquid = AIR;

   SurfaceColumn(int minY, int height) {
      this.minY = minY;
      this.states = new BlockState[height];
   }

   void reset(int top, int fluid, BlockState solid, BlockState liquid) {
      if (this.high >= this.low) {
         Arrays.fill(this.states, this.low - this.minY, this.high - this.minY + 1, null);
      }

      this.low = Integer.MAX_VALUE;
      this.high = Integer.MIN_VALUE;
      this.top = top;
      this.fluid = fluid;
      this.solid = solid;
      this.liquid = liquid;
   }

   boolean changed() {
      return this.high >= this.low;
   }

   int low() {
      return this.low;
   }

   int high() {
      return this.high;
   }

   @Nullable BlockState placed(int y) {
      int index = y - this.minY;
      return index >= 0 && index < this.states.length ? this.states[index] : null;
   }

   public BlockState getBlock(int y) {
      BlockState placed = this.placed(y);
      if (placed != null) {
         return placed;
      } else if (this.fluid != Integer.MIN_VALUE && y >= this.top && y < this.fluid) {
         return this.liquid;
      } else {
         return y >= this.top ? AIR : this.solid;
      }
   }

   public void setBlock(int y, BlockState state) {
      int index = y - this.minY;
      if (index >= 0 && index < this.states.length) {
         this.states[index] = state;
         this.low = Math.min(this.low, y);
         this.high = Math.max(this.high, y);
      }
   }
}
