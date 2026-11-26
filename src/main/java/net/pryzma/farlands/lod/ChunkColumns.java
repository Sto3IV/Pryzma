package net.pryzma.farlands.lod;

import java.util.Arrays;

public final class ChunkColumns {
   public final int chunkX;
   public final int chunkZ;
   public final int[] offsets;
   public final long[] spans;

   public ChunkColumns(int chunkX, int chunkZ, int[] offsets, long[] spans) {
      this.chunkX = chunkX;
      this.chunkZ = chunkZ;
      this.offsets = offsets;
      this.spans = spans;
   }

   public int first(int x, int z) {
      return this.offsets[z * 16 + x];
   }

   public int count(int x, int z) {
      int i = z * 16 + x;
      return this.offsets[i + 1] - this.offsets[i];
   }

   public static final class Builder {
      private final int[] offsets = new int[257];
      private long[] spans = new long[1024];
      private int size;
      private int column;
      private int columnStart;

      public ChunkColumns.Builder reset() {
         this.size = 0;
         this.column = 0;
         this.columnStart = 0;
         return this;
      }

      public void beginColumn() {
         this.columnStart = this.size;
      }

      public void add(long span) {
         if (this.size > this.columnStart) {
            long last = this.spans[this.size - 1];
            if (Span.state(last) == Span.state(span)
               && Span.bottom(last) == Span.top(span)
               && Span.biome(last) == Span.biome(span)
               && Span.sky(last) == Span.sky(span)
               && Span.block(last) == Span.block(span)) {
               this.spans[this.size - 1] = Span.withBottom(last, Span.bottom(span));
               return;
            }
         }

         if (this.size == this.spans.length) {
            this.spans = Arrays.copyOf(this.spans, this.spans.length * 2);
         }

         this.spans[this.size++] = span;
      }

      public void endColumn() {
         this.offsets[this.column] = this.columnStart;
         this.column++;
         this.offsets[this.column] = this.size;
      }

      public ChunkColumns build(int chunkX, int chunkZ) {
         return new ChunkColumns(chunkX, chunkZ, (int[])this.offsets.clone(), Arrays.copyOf(this.spans, this.size));
      }
   }
}
