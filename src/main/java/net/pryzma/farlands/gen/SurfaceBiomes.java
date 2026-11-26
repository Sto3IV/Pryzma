package net.pryzma.farlands.gen;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.densityfunction.DensityBufferPool;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler.Bound;
import org.jspecify.annotations.Nullable;

public final class SurfaceBiomes {
   private final DensityBufferPool pool = new DensityBufferPool(16);
   private final Bound estimate;
   private final BiomeResolver resolver;
   private final int minQuart;
   private final int maxQuart;

   private SurfaceBiomes(GenWorld world, NoiseBasedChunkGenerator generator) {
      NoiseGeneratorSettings settings = (NoiseGeneratorSettings)generator.generatorSettings().value();
      SamplerContext context = SamplerContext.builder()
         .setUserFields(ContextMap.builder().set(Beardifier.CONTEXT_KEY, Beardifier.EMPTY).build())
         .useBufferArena(this.pool)
         .enableCaches()
         .build();
      this.estimate = world.randomState.samplersWithContext(context).get(new StripInterpolation().rewrite(settings.noiseRouter().chunkSurfaceLevel()));
      this.resolver = generator.getBiomeSource().createResolver(world.randomState.createClimateSampler(context));
      NoiseSettings noise = settings.noiseSettings().clampToHeightAccessor(world.level);
      this.minQuart = noise.minY() >> 2;
      this.maxQuart = (noise.minY() + noise.height() >> 2) - 1;
   }

   public static @Nullable SurfaceBiomes create(ServerLevel level) {
      return level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator generator && !level.dimensionType().hasCeiling()
         ? new SurfaceBiomes(GenWorld.of(level), generator)
         : null;
   }

   public int minQuart() {
      return this.minQuart;
   }

   public SurfaceBiomes.Result compute(BiomeGrid.Layout layout) {
      int size = layout.size();
      int spacing = layout.stride() * 4;
      Holder<Biome>[] biomes = new Holder[size * size];
      int[] quartY = new int[size * size];
      DensityVolume volume = new DensityVolume(size, 1, size, layout.originQX() * 4, 0, layout.originQZ() * 4, spacing, 1, spacing);

      try {
         ScopedDensityBuffer buffer = this.estimate.sampleVolume(volume);

         try {
            for (int z = 0; z < size; z++) {
               for (int x = 0; x < size; x++) {
                  int qy = Math.max(this.minQuart, Math.min(this.maxQuart, (int)Math.floor(buffer.get(volume.indexUnchecked(x, 0, z))) >> 2));
                  int index = z * size + x;
                  quartY[index] = qy;
                  biomes[index] = this.resolver.getNoiseBiome(layout.originQX() + x * layout.stride(), qy, layout.originQZ() + z * layout.stride());
               }
            }
         } catch (Throwable var17) {
            if (buffer != null) {
               try {
                  buffer.close();
               } catch (Throwable var16) {
                  var17.addSuppressed(var16);
               }
            }

            throw var17;
         }

         if (buffer != null) {
            buffer.close();
         }
      } finally {
         this.pool.garbageCollect();
      }

      return new SurfaceBiomes.Result(biomes, quartY);
   }

   public record Result(Holder<Biome>[] biomes, int[] quartY) {
   }
}
