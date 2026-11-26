package net.pryzma.farlands.gen;

import java.util.Arrays;
import net.minecraft.core.Holder;
import net.minecraft.util.LinearCongruentialGenerator;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeResolver;

final class ZoomBiomes extends BiomeManager {
   private static final int BITS = 9;
   private static final int SLOTS = 512;
   private final BiomeResolver resolver;
   private final long seed;
   private final long[] keys = new long[512];
   private final double[] fiddles = new double[12288];

   ZoomBiomes(BiomeResolver resolver, long seed) {
      super(resolver, seed);
      this.resolver = resolver;
      this.seed = seed;
      Arrays.fill(this.keys, Long.MIN_VALUE);
   }

   private static long pack(int x, int y, int z) {
      return (x & 67108863L) << 38 | (z & 67108863L) << 12 | y & 4095L;
   }

   private static double fiddle(long value) {
      double uniform = Math.floorMod(value >> 24, 1024) / 1024.0;
      return (uniform - 0.5) * 0.9;
   }

   private void fill(int base, int parentX, int parentY, int parentZ) {
      for (int i = 0; i < 8; i++) {
         int cornerX = (i & 4) == 0 ? parentX : parentX + 1;
         int cornerY = (i & 2) == 0 ? parentY : parentY + 1;
         int cornerZ = (i & 1) == 0 ? parentZ : parentZ + 1;
         long value = this.seed;
         value = LinearCongruentialGenerator.next(value, cornerX);
         value = LinearCongruentialGenerator.next(value, cornerY);
         value = LinearCongruentialGenerator.next(value, cornerZ);
         value = LinearCongruentialGenerator.next(value, cornerX);
         value = LinearCongruentialGenerator.next(value, cornerY);
         value = LinearCongruentialGenerator.next(value, cornerZ);
         this.fiddles[base + i * 3] = fiddle(value);
         value = LinearCongruentialGenerator.next(value, this.seed);
         this.fiddles[base + i * 3 + 1] = fiddle(value);
         value = LinearCongruentialGenerator.next(value, this.seed);
         this.fiddles[base + i * 3 + 2] = fiddle(value);
      }
   }

   public Holder<Biome> getBiome(int x, int y, int z) {
      int absX = x - 2;
      int absY = y - 2;
      int absZ = z - 2;
      int parentX = absX >> 2;
      int parentY = absY >> 2;
      int parentZ = absZ >> 2;
      double fractX = (absX & 3) / 4.0;
      double fractY = (absY & 3) / 4.0;
      double fractZ = (absZ & 3) / 4.0;
      long key = pack(parentX, parentY, parentZ);
      int slot = (int)(key * -7046029254386353131L >>> 55);
      int base = slot * 24;
      if (this.keys[slot] != key) {
         this.fill(base, parentX, parentY, parentZ);
         this.keys[slot] = key;
      }

      int minI = 0;
      double min = Double.POSITIVE_INFINITY;

      for (int i = 0; i < 8; i++) {
         double distanceX = (i & 4) == 0 ? fractX : fractX - 1.0;
         double distanceY = (i & 2) == 0 ? fractY : fractY - 1.0;
         double distanceZ = (i & 1) == 0 ? fractZ : fractZ - 1.0;
         double dz = distanceZ + this.fiddles[base + i * 3 + 2];
         double dy = distanceY + this.fiddles[base + i * 3 + 1];
         double dx = distanceX + this.fiddles[base + i * 3];
         double next = dz * dz + dy * dy + dx * dx;
         if (min > next) {
            minI = i;
            min = next;
         }
      }

      return this.resolver
         .getNoiseBiome((minI & 4) == 0 ? parentX : parentX + 1, (minI & 2) == 0 ? parentY : parentY + 1, (minI & 1) == 0 ? parentZ : parentZ + 1);
   }
}
