package net.pryzma.farlands.gen;

import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Interval;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction.Axes;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction.CompileContext;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunctions.HolderHolder;
import net.minecraft.world.level.levelgen.densityfunction.op.InterpolatedFunction;
import org.jspecify.annotations.Nullable;

final class CornerCache {
   static final ContextKey<CornerCache> KEY = new ContextKey(Identifier.fromNamespaceAndPath("pryzma", "corner_cache"));
   private final int minY;
   private final int maxY;
   private final List<CornerCache.Corners> nodes = new ArrayList<>();
   private boolean active;
   private int minBlockX;
   private int minBlockZ;
   private int maxBlockX;
   private int maxBlockZ;
   private int generation;

   CornerCache(int minY, int maxY) {
      this.minY = minY;
      this.maxY = maxY;
   }

   static DensityFunction wrap(DensityFunction function) {
      CornerCache.Rewrite rule = new CornerCache.Rewrite();

      try {
         DensityFunction result = rule.rewrite(function);
         return rule.count > 0 ? result : function;
      } catch (RuntimeException e) {
         return function;
      }
   }

   void begin(int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ) {
      this.minBlockX = minBlockX;
      this.minBlockZ = minBlockZ;
      this.maxBlockX = maxBlockX;
      this.maxBlockZ = maxBlockZ;
      this.generation++;
      this.active = true;
   }

   void end() {
      this.active = false;
   }

   private CornerCache.@Nullable Corners corners(int id, int cellXz, int cellY) {
      while (this.nodes.size() <= id) {
         this.nodes.add(null);
      }

      CornerCache.Corners node = this.nodes.get(id);
      if (node == null || node.cellXz != cellXz || node.cellY != cellY) {
         node = new CornerCache.Corners(cellXz, cellY);
         this.nodes.set(id, node);
      }

      if (node.generation != this.generation) {
         node.reset(this.generation, this.minBlockX, this.minBlockZ, this.maxBlockX, this.maxBlockZ, this.minY, this.maxY);
      }

      return node;
   }

   private record Cached(DensityFunction input, int cellSizeXz, int cellSizeY, int id) implements DensityFunction {
      public DensitySampler compileSampler(CompileContext context) {
         return new CornerCache.Sampler(
            this.input.compileSampler(context),
            new InterpolatedFunction(this.input, this.cellSizeXz, this.cellSizeY).compileSampler(context),
            this.cellSizeXz,
            this.cellSizeY,
            1.0F / this.cellSizeXz,
            1.0F / this.cellSizeY,
            this.id
         );
      }

      public DensityFunction rewriteChildren(DfRewriteRule rule) {
         DensityFunction input = rule.rewrite(this.input);
         return input == this.input ? this : new CornerCache.Cached(input, this.cellSizeXz, this.cellSizeY, this.id);
      }

      public Interval range() {
         return this.input.range();
      }

      public @Axes int domainAxes() {
         return this.input.domainAxes();
      }

      public MapCodec<? extends DensityFunction> codec() {
         throw new UnsupportedOperationException("Distant terrain cache is never encoded");
      }
   }

   private static final class Corners {
      final int cellXz;
      final int cellY;
      int generation = -1;
      int originX;
      int originY;
      int originZ;
      int sizeX;
      int sizeY;
      int sizeZ;
      float[] values = new float[0];
      int[] stamps = new int[0];
      int stamp;

      Corners(int cellXz, int cellY) {
         this.cellXz = cellXz;
         this.cellY = cellY;
      }

      void reset(int generation, int minX, int minZ, int maxX, int maxZ, int minY, int maxY) {
         this.generation = generation;
         this.originX = Math.floorDiv(minX, this.cellXz);
         this.originZ = Math.floorDiv(minZ, this.cellXz);
         this.originY = Math.floorDiv(minY, this.cellY);
         this.sizeX = Math.floorDiv(maxX, this.cellXz) - this.originX + 2;
         this.sizeZ = Math.floorDiv(maxZ, this.cellXz) - this.originZ + 2;
         this.sizeY = Math.floorDiv(maxY, this.cellY) - this.originY + 2;
         int size = this.sizeX * this.sizeY * this.sizeZ;
         if (this.values.length < size) {
            this.values = new float[size];
            this.stamps = new int[size];
            this.stamp = 0;
         }

         this.stamp++;
         if (this.stamp == Integer.MAX_VALUE) {
            Arrays.fill(this.stamps, 0);
            this.stamp = 1;
         }
      }

      boolean covers(int cx, int cy, int cz, int nx, int ny, int nz) {
         return cx >= this.originX
            && cy >= this.originY
            && cz >= this.originZ
            && cx + nx <= this.originX + this.sizeX
            && cy + ny <= this.originY + this.sizeY
            && cz + nz <= this.originZ + this.sizeZ;
      }

      int index(int cx, int cy, int cz) {
         return ((cz - this.originZ) * this.sizeX + cx - this.originX) * this.sizeY + cy - this.originY;
      }

