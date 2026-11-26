package net.pryzma.farlands.gen;

import net.pryzma.farlands.lod.BlockShapes;
import net.pryzma.farlands.lod.LodTile;
import net.pryzma.farlands.lod.Span;
import net.pryzma.farlands.lod.TileBuilder;
import net.pryzma.farlands.work.Slots;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import org.jspecify.annotations.Nullable;

public final class BaseColumnTerrain implements TerrainSource {
   private static final int DEPTH = 12;
   private final Level level;
   private final ChunkGenerator generator;
   private final RandomState randomState;
   private final BiomeIds biomeIds;
   private final Slots<BiomeResolver> resolvers;
   private final Slots<TileBuilder> builders = new Slots<>(TileBuilder::new);

   public BaseColumnTerrain(GenWorld world, ChunkGenerator generator, BiomeIds biomeIds) {
      this.level = world.level;
      this.generator = generator;
      this.randomState = world.randomState;
      this.biomeIds = biomeIds;
      this.resolvers = new Slots<>(() -> generator.getBiomeSource().createCachingResolver(this.randomState));
   }

   @Override
   public @Nullable LodTile generate(int lodLevel, int tx, int tz) {
      TileBuilder b = this.builders.take();
      BiomeResolver biomes = this.resolvers.take();

      try {
         return this.generate(b, biomes, lodLevel, tx, tz);
      } finally {
         this.builders.give(b);
         this.resolvers.give(biomes);
      }
   }

   @Override
   public void close() {
      this.builders.clear();
      this.resolvers.clear();
   }

   private LodTile generate(TileBuilder b, BiomeResolver biomes, int lodLevel, int tx, int tz) {
      int step = 1 << lodLevel;
      int x0 = tx * 64 * step;
      int z0 = tz * 64 * step;
      int minY = this.level.getMinY();

      for (int cz = -1; cz <= 64; cz++) {
         for (int cx = -1; cx <= 64; cx++) {
            int bx = x0 + cx * step;
            int bz = z0 + cz * step;
            b.begin();

            NoiseColumn column;
            try {
               column = this.generator.getBaseColumn(bx, bz, this.level, this.randomState);
            } catch (RuntimeException e) {
               b.end(cx, cz);
               continue;
            }

            int y = this.level.getMaxY();

            while (y >= minY && column.getBlock(y).isAir()) {
               y--;
            }

            if (y < minY) {
               b.end(cx, cz);
            } else {
               Holder<Biome> biome = biomes.getNoiseBiome(bx >> 2, y >> 2, bz >> 2);
               int biomeId = this.biomeIds.id(biome);

               for (int taken = 0; y >= minY && taken < 12; taken++) {
                  BlockState state = column.getBlock(y);
                  int id = BlockShapes.stored(state);
                  if (id != -1) {
                     b.add(Span.pack(y + 1, y, id, biomeId, 15, state.getLightEmission()));
                  }

                  y--;
               }

               if (y >= minY) {
                  int id = BlockShapes.stored(column.getBlock(y));
                  if (id != -1) {
                     b.add(Span.pack(y + 1, minY, id, biomeId, 15, 0));
                  }
               }

               b.end(cx, cz);
            }
         }
      }

      return b.build(lodLevel, tx, tz, 0L, true);
   }
}
