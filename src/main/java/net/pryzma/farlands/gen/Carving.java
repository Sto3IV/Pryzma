package net.pryzma.farlands.gen;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.chunk.CarverOutput;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.carver.WorldCarver;
import org.jspecify.annotations.Nullable;

final class Carving {
   private static final int RANGE = 8;
   private static final int MAX_CACHED = 16384;
   private final ChunkGenerator generator;
   private final WorldGenerationContext context;
   private final long seed;
   private final int minY;
   private final int maxY;
   private final ConcurrentHashMap<Long, Carving.Mask> masks = new ConcurrentHashMap<>();
   private final ConcurrentHashMap<Long, BiomeGenerationSettings> sources = new ConcurrentHashMap<>();
   private final boolean any;

   Carving(ChunkGenerator generator, WorldGenerationContext context, long seed, Set<Holder<Biome>> biomes) {
      this.generator = generator;
      this.context = context;
      this.seed = seed;
      this.minY = context.getMinGenY() + 1;
      this.maxY = context.getMinGenY() + context.getGenDepth() - 1 - 7;
      boolean found = false;

      for (Holder<Biome> biome : biomes) {
         if (generator.getBiomeGenerationSettings(biome).getCarvers().iterator().hasNext()) {
            found = true;
            break;
         }
      }

      this.any = found;
   }

   boolean active() {
      return this.any;
   }

   Carving.@Nullable Mask mask(int chunkX, int chunkZ, Function<ChunkPos, Holder<Biome>> biomeAt) {
      long key = ChunkPos.pack(chunkX, chunkZ);
      Carving.Mask cached = this.masks.get(key);
      if (cached != null) {
         return cached.empty() ? null : cached;
      }

      ChunkPos chunk = new ChunkPos(chunkX, chunkZ);
      Carving.Mask mask = new Carving.Mask(this.minY, this.maxY);
      WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));

      for (int dx = -8; dx <= 8; dx++) {
         for (int dz = -8; dz <= 8; dz++) {
            ChunkPos source = new ChunkPos(chunkX + dx, chunkZ + dz);
            long sourceKey = ChunkPos.pack(source.x(), source.z());
            BiomeGenerationSettings settings = this.sources.get(sourceKey);
            if (settings == null) {
               settings = this.generator.getBiomeGenerationSettings(biomeAt.apply(source));
               if (this.sources.size() > 65536) {
                  this.sources.clear();
               }

               this.sources.put(sourceKey, settings);
            }

            int index = 0;

            for (Holder<WorldCarver> holder : settings.getCarvers()) {
               WorldCarver carver = (WorldCarver)holder.value();
               random.setLargeFeatureSeed(this.seed + index, source.x(), source.z());
               if (carver.isStartChunk(random)) {
                  carver.carve(this.context, random, chunk, source, mask);
               }

               index++;
            }
         }
      }

      if (this.masks.size() > 16384) {
         this.masks.clear();
      }

      this.masks.put(key, mask);
      return mask.empty() ? null : mask;
   }

   static final class Mask implements CarverOutput {
      private final int minY;
      private final int maxY;
      private final int words;
      private final long[] bits;
      private boolean any;

      Mask(int minY, int maxY) {
         this.minY = minY;
         this.maxY = maxY;
         this.words = Math.max(1, maxY - minY + 64 >> 6);
         this.bits = new long[maxY >= minY ? 256 * this.words : 0];
      }

      public void carve(int x, int y, int z) {
         if (y >= this.minY && y <= this.maxY) {
            int i = y - this.minY;
            this.bits[((z & 15) << 4 | x & 15) * this.words + (i >> 6)] = this.bits[((z & 15) << 4 | x & 15) * this.words + (i >> 6)] | 1L << (i & 63);
            this.any = true;
         }
      }

      public int minY() {
         return this.minY;
      }

      public int maxY() {
         return this.maxY;
      }

      boolean empty() {
         return !this.any;
      }

      boolean carved(int x, int y, int z) {
         if (y >= this.minY && y <= this.maxY) {
            int i = y - this.minY;
            return (this.bits[((z & 15) << 4 | x & 15) * this.words + (i >> 6)] >>> (i & 63) & 1L) != 0L;
         } else {
            return false;
         }
      }
   }
}