      void ensure(SamplerContext context, DensitySampler input, int cx0, int cy0, int cz0, int nx, int ny, int nz) {
         int runStart = -1;
         int rx0 = 0;
         int rx1 = 0;
         int rz0 = 0;
         int rz1 = 0;

         for (int ly = 0; ly <= ny; ly++) {
            int mx0 = Integer.MAX_VALUE;
            int mx1 = Integer.MIN_VALUE;
            int mz0 = Integer.MAX_VALUE;
            int mz1 = Integer.MIN_VALUE;
            if (ly < ny) {
               int cy = cy0 + ly;

               for (int z = 0; z < nz; z++) {
                  for (int x = 0; x < nx; x++) {
                     if (this.stamps[this.index(cx0 + x, cy, cz0 + z)] != this.stamp) {
                        mx0 = Math.min(mx0, x);
                        mx1 = Math.max(mx1, x);
                        mz0 = Math.min(mz0, z);
                        mz1 = Math.max(mz1, z);
                     }
                  }
               }
            }

            boolean missing = mx0 <= mx1;
            if (runStart >= 0 && (!missing || mx0 != rx0 || mx1 != rx1 || mz0 != rz0 || mz1 != rz1)) {
               this.fill(context, input, cx0 + rx0, cy0 + runStart, cz0 + rz0, rx1 - rx0 + 1, ly - runStart, rz1 - rz0 + 1);
               runStart = -1;
            }

            if (missing && runStart < 0) {
               runStart = ly;
               rx0 = mx0;
               rx1 = mx1;
               rz0 = mz0;
               rz1 = mz1;
            }
         }
      }

      private void fill(SamplerContext context, DensitySampler input, int cx, int cy, int cz, int nx, int ny, int nz) {
         DensityVolume volume = new DensityVolume(nx, ny, nz, cx * this.cellXz, cy * this.cellY, cz * this.cellXz, this.cellXz, this.cellY, this.cellXz);
         ScopedDensityBuffer buffer = context.acquireBuffer(volume);

         try {
            input.sampleVolume(context, buffer, volume);

            for (int z = 0; z < nz; z++) {
               for (int x = 0; x < nx; x++) {
                  for (int y = 0; y < ny; y++) {
                     int i = this.index(cx + x, cy + y, cz + z);
                     if (this.stamps[i] != this.stamp) {
                        this.values[i] = buffer.get(volume.indexUnchecked(x, y, z));
                        this.stamps[i] = this.stamp;
                     }
                  }
               }
            }
         } catch (Throwable var16) {
            if (buffer != null) {
               try {
                  buffer.close();
               } catch (Throwable var15) {
                  var16.addSuppressed(var15);
               }
            }

            throw var16;
         }

         if (buffer != null) {
            buffer.close();
         }
      }
   }

   private static final class Rewrite implements DfRewriteRule {
      int count;

      public DensityFunction rewrite(DensityFunction function) {
         DensityFunction current = function;

         while (current instanceof HolderHolder) {
            HolderHolder holder = (HolderHolder)current;
            current = (DensityFunction)holder.function().value();
         }

         if (current instanceof InterpolatedFunction interpolated) {
            return new CornerCache.Cached(interpolated.input(), interpolated.cellSizeXz(), interpolated.cellSizeY(), this.count++);
         } else {
            DensityFunction rewritten = current.rewriteChildren(this);
            return rewritten == current ? function : rewritten;
         }
      }
   }

   private record Sampler(DensitySampler input, DensitySampler plain, int cellSizeXz, int cellSizeY, float cellSizeXzInv, float cellSizeYInv, int id)
      implements DensitySampler {
      public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
         return this.plain.sampleValue(context, blockX, blockY, blockZ);
      }

