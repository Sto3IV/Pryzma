package net.pryzma.farlands.gen;

import net.pryzma.farlands.Farlands;
import net.pryzma.farlands.mixin.NoiseColumnInvoker;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;

public final class EffectiveDensity {
   private static final ThreadLocal<DensityFunction[]> CAPTURE = new ThreadLocal<>();

   private EffectiveDensity() {
   }

   public static void seen(DensityFunction density) {
      DensityFunction[] slot = CAPTURE.get();
      if (slot != null && slot[0] == null) {
         slot[0] = density;
      }
   }

   public static DensityFunction of(NoiseBasedChunkGenerator generator, GenWorld world, DensityFunction fallback) {
      DensityFunction[] slot = new DensityFunction[1];
      CAPTURE.set(slot);

      try {
         ProtoChunk chunk = new ProtoChunk(new ChunkPos(0, 0), UpgradeData.EMPTY, world.level, world.palettes, null);
         NoiseSettings noise = ((NoiseGeneratorSettings)generator.generatorSettings().value())
            .noiseSettings()
            .clampToHeightAccessor(chunk.getHeightAccessorForGeneration());
         NoiseColumnInvoker invoker = (NoiseColumnInvoker)generator;
         NoiseChunk noiseChunk = invoker.prFarlands$createNoiseChunk(chunk, world.structures, Blender.empty(), world.randomState, noise);

         try {
            invoker.prFarlands$doFill(noiseChunk, chunk);
         } catch (Throwable var16) {
            if (noiseChunk != null) {
               try {
                  noiseChunk.close();
               } catch (Throwable var15) {
                  var16.addSuppressed(var15);
               }
            }

            throw var16;
         }

         if (noiseChunk != null) {
            noiseChunk.close();
         }
      } catch (RuntimeException | LinkageError e) {
         Farlands.LOG.warn("Generator density capture failed", e);
         return fallback;
      } finally {
         CAPTURE.remove();
      }

      DensityFunction found = slot[0];
      if (found == null) {
         return fallback;
      }

      if (found != fallback) {
         Farlands.LOG.info("Distant terrain uses the generator's modified final density {}", found.getClass().getName());
      }

      return found;
   }
}
