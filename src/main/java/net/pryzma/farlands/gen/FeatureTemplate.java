package net.pryzma.farlands.gen;

import net.pryzma.farlands.lod.BlockShapes;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

final class FeatureTemplate {
   final int minDx;
   final int minDz;
   final int sizeX;
   final int sizeZ;
   final int height;
   final int reach;
   final boolean paints;
   private final int[] start;
   private final long[] runs;

   private FeatureTemplate(int minDx, int minDz, int sizeX, int sizeZ, int[] start, long[] runs, boolean paints) {
      this.paints = paints;
      this.minDx = minDx;
      this.minDz = minDz;
      this.sizeX = sizeX;
      this.sizeZ = sizeZ;
      this.start = start;
      this.runs = runs;
      int top = 0;

      for (long run : runs) {
         top = Math.max(top, top(run));
      }

      this.height = top;
      this.reach = Math.max(Math.max(-minDx, minDx + sizeX - 1), Math.max(-minDz, minDz + sizeZ - 1));
   }

   static int top(long run) {
      return (short)(run >>> 48);
   }

   static int bottom(long run) {
      return (short)(run >>> 32);
   }

   static int state(long run) {
      return (int)run;
   }

   private static long pack(int top, int bottom, int state) {
      return (long)(top & 65535) << 48 | (long)(bottom & 65535) << 32 | state & 4294967295L;
   }

   int first(int dx, int dz) {
      int x = dx - this.minDx;
      int z = dz - this.minDz;
      if (x >= 0 && z >= 0 && x < this.sizeX && z < this.sizeZ) {
         int index = z * this.sizeX + x;
         return this.start[index] == this.start[index + 1] ? -1 : this.start[index];
      } else {
         return -1;
      }
   }

   int end(int dx, int dz) {
      return this.start[(dz - this.minDz) * this.sizeX + dx - this.minDx + 1];
   }

   long run(int index) {
      return this.runs[index];
   }

   static @Nullable FeatureTemplate capture(LongArrayList cluster, LodGenLevel gen, int anchorX, int anchorZ, int baseY, boolean paints) {
      int minX = Integer.MAX_VALUE;
      int minZ = Integer.MAX_VALUE;
      int maxX = Integer.MIN_VALUE;
      int maxZ = Integer.MIN_VALUE;

      for (int i = 0; i < cluster.size(); i++) {
         long key = cluster.getLong(i);
         minX = Math.min(minX, BlockPos.getX(key));
         maxX = Math.max(maxX, BlockPos.getX(key));
         minZ = Math.min(minZ, BlockPos.getZ(key));
         maxZ = Math.max(maxZ, BlockPos.getZ(key));
      }

      int sizeX = maxX - minX + 1;
      int sizeZ = maxZ - minZ + 1;
      if (sizeX <= 64 && sizeZ <= 64) {
         LongArrayList valid = new LongArrayList(cluster.size());

         for (int i = 0; i < cluster.size(); i++) {
            long key = cluster.getLong(i);
            BlockState state = (BlockState)gen.placed.get(key);
            if (state != null && !state.isAir() && BlockShapes.stored(state) != -1) {
               valid.add(key);
            }
         }

         if (valid.isEmpty()) {
            return null;
         }

         long[] order = new long[valid.size()];

         for (int i = 0; i < order.length; i++) {
            long key = valid.getLong(i);
            long column = (long)(BlockPos.getZ(key) - minZ) * sizeX + BlockPos.getX(key) - minX;
            order[i] = column << 40 | (long)(1048575 - (BlockPos.getY(key) + 524288)) << 20 | i;
         }

         Arrays.sort(order);
         int[] start = new int[sizeX * sizeZ + 1];
         LongArrayList runs = new LongArrayList();
         int column = -1;
         int runTop = 0;
         int runBottom = 0;
         int runState = -1;

         for (long entry : order) {
            long key = valid.getLong((int)(entry & 1048575L));
            int entryColumn = (int)(entry >>> 40);
            int y = BlockPos.getY(key) - baseY;
            int state = BlockShapes.stored((BlockState)gen.placed.get(key));
            if (entryColumn == column) {
               if (state == runState && y == runBottom - 1) {
                  runBottom = y;
               } else {
                  runs.add(pack(runTop, runBottom, runState));
                  runState = state;
                  runTop = y + 1;
                  runBottom = y;
               }
            } else {
               if (runState >= 0) {
                  runs.add(pack(runTop, runBottom, runState));
               }

               for (int c = column + 1; c <= entryColumn; c++) {
                  start[c] = runs.size();
               }

               column = entryColumn;
               runState = state;
               runTop = y + 1;
               runBottom = y;
            }
         }

         if (runState >= 0) {
            runs.add(pack(runTop, runBottom, runState));
         }

         for (int c = column + 1; c <= sizeX * sizeZ; c++) {
            start[c] = runs.size();
         }

         return new FeatureTemplate(minX - anchorX, minZ - anchorZ, sizeX, sizeZ, start, runs.toLongArray(), paints);
      } else {
         return null;
      }
   }
}
