package net.pryzma.farlands.gen;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.Nullable;

public final class BiomeGrid {
   public static final int MARGIN = 8;
   private final BiomeGrid.Layout layout;
   private final @Nullable Holder<Biome>[] biomes;
   private final int[] quartY;

   public BiomeGrid(BiomeGrid.Layout layout, @Nullable Holder<Biome>[] biomes, int[] quartY) {
      this.layout = layout;
      this.biomes = biomes;
      this.quartY = quartY;
   }

   public static BiomeGrid.Layout layout(int level, int tx, int tz) {
      int step = 1 << level;
      if (level <= 2) {
         int quarts = 64 * step / 4;
         return new BiomeGrid.Layout(tx * quarts - 8, tz * quarts - 8, 1, quarts + 16);
      } else {
         int x0 = tx * 64 * step - step;
         int z0 = tz * 64 * step - step;
         return new BiomeGrid.Layout(x0 >> 2, z0 >> 2, step >> 2, 66);
      }
   }

   public int index(int qx, int qz) {
      int dx = qx - this.layout.originQX();
      int dz = qz - this.layout.originQZ();
      int stride = this.layout.stride();
      int ix = stride == 1 ? dx : Math.floorDiv(dx + stride / 2, stride);
      int iz = stride == 1 ? dz : Math.floorDiv(dz + stride / 2, stride);
      if (ix < 0 || iz < 0) {
         return -1;
      } else {
         return ix < this.layout.size() && iz < this.layout.size() ? iz * this.layout.size() + ix : -1;
      }
   }

   public @Nullable Holder<Biome> biome(int index) {
      return this.biomes[index];
   }

   public int quartY(int index) {
      return this.quartY[index];
   }

   public record Layout(int originQX, int originQZ, int stride, int size) {
   }
}
