package net.pryzma.farlands.gen;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import net.pryzma.farlands.Farlands;
import net.pryzma.farlands.lod.BlockShapes;
import net.pryzma.farlands.lod.LodTile;
import net.pryzma.farlands.lod.Span;
import net.pryzma.farlands.lod.TileBuilder;
import net.pryzma.farlands.work.Slots;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.SectionPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.ResourceManager.Empty;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.Climate.Sampler;
import net.minecraft.world.level.biome.FeatureSorter.StepFeatureData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.GenerationStep.Decoration;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.feature.AbstractOreFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.LakeFeature;
import net.minecraft.world.level.levelgen.feature.SnowAndFreezeFeature;
import net.minecraft.world.level.levelgen.heightproviders.ConstantHeight;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.placement.FeaturePlacer;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.RarityFilter;
import net.minecraft.world.level.levelgen.placement.SurfaceRelativeThresholdFilter;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.StructurePiece.BlockSelector;
import net.minecraft.world.level.levelgen.structure.StructureSet.StructureSelectionEntry;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.DimensionOriginStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.Palette;
import org.jspecify.annotations.Nullable;

public final class Vegetation {
   public static final int FINE_LEVEL = 2;
   private static final boolean[] STEPS = steps();
   private static final int FAILURE_LIMIT = 8;
   private static final Map<Class<?>, Object> STRUCTURE_LOCKS = new ConcurrentHashMap<>();
   private static final int WINDOW = 96;
   private final GenWorld world;
   private final ChunkGenerator generator;
   private final Supplier<List<StepFeatureData>> steps;
   private final Function<Holder<Biome>, BiomeGenerationSettings> settings;
   private final Set<Holder<Biome>> sourceBiomes;
   private final BlockState defaultBlock;
   private final Map<PlacedFeature, AtomicInteger> failures = new ConcurrentHashMap<>();
   private final Map<PlacedFeature, Vegetation.FeatureInfo> infos = new ConcurrentHashMap<>();
   private final WorldGenerationContext heightContext;
   private final Slots<LodGenLevel> levels;
   private final Slots<BlockState[]> windows = new Slots<>(() -> new BlockState[96]);
   private final Map<Long, List<Vegetation.StartRecipe>> structureStarts = new ConcurrentHashMap<>();
   private final Set<Class<?>> failedPlacements = ConcurrentHashMap.newKeySet();
   private final @Nullable StructurePieceSerializationContext pieceContext;
   private volatile @Nullable List<StructurePlacement> visiblePlacements;
   private final Map<Integer, List<Structure>> structuresByStep;
   private static final boolean PARALLEL_TEMPLATES = concurrentTemplates();
   private static final Map<Class<?>, Boolean> START_SHARED = new ConcurrentHashMap<>();
   private static final Map<Class<?>, Boolean> SHARED_STATE = new ConcurrentHashMap<>();
   private static final int RARE_CHANCE = 16;

   private static boolean[] steps() {
      boolean[] steps = new boolean[Decoration.values().length];

      for (Decoration step : new Decoration[]{
         Decoration.RAW_GENERATION,
         Decoration.LAKES,
         Decoration.LOCAL_MODIFICATIONS,
         Decoration.SURFACE_STRUCTURES,
         Decoration.UNDERGROUND_ORES,
         Decoration.VEGETAL_DECORATION,
         Decoration.TOP_LAYER_MODIFICATION
      }) {
         steps[step.ordinal()] = true;
      }

      return steps;
   }

   private Vegetation(
      GenWorld world,
      ChunkGenerator generator,
      Supplier<List<StepFeatureData>> steps,
      Function<Holder<Biome>, BiomeGenerationSettings> settings,
      BlockState defaultBlock
   ) {
      this.world = world;
      this.generator = generator;
      this.steps = steps;
      this.settings = settings;
      this.sourceBiomes = generator.getBiomeSource().possibleBiomes();
      this.defaultBlock = defaultBlock;
      this.levels = new Slots<>(() -> new LodGenLevel(world, defaultBlock));
      this.heightContext = new WorldGenerationContext(generator, world.level);
      this.structuresByStep = world.registries
         .lookupOrThrow(Registries.STRUCTURE)
         .stream()
         .collect(Collectors.groupingBy(structure -> structure.step().ordinal()));
      this.pieceContext = world.templates == null ? null : new StructurePieceSerializationContext(Empty.INSTANCE, world.registries, world.templates);
   }

