package net.pryzma.farlands.lod;

import net.minecraft.world.level.block.Block;

public final class LodTile {
   public static final int SIZE = 64;
   public static final int SHIFT = 6;
   public static final int STRIDE = 66;
   public static final int CELLS = 4356;
   public static final int MAX_LEVEL = 7;
   public final int level;
   public final int tx;
   public final int tz;
   public final int[] offsets;
   public final long[] spans;
   public final int minY;
   public final int maxY;
   public final int surfaceY;
   public final long realMask;
   public final boolean complete;

   public LodTile(int level, int tx, int tz, int[] offsets, long[] spans, long realMask, boolean complete) {
      this.level = level;
      this.tx = tx;
      this.tz = tz;
      this.offsets = offsets;
      this.spans = spans;
      this.realMask = realMask;
      this.complete = complete;
      int lo = Integer.MAX_VALUE;
      int hi = Integer.MIN_VALUE;
      int surface = Integer.MAX_VALUE;

      for (int z = 0; z < 64; z++) {
         for (int x = 0; x < 64; x++) {
            int c = cell(x, z);
            int from = offsets[c];
            int to = offsets[c + 1];
            if (from != to) {
               hi = Math.max(hi, Span.top(spans[from]));
               int low = Span.top(spans[from]);

               for (int i = from; i < to; i++) {
                  low = Math.min(low, Math.max(Span.bottom(spans[i]), Span.top(spans[i]) - 48));
               }

               lo = Math.min(lo, low);
               int ground = from;

               while (ground + 1 < to && !Block.stateById(Span.state(spans[ground])).getFluidState().isEmpty()) {
                  ground++;
               }

               surface = Math.min(surface, Span.top(spans[ground]));
            }
         }
      }

      this.minY = lo == Integer.MAX_VALUE ? 0 : lo;
      this.maxY = hi == Integer.MIN_VALUE ? 0 : hi;
      this.surfaceY = surface == Integer.MAX_VALUE ? this.minY : surface;
   }

   public static int cell(int x, int z) {
      return (z + 1) * 66 + x + 1;
   }

   public int count(int x, int z) {
      int c = cell(x, z);
      return this.offsets[c + 1] - this.offsets[c];
   }

   public int first(int x, int z) {
      return this.offsets[cell(x, z)];
   }

   public int topY(int x, int z) {
      int c = cell(x, z);
      return this.offsets[c] == this.offsets[c + 1] ? Integer.MIN_VALUE : Span.top(this.spans[this.offsets[c]]);
   }

   public boolean isEmpty() {
      return this.spans.length == 0;
   }

   public long key() {
      return key(this.level, this.tx, this.tz);
   }

   public int blockSize() {
      return 64 << this.level;
   }

   public int originX() {
      return this.tx * this.blockSize();
   }

   public int originZ() {
      return this.tz * this.blockSize();
   }

   public static long key(int level, int tx, int tz) {
      return (long)level << 58 | (tx & 536870911L) << 29 | tz & 536870911L;
   }

   public static int keyLevel(long key) {
      return (int)(key >>> 58);
   }

   public static int keyX(long key) {
      return (int)(key << 6 >> 35);
   }

   public static int keyZ(long key) {
      return (int)(key << 35 >> 35);
   }
}