      public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
         CornerCache cache = (CornerCache)context.getField(CornerCache.KEY);
         int step = volume.stepBlockX();
         if (cache != null
            && cache.active
            && volume.stepBlockY() == 1
            && volume.stepBlockZ() == step
            && step < this.cellSizeXz
            && volume.sizeY() != 1
            && this.cellSizeY != 1) {
            int minX = volume.minBlockX();
            int minY = volume.minBlockY();
            int minZ = volume.minBlockZ();
            int blockSizeX = volume.sizeX() * step;
            int blockSizeY = volume.sizeY();
            int blockSizeZ = volume.sizeZ() * step;
            int maxX = minX + blockSizeX - 1;
            int maxY = minY + blockSizeY - 1;
            int maxZ = minZ + blockSizeZ - 1;
            int minCellX = Math.floorDiv(minX, this.cellSizeXz);
            int minCellY = Math.floorDiv(minY, this.cellSizeY);
            int minCellZ = Math.floorDiv(minZ, this.cellSizeXz);
            int cellCountX = Math.floorDiv(maxX, this.cellSizeXz) - minCellX + 1;
            int cellCountY = Math.floorDiv(maxY, this.cellSizeY) - minCellY + 1;
            int cellCountZ = Math.floorDiv(maxZ, this.cellSizeXz) - minCellZ + 1;
            int cornersX = Math.floorMod(maxX, this.cellSizeXz) == 0 ? cellCountX : cellCountX + 1;
            int cornersY = Math.floorMod(maxY, this.cellSizeY) == 0 ? cellCountY : cellCountY + 1;
            int cornersZ = Math.floorMod(maxZ, this.cellSizeXz) == 0 ? cellCountZ : cellCountZ + 1;
            CornerCache.Corners corners = cache.corners(this.id, this.cellSizeXz, this.cellSizeY);
            if (corners != null && corners.covers(minCellX, minCellY, minCellZ, cornersX, cornersY, cornersZ)) {
               corners.ensure(context, this.input, minCellX, minCellY, minCellZ, cornersX, cornersY, cornersZ);
               float[] v = corners.values;

               for (int cellZ = 0; cellZ < cellCountZ; cellZ++) {
                  int nextCellZ = Math.min(cellZ + 1, cornersZ - 1);

                  for (int cellX = 0; cellX < cellCountX; cellX++) {
                     int nextCellX = Math.min(cellX + 1, cornersX - 1);
                     float v000 = v[corners.index(minCellX + cellX, minCellY, minCellZ + cellZ)];
                     float v100 = v[corners.index(minCellX + nextCellX, minCellY, minCellZ + cellZ)];
                     float v001 = v[corners.index(minCellX + cellX, minCellY, minCellZ + nextCellZ)];
                     float v101 = v[corners.index(minCellX + nextCellX, minCellY, minCellZ + nextCellZ)];

                     for (int cellY = 0; cellY < cellCountY; cellY++) {
                        int nextCellY = Math.min(cellY + 1, cornersY - 1);
                        float v010 = v[corners.index(minCellX + cellX, minCellY + nextCellY, minCellZ + cellZ)];
                        float v110 = v[corners.index(minCellX + nextCellX, minCellY + nextCellY, minCellZ + cellZ)];
                        float v011 = v[corners.index(minCellX + cellX, minCellY + nextCellY, minCellZ + nextCellZ)];
                        float v111 = v[corners.index(minCellX + nextCellX, minCellY + nextCellY, minCellZ + nextCellZ)];
                        this.fillCell(
                           outputBuffer,
                           volume,
                           step,
                           blockSizeX,
                           blockSizeY,
                           blockSizeZ,
                           (minCellX + cellX) * this.cellSizeXz - minX,
                           (minCellY + cellY) * this.cellSizeY - minY,
                           (minCellZ + cellZ) * this.cellSizeXz - minZ,
                           v000,
                           v100,
                           v010,
                           v110,
                           v001,
                           v101,
                           v011,
                           v111
                        );
                        v000 = v010;
                        v100 = v110;
                        v001 = v011;
                        v101 = v111;
                     }
                  }
               }
            } else {
               this.plain.sampleVolume(context, outputBuffer, volume);
            }
         } else {
            this.plain.sampleVolume(context, outputBuffer, volume);
         }
      }

      private void fillCell(
         DensityBuffer outputBuffer,
         DensityVolume volume,
         int step,
         int sizeX,
         int sizeY,
         int sizeZ,
         int cellOutputX,
         int cellOutputY,
         int cellOutputZ,
         float v000,
         float v100,
         float v010,
         float v110,
         float v001,
         float v101,
         float v011,
         float v111
      ) {
         int x0 = Math.max(0, -cellOutputX);
         int y0 = Math.max(0, -cellOutputY);
         int z0 = Math.max(0, -cellOutputZ);
         int x1 = Math.min(this.cellSizeXz, sizeX - cellOutputX) - 1;
         int y1 = Math.min(this.cellSizeY, sizeY - cellOutputY) - 1;
         int z1 = Math.min(this.cellSizeXz, sizeZ - cellOutputZ) - 1;

         for (int z = z0; z <= z1; z++) {
            int outputZ = cellOutputZ + z;
            if (outputZ % step == 0) {
               float alphaZ = z * this.cellSizeXzInv;
               float v00 = Mth.lerp(alphaZ, v000, v001);
               float v01 = Mth.lerp(alphaZ, v010, v011);
               float v10 = Mth.lerp(alphaZ, v100, v101);
               float v11 = Mth.lerp(alphaZ, v110, v111);

               for (int x = x0; x <= x1; x++) {
                  int outputX = cellOutputX + x;
                  if (outputX % step == 0) {
                     float alphaX = x * this.cellSizeXzInv;
                     float low = Mth.lerp(alphaX, v00, v10);
                     float high = Mth.lerp(alphaX, v01, v11);
                     float valueStep = (high - low) * this.cellSizeYInv;
                     float value = low + valueStep * y0;
                     int outputIndex = volume.indexUnchecked(outputX / step, cellOutputY + y0, outputZ / step);

                     for (int y = y0; y <= y1; y++) {
                        outputBuffer.set(outputIndex++, value);
                        value += valueStep;
                     }
                  }
               }
            }
         }
      }
   }
}