   private static boolean concurrentTemplates() {
      try {
         Constructor<Palette> create = Palette.class.getDeclaredConstructor(List.class);
         create.setAccessible(true);
         Field cache = Palette.class.getDeclaredField("cache");
         cache.setAccessible(true);
         return cache.get(create.newInstance(List.of())) instanceof ConcurrentMap;
      } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
         return false;
      }
   }

   private static <T> T locked(Structure structure, Supplier<T> action) {
      if (PARALLEL_TEMPLATES && structure instanceof JigsawStructure) {
         return action.get();
      }

      if (PARALLEL_TEMPLATES && Boolean.FALSE.equals(START_SHARED.get(structure.getClass()))) {
         return action.get();
      }

      synchronized (STRUCTURE_LOCKS.computeIfAbsent(structure.getClass(), type -> new Object())) {
         return action.get();
      }
   }

   private static <T> T placing(Structure structure, StructureStart start, Supplier<T> action) {
      if (PARALLEL_TEMPLATES && !sharedState(start)) {
         return action.get();
      }

      synchronized (STRUCTURE_LOCKS.computeIfAbsent(structure.getClass(), type -> new Object())) {
         return action.get();
      }
   }

   static boolean sharedState(StructureStart start) {
      for (StructurePiece piece : start.getPieces()) {
         if (SHARED_STATE.computeIfAbsent(piece.getClass(), Vegetation::scanSharedState)) {
            return true;
         }
      }

      return false;
   }

   private static boolean scanSharedState(Class<?> type) {
      boolean shared = findSharedState(type);
      Farlands.LOG.info("Distant structure piece {}: {}", type.getName(), shared ? "shared static state, placed one at a time" : "placed in parallel");
      return shared;
   }

   private static boolean findSharedState(Class<?> type) {
      for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
         if (staticState(c)) {
            return true;
         }

         for (Class<?> outer = c.getEnclosingClass(); outer != null; outer = outer.getEnclosingClass()) {
            if (staticState(outer)) {
               return true;
            }
         }
      }

      return false;
   }

   private static boolean staticState(Class<?> type) {
      try {
         for (Field field : type.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) && !field.isSynthetic()) {
               Class<?> declared = field.getType();
               if (!declared.isPrimitive() && declared != String.class && !declared.isEnum()) {
                  field.setAccessible(true);
                  Object value = field.get(null);
                  if (value != null) {
                     if (value instanceof BlockSelector || value instanceof RandomSource || value instanceof Random) {
                        return true;
                     }

                     if ((value instanceof Collection || value instanceof Map) && !unchangeable(value)) {
                        return true;
                     }

                     if (declared.isArray() && !declared.getComponentType().isPrimitive() && !immutableElement(declared.getComponentType())) {
                        return true;
                     }
                  }
               }
            }
         }

         return false;
      } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
         return true;
      }
   }

   private static boolean immutableElement(Class<?> type) {
      return type == String.class
         || type.isEnum()
         || Number.class.isAssignableFrom(type)
         || type == Boolean.class
         || type == Character.class
         || BlockState.class.isAssignableFrom(type)
         || Block.class.isAssignableFrom(type)
         || Identifier.class.isAssignableFrom(type)
         || ResourceKey.class.isAssignableFrom(type)
         || Holder.class.isAssignableFrom(type);
   }

   private static boolean unchangeable(Object value) {
      if (!(value instanceof ConcurrentMap) && !(value instanceof ImmutableCollection) && !(value instanceof ImmutableMap)) {
         String name = value.getClass().getName();
         return name.startsWith("java.util.ImmutableCollections")
            || name.startsWith("java.util.Collections$Unmodifiable")
            || name.startsWith("java.util.Collections$Empty")
            || name.startsWith("java.util.Collections$Singleton");
      } else {
         return true;
      }
   }

   static void rethrowFatal(Throwable e) {
      if (e instanceof VirtualMachineError error && !(e instanceof StackOverflowError)) {
         throw error;
      }
   }

   public static @Nullable Vegetation create(GenWorld world, ChunkGenerator generator, BlockState defaultBlock) {
      try {
         Field stepsField = ChunkGenerator.class.getDeclaredField("featuresPerStep");
         stepsField.setAccessible(true);
         Field settingsField = ChunkGenerator.class.getDeclaredField("generationSettingsGetter");
         settingsField.setAccessible(true);
         Supplier<List<StepFeatureData>> steps = (Supplier<List<StepFeatureData>>)stepsField.get(generator);
         Function<Holder<Biome>, BiomeGenerationSettings> settings = (Function<Holder<Biome>, BiomeGenerationSettings>)settingsField.get(generator);
         return new Vegetation(world, generator, steps, settings, defaultBlock);
      } catch (ReflectiveOperationException | RuntimeException e) {
         Farlands.LOG.warn("Distant vegetation unavailable", e);
         return null;
      }
   }

   public boolean rare(PlacedFeature feature) {
      return this.info(feature).rare();
   }

   public Vegetation.RealResult decorateReal(LodTile tile, int x0, int z0, int step, BiomeManager biomeManager, Vegetation.FeatureFilter real) {
      LodGenLevel gen = this.levels.take();

      try {
         gen.reset(tile, x0, z0, step, biomeManager);
         gen.hideSnow = true;
         int originX = x0 + step;
         int originZ = z0 + step;
         int size = 64 * step;
         Vegetation.Pass pass = new Vegetation.Pass(gen, biomeManager, real, null, true);
         pass.area(
            Math.floorDiv(originX - 16, 16), Math.floorDiv(originZ - 16, 16), Math.floorDiv(originX + size + 15, 16), Math.floorDiv(originZ + size + 15, 16)
         );
         pass.level = Integer.numberOfTrailingZeros(step);

         for (int cz = Math.floorDiv(originZ - 16, 16); cz <= Math.floorDiv(originZ + size + 15, 16); cz++) {
            for (int cx = Math.floorDiv(originX - 16, 16); cx <= Math.floorDiv(originX + size + 15, 16); cx++) {
               this.decorateChunk(pass, cx, cz);
            }
         }

         if (gen.placed.isEmpty()) {
            return new Vegetation.RealResult(tile, null, pass.realChunks);
         }

         boolean[] touched = new boolean[4356];
         LodTile decorated = this.bake(gen, tile, originX, originZ, step, touched);
         gen.shrink();
         return new Vegetation.RealResult(decorated, touched, pass.realChunks);
      } finally {
         this.levels.give(gen);
      }
   }

   public void probe(LodTile tile, int x0, int z0, BiomeManager biomeManager, Vegetation.FeatureCapture capture) {
      LodGenLevel gen = this.levels.take();

      try {
         gen.reset(tile, x0, z0, 1, biomeManager);
         gen.hideSnow = false;
         Vegetation.Pass pass = new Vegetation.Pass(gen, biomeManager, (feature, biome, blockX, blockZ) -> this.capturable(feature), capture, false);
         int chunkX = Math.floorDiv(x0 + 1, 16);
         int chunkZ = Math.floorDiv(z0 + 1, 16);

         for (int cz = chunkZ; cz < chunkZ + 4; cz++) {
            for (int cx = chunkX; cx < chunkX + 4; cx++) {
               this.decorateChunk(pass, cx, cz);
            }
         }

         gen.shrink();
      } finally {
         gen.journaling = false;
         gen.journal.clear();
         this.levels.give(gen);
      }
   }

   private boolean capturable(PlacedFeature feature) {
      return !this.info(feature).freeze() && !(feature.feature().value() instanceof LakeFeature);
   }

   private Vegetation.FeatureInfo info(PlacedFeature feature) {
      return this.infos.computeIfAbsent(feature, this::classify);
   }

   private Vegetation.FeatureInfo classify(PlacedFeature feature) {
      boolean underground = false;
      int maxY = Integer.MAX_VALUE;

      for (PlacementModifier modifier : feature.placement()) {
         if (modifier instanceof HeightRangePlacement range) {
            maxY = Math.min(maxY, this.maxHeight(range.height()));
         } else if (modifier instanceof SurfaceRelativeThresholdFilter filter
            && (filter.heightmap() == Types.OCEAN_FLOOR || filter.heightmap() == Types.OCEAN_FLOOR_WG)
            && filter.maxInclusive() < -1) {
            underground = true;
         }
      }

      Feature root = (Feature)feature.feature().value();
      boolean rare = false;

      for (PlacementModifier modifier : feature.placement()) {
         if (modifier instanceof RarityFilter rarity && rarityChance(rarity) >= 16) {
            rare = true;
         }
      }

      return new Vegetation.FeatureInfo(root instanceof SnowAndFreezeFeature, underground, maxY, rare);
   }

   private static int rarityChance(RarityFilter rarity) {
      try {
         for (Field field : RarityFilter.class.getDeclaredFields()) {
            if (field.getType() == int.class && !Modifier.isStatic(field.getModifiers())) {
               field.setAccessible(true);
               return field.getInt(rarity);
            }
         }

         return 0;
      } catch (ReflectiveOperationException | RuntimeException e) {
         return 0;
      }
   }

   private int maxHeight(HeightProvider provider) {
      try {
         if (provider instanceof ConstantHeight constant) {
            return constant.getValue().resolveY(this.heightContext);
         } else {
            for (Class<?> type = provider.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
               for (Field field : type.getDeclaredFields()) {
                  if (field.getName().equals("maxInclusive") && VerticalAnchor.class.isAssignableFrom(field.getType())) {
                     field.setAccessible(true);
                     return ((VerticalAnchor)field.get(provider)).resolveY(this.heightContext);
                  }
               }
            }

            return Integer.MAX_VALUE;
         }
      } catch (ReflectiveOperationException | RuntimeException e) {
         return Integer.MAX_VALUE;
      }
   }

   private void decorateChunk(Vegetation.Pass pass, int cx, int cz) {
      LodGenLevel gen = pass.gen;
      Set<Holder<Biome>> biomes = pass.biomes;
      WorldgenRandom random = pass.random;
      List<StepFeatureData> stepData = pass.stepData;
      gen.region(cx, cz);
      biomes.clear();

      for (int sz = -8; sz < 32; sz += 16) {
         for (int sx = -8; sx < 32; sx += 16) {
            int bx = cx * 16 + sx;
            int bz = cz * 16 + sz;
            int h = gen.getHeight(Types.WORLD_SURFACE_WG, bx, bz);
            biomes.add(pass.biomeManager.getBiome(pass.probe.set(bx, h - 1, bz)));
         }
      }

      biomes.retainAll(this.sourceBiomes);
      if (!biomes.isEmpty()) {
         Set<Holder<Biome>> inside = pass.inside;
         inside.clear();
         if (pass.capture == null) {
            for (int sz = 1; sz < 16; sz += 7) {
               for (int sx = 1; sx < 16; sx += 7) {
                  int bx = cx * 16 + sx;
                  int bz = cz * 16 + sz;
                  int h = gen.getHeight(Types.WORLD_SURFACE_WG, bx, bz);
                  inside.add(pass.biomeManager.getBiome(pass.probe.set(bx, h - 1, bz)));
               }
            }

            inside.retainAll(this.sourceBiomes);
         }

         int floor = Integer.MAX_VALUE;

         for (int sz = 0; sz < 16; sz += 7) {
            for (int sx = 0; sx < 16; sx += 7) {
               floor = Math.min(floor, gen.getHeight(Types.OCEAN_FLOOR_WG, cx * 16 + sx + 1, cz * 16 + sz + 1));
            }
         }

         BlockPos origin = SectionPos.of(new ChunkPos(cx, cz), this.world.level.getMinSectionY()).origin();
         long decorationSeed = random.setDecorationSeed(this.world.seed, origin.getX(), origin.getZ());
         BoundingBox box = new BoundingBox(cx * 16, this.world.level.getMinY(), cz * 16, cx * 16 + 15, this.world.level.getMaxY(), cz * 16 + 15);
         List<StructureStart> starts = pass.structures ? this.startsFor(pass, cx, cz, box) : List.of();
         ChunkPos chunk = new ChunkPos(cx, cz);
         int steps = Math.max(Decoration.values().length, stepData.size());

         for (int stepIndex = 0; stepIndex < steps; stepIndex++) {
            if (stepIndex >= STEPS.length || STEPS[stepIndex]) {
               if (!starts.isEmpty()) {
                  this.placeStructures(gen, starts, box, chunk, random, decorationSeed, stepIndex);
               }

               if (stepIndex < stepData.size()) {
                  StepFeatureData data = stepData.get(stepIndex);
                  IntSet indices = new IntArraySet();

                  for (Holder<Biome> biome : biomes) {
                     List<HolderSet<PlacedFeature>> features = this.settings.apply(biome).features();
                     if (stepIndex < features.size()) {
                        for (Holder<PlacedFeature> feature : features.get(stepIndex)) {
                           indices.add(data.indexMapping().applyAsInt((PlacedFeature)feature.value()));
                        }
                     }
                  }

                  int[] sorted = indices.toIntArray();
                  Arrays.sort(sorted);

                  for (int index : sorted) {
                     if (index >= 0 && index < data.features().size()) {
                        PlacedFeature feature = (PlacedFeature)data.features().get(index);
                        if (!(feature.feature().value() instanceof AbstractOreFeature)) {
                           Vegetation.FeatureInfo info = this.info(feature);
                           if ((!info.freeze() || pass.capture != null || pass.level < 1)
                              && !info.underground()
                              && (info.maxY() == Integer.MAX_VALUE || info.maxY() + 8 >= floor)
                              && this.wanted(pass, feature, biomes, inside, cx, cz)) {
                              random.setFeatureSeed(decorationSeed, index, stepIndex);
                              if (pass.capture == null) {
                                 pass.realChunks.computeIfAbsent(feature, f -> new LongOpenHashSet()).add(ChunkPos.pack(cx, cz));
                              }

                              try {
                                 if (pass.capture != null) {
                                    this.placeEach(pass, feature, random, origin, stepIndex << 16 | index);
                                 } else {
                                    pass.placer.placeWithBiomeCheck(feature, random, origin);
                                 }
                              } catch (Throwable e) {
                                 gen.journaling = false;
                                 gen.resetStubs();
                                 rethrowFatal(e);
                                 int failed = this.failures.computeIfAbsent(feature, f -> new AtomicInteger()).incrementAndGet();
                                 if (failed == 1 && e instanceof StackOverflowError) {
                                    Farlands.LOG
                                       .warn(
                                          "Distant feature {} overflowed the stack",
                                          this.world
                                             .registries
                                             .lookupOrThrow(Registries.PLACED_FEATURE)
                                             .getResourceKey(feature)
                                             .map(Object::toString)
                                             .orElse("?")
                                       );
                                 } else if (failed == 1) {
                                    Farlands.LOG
                                       .debug(
                                          "Distant feature {} failed",
                                          this.world
                                             .registries
                                             .lookupOrThrow(Registries.PLACED_FEATURE)
                                             .getResourceKey(feature)
                                             .map(Object::toString)
                                             .orElse("?"),
                                          e
                                       );
                                 }

                                 if (failed == 8) {
                                    Farlands.LOG
                                       .info(
                                          "Distant feature {} keeps failing, it is left out where it fails: {}",
                                          this.world
                                             .registries
                                             .lookupOrThrow(Registries.PLACED_FEATURE)
                                             .getResourceKey(feature)
                                             .map(Object::toString)
                                             .orElse("?"),
                                          e.toString()
                                       );
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private boolean wanted(Vegetation.Pass pass, PlacedFeature feature, Set<Holder<Biome>> biomes, Set<Holder<Biome>> inside, int cx, int cz) {
      boolean listed = false;

      for (Holder<Biome> biome : inside) {
         if (this.settings.apply(biome).hasFeature(feature)) {
            if (pass.filter.test(feature, biome, cx * 16 + 8, cz * 16 + 8)) {
               return true;
            }

            listed = true;
         }
      }

      if (listed) {
         return false;
      }

      for (Holder<Biome> biome : biomes) {
         if (this.settings.apply(biome).hasFeature(feature) && pass.filter.test(feature, biome, cx * 16 + 8, cz * 16 + 8)) {
            return true;
         }
      }

      return false;
   }

   private void placeEach(Vegetation.Pass pass, PlacedFeature placed, WorldgenRandom random, BlockPos origin, int kind) {
      LodGenLevel gen = pass.gen;
      Vegetation.FeatureCapture capture = pass.capture;
      Feature feature = (Feature)placed.feature().value();
      List<PlacementModifier> placement = placed.placement();
      PlacementContext context = new PlacementContext(gen, this.generator, Optional.of(placed));
      ArrayList<BlockPos> positions = new ArrayList<>();
      IntArrayList indices = new IntArrayList();
      ArrayList<BlockPos> modified = new ArrayList<>();
      if (placement.isEmpty()) {
         this.placeOne(gen, capture, feature, placed, random, origin, kind);
      } else {
         positions.add(origin);
         indices.add(0);

         while (!positions.isEmpty()) {
            BlockPos pos = positions.removeLast();
            int modifierIndex = indices.removeInt(indices.size() - 1);
            modified.clear();
            placement.get(modifierIndex).modify(context, random, pos, modified::add);
            int next = modifierIndex + 1;
            if (next < placement.size()) {
               for (int i = modified.size() - 1; i >= 0; i--) {
                  positions.add(modified.get(i));
                  indices.add(next);
               }
            } else {
               for (BlockPos target : modified) {
                  this.placeOne(gen, capture, feature, placed, random, target, kind);
               }
            }
         }
      }
   }

   private void placeOne(
      LodGenLevel gen, Vegetation.FeatureCapture capture, Feature feature, PlacedFeature placed, WorldgenRandom random, BlockPos target, int kind
   ) {
      gen.journal.clear();
      gen.journaling = true;

      try {
         feature.place(gen, this.generator, random, target);
      } finally {
         gen.journaling = false;
      }

      if (!gen.journal.isEmpty()) {
         capture.placed(placed, kind, gen.journal, gen);
      }
   }

   static int nearBiome(LodTile tile, int cx, int cz) {
      for (int r = 1; r <= 4; r++) {
         for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
               if (Math.max(Math.abs(dx), Math.abs(dz)) == r) {
                  int x = cx + dx;
                  int z = cz + dz;
                  if (x >= -1 && z >= -1 && x <= 64 && z <= 64 && tile.count(x, z) != 0) {
                     return Span.biome(tile.spans[tile.first(x, z)]);
                  }
               }
            }
         }
      }

      return 0;
   }

   private static int skyAt(LodTile tile, int cx, int cz, int y) {
      int from = tile.first(cx, cz);
      int count = tile.count(cx, cz);

      for (int i = 0; i < count; i++) {
         long span = tile.spans[from + i];
         if (Span.top(span) <= y) {
            return 15;
         }

         if (Span.bottom(span) <= y) {
            return Block.stateById(Span.state(span)).getFluidState().isEmpty() ? Span.sky(span) : Math.max(0, Span.sky(span) - (Span.top(span) - 1 - y));
         }
      }

      return 15;
   }

   private static boolean isVisibleStep(Decoration step) {
      return step.ordinal() < STEPS.length && STEPS[step.ordinal()];
   }

   private List<StructureStart> startsFor(Vegetation.Pass pass, int cx, int cz, BoundingBox box) {
      if (this.world.generateStructures && this.world.templates != null && this.pieceContext != null) {
         LongArrayList candidates = this.candidatesNear(pass, cx, cz);
         List<StructureStart> out = null;

         for (int i = 0; i < candidates.size(); i++) {
            long chunk = candidates.getLong(i);
            List<Vegetation.StartRecipe> recipes = this.recipesAt(chunk);
            StructureStart[] loaded = (StructureStart[])pass.loaded.get(chunk);

            for (int r = 0; r < recipes.size(); r++) {
               Vegetation.StartRecipe recipe = recipes.get(r);
               if (recipe.box.intersects(box)) {
                  if (loaded == null) {
                     loaded = new StructureStart[recipes.size()];
                     pass.loaded.put(chunk, loaded);
                  }

                  StructureStart start = loaded[r];
                  if (start == null) {
                     start = this.load(recipe);
                     if (start == null) {
                        List<StructureStart> fresh = (List<StructureStart>)pass.regenerated.get(chunk);
                        if (fresh == null) {
                           fresh = this.computeStartsSafely(ChunkPos.getX(chunk), ChunkPos.getZ(chunk));
                           pass.regenerated.put(chunk, fresh);
                        }

                        if (fresh.size() != recipes.size()) {
                           continue;
                        }

                        start = fresh.get(r);
                     }

                     loaded[r] = start;
                  }

                  if (out == null) {
                     out = new ArrayList<>(4);
                  }

                  out.add(start);
               }
            }
         }

         return out == null ? List.of() : out;
      } else {
         return List.of();
      }
   }

   private LongArrayList candidatesNear(Vegetation.Pass pass, int cx, int cz) {
      if (!pass.bounded) {
         return this.startCandidates(cx - 8, cz - 8, cx + 8, cz + 8);
      }

      LongArrayList all = pass.candidates;
      if (all == null) {
         all = this.startCandidates(pass.minCx - 8, pass.minCz - 8, pass.maxCx + 8, pass.maxCz + 8);
         pass.candidates = all;
      }

      LongArrayList near = pass.near;
      near.clear();

      for (int i = 0; i < all.size(); i++) {
         long chunk = all.getLong(i);
         if (Math.abs(ChunkPos.getX(chunk) - cx) <= 8 && Math.abs(ChunkPos.getZ(chunk) - cz) <= 8) {
            near.add(chunk);
         }
      }

      return near;
   }

   private List<StructurePlacement> visiblePlacements() {
      List<StructurePlacement> placements = this.visiblePlacements;
      if (placements != null) {
         return placements;
      }

      LinkedHashSet<StructurePlacement> found = new LinkedHashSet<>();

      for (Holder<StructureSet> set : this.world.structureState.possibleStructureSets()) {
         for (StructureSelectionEntry entry : ((StructureSet)set.value()).structures()) {
            if (isVisibleStep(((Structure)entry.structure().value()).step())) {
               found.add(((StructureSet)set.value()).placement());
               break;
            }
         }
      }

      placements = List.copyOf(found);
      this.visiblePlacements = placements;
      return placements;
   }

   private LongArrayList startCandidates(int minX, int minZ, int maxX, int maxZ) {
      ChunkGeneratorStructureState state = this.world.structureState;
      long seed = state.getLevelSeed();
      LongOpenHashSet found = new LongOpenHashSet();

      for (StructurePlacement placement : this.visiblePlacements()) {
         try {
            this.collectCandidates(state, seed, placement, minX, minZ, maxX, maxZ, found);
         } catch (Throwable e) {
            rethrowFatal(e);
            if (this.failedPlacements.add(placement.getClass())) {
               Farlands.LOG.warn("Distant structure placement {} failed: {}", placement.getClass().getName(), e.toString());
            }
         }
      }

      LongArrayList sorted = new LongArrayList(found);
      sorted.sort((a, b) -> {
         int byZ = Integer.compare(ChunkPos.getZ(a), ChunkPos.getZ(b));
         return byZ != 0 ? byZ : Integer.compare(ChunkPos.getX(a), ChunkPos.getX(b));
      });
      return sorted;
   }

   private void collectCandidates(
      ChunkGeneratorStructureState state, long seed, StructurePlacement placement, int minX, int minZ, int maxX, int maxZ, LongOpenHashSet out
   ) {
      LongArrayList found = new LongArrayList();
      if (placement.getClass() == RandomSpreadStructurePlacement.class) {
         RandomSpreadStructurePlacement spread = (RandomSpreadStructurePlacement)placement;
         int spacing = spread.spacing();

         for (int rz = Math.floorDiv(minZ, spacing); rz <= Math.floorDiv(maxZ, spacing); rz++) {
            for (int rx = Math.floorDiv(minX, spacing); rx <= Math.floorDiv(maxX, spacing); rx++) {
               ChunkPos chunk = spread.getPotentialStructureChunk(seed, rx * spacing, rz * spacing);
               if (chunk.x() >= minX && chunk.x() <= maxX && chunk.z() >= minZ && chunk.z() <= maxZ) {
                  found.add(ChunkPos.pack(chunk.x(), chunk.z()));
               }
            }
         }
      } else if (placement.getClass() == ConcentricRingsStructurePlacement.class) {
         List<ChunkPos> ring = state.getRingPositionsFor((ConcentricRingsStructurePlacement)placement);
         if (ring == null) {
            return;
         }

         for (ChunkPos chunk : ring) {
            if (chunk.x() >= minX && chunk.x() <= maxX && chunk.z() >= minZ && chunk.z() <= maxZ) {
               found.add(ChunkPos.pack(chunk.x(), chunk.z()));
            }
         }
      } else if (placement.getClass() == DimensionOriginStructurePlacement.class) {
         ChunkPos chunk = state.getDimensionOrigin();
         if (chunk.x() >= minX && chunk.x() <= maxX && chunk.z() >= minZ && chunk.z() <= maxZ) {
            found.add(ChunkPos.pack(chunk.x(), chunk.z()));
         }
      } else {
         for (int sz = minZ; sz <= maxZ; sz++) {
            for (int sx = minX; sx <= maxX; sx++) {
               if (placement.isStructureChunk(state, sx, sz)) {
                  found.add(ChunkPos.pack(sx, sz));
               }
            }
         }
      }

      out.addAll(found);
   }

   private void placeStructures(
      LodGenLevel gen, List<StructureStart> starts, BoundingBox box, ChunkPos pos, WorldgenRandom random, long decorationSeed, int stepIndex
   ) {
      List<Structure> structures = this.structuresByStep.get(stepIndex);
      if (structures != null) {
         for (int index = 0; index < structures.size(); index++) {
            Structure structure = structures.get(index);
            boolean seeded = false;

            for (StructureStart start : starts) {
               if (start.getStructure() == structure) {
                  if (!seeded) {
                     random.setFeatureSeed(decorationSeed, index, stepIndex);
                     seeded = true;
                  }

                  try {
                     placing(structure, start, () -> {
                        start.placeInChunk(gen, this.world.structures, this.generator, random, box, pos);
                        return null;
                     });
                  } catch (Throwable e) {
                     gen.resetStubs();
                     rethrowFatal(e);
                     if (e instanceof StackOverflowError) {
                        Farlands.LOG.debug("Distant structure piece overflowed the stack");
                     } else {
                        Farlands.LOG.debug("Distant structure piece failed", e);
                     }
                  }
               }
            }
         }
      }
   }

   private List<Vegetation.StartRecipe> recipesAt(long key) {
      List<Vegetation.StartRecipe> recipes = this.structureStarts.get(key);
      if (recipes == null) {
         List<StructureStart> starts = this.computeStartsSafely(ChunkPos.getX(key), ChunkPos.getZ(key));
         if (starts.isEmpty()) {
            recipes = List.of();
         } else {
            ArrayList<Vegetation.StartRecipe> built = new ArrayList<>(starts.size());

            for (StructureStart start : starts) {
               built.add(new Vegetation.StartRecipe(start));
            }

            recipes = List.copyOf(built);
         }

         if (this.structureStarts.size() > 400000) {
            this.structureStarts.clear();
         }

         List<Vegetation.StartRecipe> raced = this.structureStarts.putIfAbsent(key, recipes);
         if (raced != null) {
            recipes = raced;
         }
      }

      return recipes;
   }

   private List<StructureStart> computeStartsSafely(int cx, int cz) {
      try {
         return this.computeStarts(cx, cz);
      } catch (Throwable e) {
         rethrowFatal(e);
         return List.of();
      }
   }

   private @Nullable StructureStart load(Vegetation.StartRecipe recipe) {
      StructurePieceSerializationContext context = this.pieceContext;
      if (context == null) {
         return null;
      }

      try {
         CompoundTag tag = recipe.tag(context);
         if (tag == null) {
            return null;
         }

         StructureStart start = StructureStart.loadStaticStart(context, tag.copy(), this.world.seed);
         return start != null && start.isValid() ? start : null;
      } catch (Throwable e) {
         rethrowFatal(e);
         return null;
      }
   }

   private List<StructureStart> computeStarts(int cx, int cz) {
      ChunkGeneratorStructureState state = this.world.structureState;
      List<StructureStart> out = null;
      Sampler climate = null;

      for (Holder<StructureSet> set : state.possibleStructureSets()) {
         List<StructureSelectionEntry> entries = ((StructureSet)set.value()).structures();
         boolean visible = false;

         for (StructureSelectionEntry entry : entries) {
            visible |= isVisibleStep(((Structure)entry.structure().value()).step());
         }

         if (visible) {
            StructurePlacement placement = ((StructureSet)set.value()).placement();
            if (placement.isStructureChunk(state, cx, cz)) {
               if (climate == null) {
                  climate = state.randomState().createClimateSampler(SamplerContext.builder().enableCaches().build());
               }

               StructureStart start = this.chooseStart(state, entries, cx, cz, climate);
               if (start != null) {
                  if (out == null) {
                     out = new ArrayList<>(2);
                  }

                  out.add(start);
               }
            }
         }
      }

      return out == null ? List.of() : out;
   }

   private @Nullable StructureStart chooseStart(ChunkGeneratorStructureState state, List<StructureSelectionEntry> entries, int cx, int cz, Sampler climate) {
      if (entries.size() == 1) {
         return this.tryStart(state, entries.getFirst(), cx, cz, climate);
      }

      ArrayList<StructureSelectionEntry> options = new ArrayList<>(entries);
      WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
      random.setLargeFeatureSeed(state.getLevelSeed(), cx, cz);
      int total = 0;

      for (StructureSelectionEntry option : options) {
         total += option.weight();
      }

      while (!options.isEmpty() && total > 0) {
         int choice = random.nextInt(total);
         int index = 0;
         Iterator selected = options.iterator();

         while (true) {
            if (selected.hasNext()) {
               StructureSelectionEntry option = (StructureSelectionEntry)selected.next();
               choice -= option.weight();
               if (choice >= 0) {
                  index++;
                  continue;
               }
            }

            index = Math.min(index, options.size() - 1);
            StructureSelectionEntry selectedx = options.get(index);
            StructureStart start = this.tryStart(state, selectedx, cx, cz, climate);
            if (start != null) {
               return start;
            }

            options.remove(index);
            total -= selectedx.weight();
            break;
         }
      }

      return null;
   }

   private @Nullable StructureStart tryStart(ChunkGeneratorStructureState state, StructureSelectionEntry entry, int cx, int cz, Sampler climate) {
      Structure structure = (Structure)entry.structure().value();
      if (!isVisibleStep(structure.step())) {
         return null;
      }

      StructureStart start = locked(
         structure,
         () -> structure.generate(
            entry.structure(),
            this.world.dimension,
            this.world.registries,
            this.generator,
            this.generator.getBiomeSource(),
            climate,
            state.randomState(),
            this.world.templates,
            state.getLevelSeed(),
            new ChunkPos(cx, cz),
            0,
            this.world.level,
            structure.biomes()::contains
         )
      );
      if (start.isValid() && !start.getPieces().isEmpty()) {
         START_SHARED.putIfAbsent(structure.getClass(), sharedState(start));
      }

      return start.isValid() ? start : null;
   }

   private LodTile bake(LodGenLevel gen, LodTile tile, int originX, int originZ, int step, boolean[] touched) {
      Long2IntOpenHashMap columnTop = new Long2IntOpenHashMap();
      Long2IntOpenHashMap columnBottom = new Long2IntOpenHashMap();
      columnTop.defaultReturnValue(Integer.MIN_VALUE);
      columnBottom.defaultReturnValue(Integer.MAX_VALUE);
      ObjectIterator chosen = gen.placed.long2ObjectEntrySet().iterator();

      while (chosen.hasNext()) {
         Entry<BlockState> entry = (Entry<BlockState>)chosen.next();
         long key = entry.getLongKey();
         int x = BlockPos.getX(key);
         int y = BlockPos.getY(key);
         int z = BlockPos.getZ(key);
         long column = ChunkPos.pack(x, z);
         if (BlockShapes.stored((BlockState)entry.getValue()) != -1) {
            columnTop.put(column, Math.max(columnTop.get(column), y));
         }

         columnBottom.put(column, Math.min(columnBottom.get(column), y));
      }

      Long2LongOpenHashMap chosenx = new Long2LongOpenHashMap();
      chosenx.defaultReturnValue(Long.MIN_VALUE);
      Long2IntOpenHashMap chosenTop = new Long2IntOpenHashMap();
      chosenTop.defaultReturnValue(Integer.MIN_VALUE);
      long[] columns = columnBottom.keySet().toLongArray();
      Arrays.sort(columns);

      for (long column : columns) {
         int x = ChunkPos.getX(column);
         int z = ChunkPos.getZ(column);
         int cx = Math.floorDiv(x - originX, step);
         int cz = Math.floorDiv(z - originZ, step);
         if (cx >= -1 && cz >= -1 && cx <= 64 && cz <= 64) {
            long cell = ChunkPos.pack(cx, cz);
            boolean empty = tile.count(cx, cz) == 0;
            if (!empty || columnTop.get(column) != Integer.MIN_VALUE) {
               int t = empty ? columnTop.get(column) : Math.max(columnTop.get(column), tile.topY(cx, cz) - 1);
               int known = chosenTop.get(cell);
               boolean centre = x - originX - cx * step == step / 2 && z - originZ - cz * step == step / 2;
               if (chosenx.get(cell) == Long.MIN_VALUE || step == 1 || centre && t >= known - 2 || t > known + 3) {
                  chosenx.put(cell, column);
                  chosenTop.put(cell, t);
               }
            }
         }
      }

      TileBuilder builder = TileBuilder.borrow();
      builder.copyFrom(tile);
      BlockState[] window = this.windows.take();

      try {
         return this.fill(gen, tile, chosenx, columnTop, columnBottom, builder, window, touched);
      } finally {
         this.windows.give(window);
      }
   }

   private LodTile fill(
      LodGenLevel gen,
      LodTile tile,
      Long2LongOpenHashMap chosen,
      Long2IntOpenHashMap columnTop,
      Long2IntOpenHashMap columnBottom,
      TileBuilder builder,
      BlockState[] window,
      boolean[] touched
   ) {
      ObjectIterator var9 = chosen.long2LongEntrySet().iterator();

      while (var9.hasNext()) {
         it.unimi.dsi.fastutil.longs.Long2LongMap.Entry entry = (it.unimi.dsi.fastutil.longs.Long2LongMap.Entry)var9.next();
         int cx = ChunkPos.getX(entry.getLongKey());
         int cz = ChunkPos.getZ(entry.getLongKey());
         long column = entry.getLongValue();
         int x = ChunkPos.getX(column);
         int z = ChunkPos.getZ(column);
         int placedTop = columnTop.get(column);
         int placedBottom = columnBottom.get(column);
         int baseTop = tile.topY(cx, cz);
         if (baseTop != Integer.MIN_VALUE || placedTop != Integer.MIN_VALUE) {
            int high = Math.min(this.world.level.getMaxY(), baseTop == Integer.MIN_VALUE ? placedTop : Math.max(placedTop, baseTop - 1));
            int low = Math.max(high - 96 + 1, baseTop == Integer.MIN_VALUE ? placedBottom : Math.min(baseTop - 1, placedBottom));
            int biome = tile.count(cx, cz) > 0 ? Span.biome(tile.spans[tile.first(cx, cz)]) : nearBiome(tile, cx, cz);
            MutableBlockPos pos = new MutableBlockPos();
            boolean solid = false;

            for (int y = low; y <= high; y++) {
               BlockState placed = (BlockState)gen.placed.get(pos.set(x, y, z).asLong());
               window[y - low] = placed != null ? placed : gen.baseState(x, y, z);
               if (placed != null && !placed.isAir() && !FeatureStamper.loose(Block.getId(placed))) {
                  solid = true;
               }
            }

            builder.begin();

            for (int y = high; y >= low; y--) {
               int id = BlockShapes.stored(window[y - low]);
               if (id != -1) {
                  BlockState state = Block.stateById(id);
                  builder.add(Span.pack(y + 1, y, id, biome, skyAt(tile, cx, cz, y), state.getLightEmission()));
               }
            }

            int from = tile.first(cx, cz);
            int count = tile.count(cx, cz);

            for (int i = 0; i < count; i++) {
               long span = tile.spans[from + i];
               if (Span.bottom(span) < low) {
                  if (Span.top(span) > low) {
                     span = Span.withTop(span, low);
                  }

                  builder.add(span);
               }
            }

            builder.end(cx, cz);
            if (solid) {
               touched[LodTile.cell(cx, cz)] = true;
            }
         }
      }

      return builder.build(tile.level, tile.tx, tile.tz, tile.realMask, tile.complete);
   }

   public interface FeatureCapture {
      void placed(PlacedFeature var1, int var2, LongArrayList var3, LodGenLevel var4);
   }

   public interface FeatureFilter {
      boolean test(PlacedFeature var1, Holder<Biome> var2, int var3, int var4);
   }

   private record FeatureInfo(boolean freeze, boolean underground, int maxY, boolean rare) {
   }

   private final class Pass {
      final LodGenLevel gen;
      final BiomeManager biomeManager;
      final Vegetation.FeatureFilter filter;
      final Vegetation.@Nullable FeatureCapture capture;
      final boolean structures;
      final Long2ObjectOpenHashMap<StructureStart[]> loaded = new Long2ObjectOpenHashMap();
      final Long2ObjectOpenHashMap<List<StructureStart>> regenerated = new Long2ObjectOpenHashMap();
      final Map<PlacedFeature, LongOpenHashSet> realChunks = new IdentityHashMap<>();
      final List<StepFeatureData> stepData = Vegetation.this.steps.get();
      final FeaturePlacer placer;
      final WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(RandomSupport.generateUniqueSeed()));
      final Set<Holder<Biome>> biomes = new ObjectArraySet();
      final Set<Holder<Biome>> inside = new ObjectArraySet();
      final MutableBlockPos probe = new MutableBlockPos();
      final LongArrayList near = new LongArrayList();
      @Nullable LongArrayList candidates;
      boolean bounded;
      int minCx;
      int minCz;
      int maxCx;
      int maxCz;
      int level = -1;

      void area(int minCx, int minCz, int maxCx, int maxCz) {
         this.bounded = true;
         this.minCx = minCx;
         this.minCz = minCz;
         this.maxCx = maxCx;
         this.maxCz = maxCz;
      }

      Pass(
         final LodGenLevel gen,
         final BiomeManager biomeManager,
         final Vegetation.FeatureFilter filter,
         final Vegetation.@Nullable FeatureCapture capture,
         final boolean structures
      ) {
         this.gen = gen;
         this.biomeManager = biomeManager;
         this.filter = filter;
         this.capture = capture;
         this.structures = structures;
         this.placer = new FeaturePlacer(gen, Vegetation.this.generator);
      }
   }

   public record RealResult(LodTile tile, boolean @Nullable [] touched, Map<PlacedFeature, LongOpenHashSet> realChunks) {
   }

   private static final class StartRecipe {
      final BoundingBox box;
      private @Nullable StructureStart origin;
      private @Nullable CompoundTag tag;

      StartRecipe(StructureStart origin) {
         this.origin = origin;
         this.box = origin.getBoundingBox();
      }

      synchronized @Nullable CompoundTag tag(StructurePieceSerializationContext context) {
         StructureStart start = this.origin;
         if (start != null) {
            this.origin = null;

            try {
               this.tag = start.createTag(context, start.getChunkPos());
            } catch (Throwable e) {
               Vegetation.rethrowFatal(e);
               this.tag = null;
            }
         }

         return this.tag;
      }
   }
}
