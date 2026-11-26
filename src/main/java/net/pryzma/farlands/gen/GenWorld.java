package net.pryzma.farlands.gen;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouterData;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.jspecify.annotations.Nullable;

public final class GenWorld {
   public final RegistryAccess registries;
   public final ResourceKey<Level> dimension;
   public final long seed;
   public final ChunkGenerator generator;
   public final RandomState randomState;
   public final ChunkGeneratorStructureState structureState;
   public final @Nullable StructureTemplateManager templates;
   public final StructureManager structures;
   public final boolean generateStructures;
   public final Level level;
   public @Nullable ServerLevel serverLevel;
   public @Nullable ServerBiomes serverBiomes;
   public final PalettedContainerFactory palettes;

   private GenWorld(
      RegistryAccess registries,
      ResourceKey<Level> dimension,
      long seed,
      ChunkGenerator generator,
      RandomState randomState,
      ChunkGeneratorStructureState structureState,
      @Nullable StructureTemplateManager templates,
      StructureManager structures,
      boolean generateStructures,
      Level level,
      @Nullable ServerLevel serverLevel,
      PalettedContainerFactory palettes
   ) {
      this.registries = registries;
      this.dimension = dimension;
      this.seed = seed;
      this.generator = generator;
      this.randomState = randomState;
      this.structureState = structureState;
      this.templates = templates;
      this.structures = structures;
      this.generateStructures = generateStructures;
      this.level = level;
      this.serverLevel = serverLevel;
      this.palettes = palettes;
   }

   public static GenWorld of(ServerLevel level) {
      return new GenWorld(
         level.registryAccess(),
         level.dimension(),
         level.getSeed(),
         level.getChunkSource().getGenerator(),
         level.getChunkSource().randomState(),
         level.getChunkSource().getGeneratorState(),
         level.getStructureTemplateManager(),
         level.structureManager(),
         level.structureManager().shouldGenerateStructures(),
         level,
         level,
         level.palettedContainerFactory()
      );
   }

   public static GenWorld remote(
      Level level, RegistryAccess registries, long seed, ChunkGenerator generator, boolean generateStructures, @Nullable StructureTemplateManager templates
   ) {
      RandomState randomState;
      if (generator instanceof NoiseBasedChunkGenerator noise) {
         randomState = RandomState.create(registries.lookupOrThrow(Registries.NOISE), seed, (NoiseGeneratorSettings)noise.generatorSettings().value());
      } else {
         randomState = RandomState.create(registries.lookupOrThrow(Registries.NOISE), seed, false, Blocks.STONE.defaultBlockState(), 63, NoiseRouterData.none());
      }

      ChunkGeneratorStructureState structureState = generator.createState(registries.lookupOrThrow(Registries.STRUCTURE_SET), randomState, seed);
      structureState.ensureStructuresGenerated();
      StructureManager structures = new StructureManager(level, new WorldOptions(seed, generateStructures, false), null);
      GenWorld world = new GenWorld(
         registries,
         level.dimension(),
         seed,
         generator,
         randomState,
         structureState,
         templates,
         structures,
         generateStructures,
         level,
         null,
         PalettedContainerFactory.create(registries)
      );
      world.serverLevel = LevelStub.create(world);
      return world;
   }
}
