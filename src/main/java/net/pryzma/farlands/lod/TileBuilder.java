package net.pryzma.farlands.lod;

import java.util.Arrays;
import net.minecraft.world.level.block.Block;

public final class TileBuilder {
   private static final ThreadLocal<TileBuilder> LOCAL = ThreadLocal.withInitial(TileBuilder::new);
   private final int[] starts = new int[4356];
   private final int[] counts = new int[4356];
   private long[] data = new long[4096];
   private long[] spare = new long[0];
   private int used;
   private long[] scratch = new long[32];
   private int scratchCount;
   private boolean borrowed;

   public static TileBuilder borrow() {
      TileBuilder builder = LOCAL.get();
      if (builder.borrowed) {
         return new TileBuilder();
      }

      builder.borrowed = true;
      return builder;
   }

   public void begin() {
      this.scratchCount = 0;
   }

   public void add(long span) {
      if (this.scratchCount > 0) {
         long last = this.scratch[this.scratchCount - 1];
         if (Span.state(last) == Span.state(span)
            && Span.bottom(last) == Span.top(span)
            && Span.biome(last) == Span.biome(span)
            && Span.sky(last) == Span.sky(span)
            && Span.block(last) == Span.block(span)) {
            this.scratch[this.scratchCount - 1] = Span.withBottom(last, Span.bottom(span));
            return;
         }
      }

      if (this.scratchCount == this.scratch.length) {
         this.scratch = Arrays.copyOf(this.scratch, this.scratch.length * 2);
      }

      this.scratch[this.scratchCount++] = span;
   }

   public void end(int x, int z) {
      this.put(LodTile.cell(x, z), this.scratch, 0, this.scratchCount);
   }

   public void set(int x, int z, long[] column, int from, int count) {
      this.put(LodTile.cell(x, z), column, from, count);
   }

   public void copyFrom(LodTile tile) {
      int total = tile.offsets[4356];
      if (this.data.length < total) {
         this.data = new long[Math.max(total, this.data.length * 2)];
      }

      System.arraycopy(tile.spans, 0, this.data, 0, total);
      this.used = total;

      for (int c = 0; c < 4356; c++) {
         this.starts[c] = tile.offsets[c];
         this.counts[c] = tile.offsets[c + 1] - tile.offsets[c];
      }
   }

   private void put(int c, long[] source, int from, int count) {
      this.counts[c] = 0;
      if (count != 0) {
         this.reserve(count);
         System.arraycopy(source, from, this.data, this.used, count);
         this.starts[c] = this.used;
         this.counts[c] = count;
         this.used += count;
      }
   }

   private void reserve(int extra) {
      if (this.used + extra > this.data.length) {
         int live = 0;

         for (int c = 0; c < 4356; c++) {
            live += this.counts[c];
         }

         int size = this.data.length;

         while (live + extra > size / 2) {
            size *= 2;
         }

         if (this.spare.length < size) {
            this.spare = new long[size];
         }

         int at = 0;

         for (int c = 0; c < 4356; c++) {
            int count = this.counts[c];
            if (count != 0) {
               System.arraycopy(this.data, this.starts[c], this.spare, at, count);
               this.starts[c] = at;
               at += count;
            }
         }

         long[] swap = this.data;
         this.data = this.spare;
         this.spare = swap.length >= size ? swap : new long[0];
         this.used = at;
      }
   }

   private void closeGaps(int maxGap) {
      for (int c = 0; c < 4356; c++) {
         int count = this.counts[c];
         if (count >= 2) {
            int base = this.starts[c];

            for (int i = 0; i < count - 1; i++) {
               long upper = this.data[base + i];
               int gap = Span.bottom(upper) - Span.top(this.data[base + i + 1]);
               if (gap > 0 && gap <= maxGap && Block.stateById(Span.state(upper)).getFluidState().isEmpty() && !BlockShapes.loose(Span.state(upper))) {
                  this.data[base + i] = Span.withBottom(upper, Span.top(this.data[base + i + 1]));
               }
            }
         }
      }
   }

   public boolean has(int x, int z) {
      return this.counts[LodTile.cell(x, z)] > 0;
   }

   public LodTile build(int level, int tx, int tz, long realMask, boolean complete) {
      if (level > 0) {
         this.closeGaps(level == 1 ? 8 : 16);
      }

      int[] offsets = new int[4357];
      int total = 0;

      for (int c = 0; c < 4356; c++) {
         offsets[c] = total;
         total += this.counts[c];
      }

      offsets[4356] = total;
      long[] spans = new long[total];

      for (int c = 0; c < 4356; c++) {
         if (this.counts[c] > 0) {
            System.arraycopy(this.data, this.starts[c], spans, offsets[c], this.counts[c]);
         }
      }

      this.borrowed = false;
      return new LodTile(level, tx, tz, offsets, spans, realMask, complete);
   }
}
