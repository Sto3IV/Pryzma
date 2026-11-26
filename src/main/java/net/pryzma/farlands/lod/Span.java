package net.pryzma.farlands.lod;

public final class Span {
   public static final int Y_OFFSET = 2048;
   public static final int MIN_Y = -2048;
   public static final int MAX_Y = 2047;

   private Span() {
   }

   public static long pack(int top, int bottom, int state, int biome, int sky, int block) {
      return clampY(top) + 2048
         | (long)(clampY(bottom) + 2048) << 12
         | (long)(state & 1048575) << 24
         | (long)(biome & 4095) << 44
         | (long)(sky & 15) << 56
         | (long)(block & 15) << 60;
   }

   public static int top(long span) {
      return (int)(span & 4095L) - 2048;
   }

   public static int bottom(long span) {
      return (int)(span >>> 12 & 4095L) - 2048;
   }

   public static int state(long span) {
      return (int)(span >>> 24 & 1048575L);
   }

   public static int biome(long span) {
      return (int)(span >>> 44 & 4095L);
   }

   public static int sky(long span) {
      return (int)(span >>> 56 & 15L);
   }

   public static int block(long span) {
      return (int)(span >>> 60 & 15L);
   }

   public static long withTop(long span, int top) {
      return span & -4096L | clampY(top) + 2048;
   }

   public static long withBottom(long span, int bottom) {
      return span & -16773121L | (long)(clampY(bottom) + 2048) << 12;
   }

   private static int clampY(int y) {
      return Math.max(-2048, Math.min(2047, y));
   }
}
