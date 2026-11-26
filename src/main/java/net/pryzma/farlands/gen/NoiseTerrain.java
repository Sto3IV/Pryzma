package net.pryzma.farlands.gen;

import net.pryzma.farlands.Farlands;
import net.pryzma.farlands.lod.LodTile;
import net.pryzma.farlands.lod.Span;
import net.pryzma.farlands.lod.TileBuilder;
import net.pryzma.farlands.work.Slots;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Interval;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.Climate.ParameterList;
import net.minecraft.world.level.biome.Climate.Sampler;
import net.minecraft.world.level.biome.Climate.TargetPoint;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.densityfunction.DensityBufferPool;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySamplerSet;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunctions.HolderHolder;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler.Bound;
import net.minecraft.world.level.levelgen.densityfunction.op.InterpolatedFunction;
import net.minecraft.world.level.levelgen.material.MaterialRuleContext;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import net.minecraft.world.level.levelgen.material.rule.RuleEvaluator;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.jspecify.annotations.Nullable;

public final class NoiseTerrain implements TerrainSource {
   private static final int N = 66;
   private static final int GROUP = 6;
   private static final int EXACT_GROUP = 11;
   private static final int EXACT_SPAN = 16;
   private static final int EXACT_MIN = 4;
   private static final int TOPDOWN_LEVELS = 5;
   private static final int TOPDOWN_FIRST = 3;
   private static final int EXACT_CHUNK = 32;
   private static final int EXACT_FIRST = 16;
   private static final int SHALLOW_DEPTH = 2;
   private static final int VEIN_VOLUME_HEIGHT = 48;
   private static final int MATERIAL_DEPTH = 8;
   private static final int COARSE_MATERIAL_DEPTH = 5;
   private static final int GROW = 24;
   private static final int GROW_ATTEMPTS = 48;
   private static final int SEARCH_MARGIN = 8;
   private static final Map<BlockState, Boolean> SNOW_SUPPORT = new ConcurrentHashMap<>();
   private static final int STONE_BELOW = 64;
   private static final int MAX_RUNS = 8;
   private static final int RUN_GROUP = 8;
   private static final int RUN_STEP_MAX = 16;
   private static final int SHAPE_DEPTH = 0;
   private static final int CARVE_LEVEL = 2;
   private final RandomState randomState;
   private final BiomeSource biomeSource;
   private final DensityFunction exactDensity;
   private final DensityFunction cornerDensity;
   private final DensityFunction coarseDensity;
   private final DensityFunction carvedDensity;
   private final DensityFunction exactCached;
   private final DensityFunction shapeDensity;
   private final @Nullable Carving carving;
   private final DensityFunction surfaceEstimate;
   private final boolean surfaceKnown;
   private final int cellXz;
   private final int cellY;
   private final int minY;
   private final int maxY;
   private final int seaLevel;
   private final BlockState defaultBlock;
   private final BlockState defaultFluid;
   private final MaterialRule materialRule;
   private final WorldGenerationContext generationContext;
   private final Set<Holder<Biome>> possibleBiomes;
   private final long zoomSeed;
   private final BiomeIds biomeIds;
   private final @Nullable Vegetation vegetation;
   private final @Nullable ServerBiomes serverBiomes;
   private final LevelHeightAccessor heights;
   private final boolean climateColumns;
   private final @Nullable DensityFunction minSurfaceInput;
   private final int minSurfaceCell;
   private static final int CLIMATE_BITS = 12;
   private static final int CLIMATE_SLOTS = 4096;
   private final Slots<NoiseTerrain.Worker> workers = new Slots<>(() -> new NoiseTerrain.Worker());
   private final @Nullable FeatureCatalog catalog;
   private final @Nullable ThreadLocal<Object> biomeTies;
   private static final int TIE_SHIFT = 10;
   private static final Object NO_LEAF = new Object();
   private final @Nullable FeatureStamper stamper;
   private final NoiseTerrain.@Nullable BiomeTable sharedBiomes;
   private static final AtomicInteger DECORATION_FAILURES = new AtomicInteger();
   private static final int COVER_HEIGHT = 2;
   private static final int COVER_MERGE = 6;

   private static long[] filled(long[] keys) {
      Arrays.fill(keys, Long.MIN_VALUE);
      return keys;
   }

   private static boolean flat(DensityFunction function) {
      return (function.domainAxes() & 2) == 0;
   }

   public NoiseTerrain(GenWorld world, NoiseBasedChunkGenerator generator, BiomeIds biomeIds, @Nullable Vegetation vegetation) {
      NoiseGeneratorSettings settings = (NoiseGeneratorSettings)generator.generatorSettings().value();
      this.randomState = world.randomState;
      this.biomeSource = generator.getBiomeSource();
      NoiseSettings noise = settings.noiseSettings().clampToHeightAccessor(world.level);
      this.minY = noise.minY();
      this.maxY = noise.minY() + noise.height();
      this.seaLevel = settings.seaLevel();
      this.defaultBlock = settings.defaultBlock();
      this.defaultFluid = settings.defaultFluid();
      this.materialRule = (MaterialRule)settings.materialRule().value();
      this.exactDensity = EffectiveDensity.of(generator, world, settings.noiseRouter().finalDensity());
      StripInterpolation strip = new StripInterpolation();
      this.cornerDensity = StripCaves.terrainShape(strip.rewrite(this.exactDensity));
      this.carvedDensity = new StripInterpolation().rewrite(StripCaves.outsideCells(this.exactDensity));
      this.exactCached = CornerCache.wrap(this.exactDensity);
      this.coarseDensity = StripCaves.terrainShape(this.exactDensity);
      this.shapeDensity = StripCaves.terrainShape(new StripInterpolation().rewrite(this.exactDensity));
      this.cellXz = strip.found && Integer.bitCount(strip.cellXz) == 1 ? strip.cellXz : 4;
      this.cellY = strip.found && Integer.bitCount(strip.cellY) == 1 ? strip.cellY : 8;
      this.surfaceEstimate = new StripInterpolation().rewrite(settings.noiseRouter().chunkSurfaceLevel());
      Interval surfaceRange = this.surfaceEstimate.range();
      this.surfaceKnown = !surfaceRange.isNaI() && surfaceRange.max() > surfaceRange.min();
      this.generationContext = new WorldGenerationContext(generator, world.level);
      this.possibleBiomes = this.biomeSource.possibleBiomes();
      Carving carving = new Carving(generator, this.generationContext, world.seed, this.possibleBiomes);
      this.carving = carving.active() ? carving : null;
      this.biomeTies = tieMemory(this.biomeSource);
      NoiseRouter router = settings.noiseRouter();
      this.climateColumns = this.biomeSource instanceof MultiNoiseBiomeSource
         && flat(router.temperature())
         && flat(router.vegetation())
         && flat(router.continents())
         && flat(router.erosion())
         && flat(router.ridges());
      DensityFunction surfaceLevel = this.randomState.surfaceSystem().preliminarySurfaceFunction();

      while (surfaceLevel instanceof HolderHolder) {
         HolderHolder holder = (HolderHolder)surfaceLevel;
         surfaceLevel = (DensityFunction)holder.function().value();
      }

      if (MaterialAccess.PRESET && surfaceLevel instanceof InterpolatedFunction interpolated && interpolated.cellSizeXz() > 0 && interpolated.cellSizeY() > 0) {
         this.minSurfaceInput = interpolated.input();
         this.minSurfaceCell = interpolated.cellSizeXz();
      } else {
         this.minSurfaceInput = null;
         this.minSurfaceCell = 0;
      }

      this.zoomSeed = BiomeManager.obfuscateSeed(world.seed);
      this.biomeIds = biomeIds;
      this.vegetation = vegetation;
      this.serverBiomes = world.serverBiomes;
      this.sharedBiomes = this.serverBiomes == null ? new NoiseTerrain.BiomeTable(this.possibleBiomes) : null;
      this.heights = world.level;
      this.catalog = vegetation == null ? null : new FeatureCatalog(this::probe, this::survey);
      this.stamper = this.catalog == null ? null : new FeatureStamper(this.catalog, world.seed);
   }

   public int[] catalogBiomes(int surveyTiles) {
      FeatureCatalog catalog = this.catalog;
      return catalog == null ? new int[0] : catalog.surveyedBiomes(surveyTiles);
   }

   public boolean catalogReady(int biome) {
      FeatureCatalog catalog = this.catalog;
      return catalog == null || catalog.ready(biome);
   }

   public void buildCatalog(int biome) {
      FeatureCatalog catalog = this.catalog;
      if (catalog != null) {
         catalog.kinds(biome, 0, 0);
      }
   }

   @Override
   public @Nullable LodTile generate(int level, int tx, int tz) {
      return this.generate(level, tx, tz, false);
   }

   @Override
   public @Nullable LodTile generate(int level, int tx, int tz, boolean preview) {
      NoiseTerrain.Worker w = this.workers.take();

      try {
         return this.generate(w, level, tx, tz, preview);
      } finally {
         if (this.serverBiomes != null) {
            w.biomeCache.clear();
         }

         w.pool.garbageCollect();
         this.workers.give(w);
      }
   }

   @Override
   public void trim() {
      this.workers.clear();
   }

   @Override
   public void close() {
      this.workers.clear();
   }

   private LodTile generate(NoiseTerrain.Worker w, int level, int tx, int tz, boolean preview) {
      if (this.serverBiomes != null) {
         w.biomeCache.clear();
      }

      CompletableFuture<BiomeGrid> pending = this.serverBiomes == null ? null : this.serverBiomes.request(level, tx, tz);
      int step = 1 << level;
      int x0 = tx * 64 * step - step;
      int z0 = tz * 64 * step - step;
      this.sampleTerrain(w, x0, z0, step);
      this.carveColumns(w, x0, z0, step, level);
      w.grid = await(pending);
      boolean biomesLate = pending != null && !pending.isDone();
      if (this.serverBiomes != null) {
         w.biomeCache.clear();
      }

      try {
         w.shallow = preview;
         boolean degraded = this.buildColumns(w, x0, z0, step, level, true);
         LodTile base = w.builder.build(level, tx, tz, 0L, !biomesLate && !degraded && !w.shallow);
         Vegetation vegetation = this.vegetation;
         FeatureCatalog catalog = this.catalog;
         FeatureStamper stamper = this.stamper;
         if (vegetation != null && catalog != null && stamper != null) {
            try {
               return this.decorate(w, base, x0, z0, step, level, vegetation, catalog, stamper, preview);
            } catch (Throwable e) {
               Vegetation.rethrowFatal(e);
               int failed = DECORATION_FAILURES.incrementAndGet();
               if (failed <= 3) {
                  Farlands.LOG.warn("Distant decoration failed for tile L{} {} {}, keeping bare terrain", new Object[]{level, tx, tz, e});
               } else {
                  Farlands.LOG.debug("Distant decoration failed for tile L{} {} {}", new Object[]{level, tx, tz, e});
               }

               return base.complete ? new LodTile(base.level, base.tx, base.tz, base.offsets, base.spans, base.realMask, false) : base;
            }
         } else {
            return base;
         }
      } finally {
         w.grid = null;
      }
   }

   private LodTile decorate(
      NoiseTerrain.Worker w,
      LodTile base,
      int x0,
      int z0,
      int step,
      int level,
      Vegetation vegetation,
      FeatureCatalog catalog,
      FeatureStamper stamper,
      boolean preview
   ) {
      LodTile tile = base;
      if (preview && level <= 2) {
         LodTile stamped = stamper.stamp(tile, null, null);
         return stamped.complete ? new LodTile(stamped.level, stamped.tx, stamped.tz, stamped.offsets, stamped.spans, stamped.realMask, false) : stamped;
      }

      boolean[] touched = null;
      Map<PlacedFeature, LongOpenHashSet> realChunks = null;
      if (level <= 2) {
         Set<PlacedFeature> near = catalog.observedIn(stamper.learn(tile));
         BiomeIds ids = this.biomeIds;
         Vegetation.FeatureFilter filter = level < 2
            ? (feature, biome, blockX, blockZ) -> catalog.realOnly(ids.id(biome), feature, blockX, blockZ)
            : (feature, biome, blockX, blockZ) -> catalog.observedRare(ids.id(biome), feature, blockX, blockZ, near)
               || vegetation.rare(feature) && catalog.realOnly(ids.id(biome), feature, blockX, blockZ);
         Vegetation.RealResult real = vegetation.decorateReal(tile, x0, z0, step, w.biomeManager, filter);
         tile = real.tile();
         touched = real.touched();
         realChunks = real.realChunks();
      }

      return stamper.stamp(tile, touched, realChunks);
   }

   private void sampleTerrain(NoiseTerrain.Worker w, int x0, int z0, int step) {
      this.sampleTerrain(w, x0, z0, step, true);
   }

   private void sampleTerrain(NoiseTerrain.Worker w, int x0, int z0, int step, boolean runs) {
      this.minSurface(w, x0, z0, step);
      this.estimate(w, x0, z0, step);
      if (step < this.cellXz) {
         w.cornerCache.begin(x0, z0, x0 + 66 * step, z0 + 66 * step);

         try {
            this.sampleExact(w, x0, z0, step);
         } finally {
            w.cornerCache.end();
         }
      } else {
         this.sampleCorners(w, x0, z0, step);
         this.carve(w, x0, z0, step);
      }

      if (runs) {
         this.scanRuns(w, x0, z0, step);
      } else {
         Arrays.fill(w.bottom, this.minY);
         Arrays.fill(w.runCount, 0);
      }

      for (int i = 0; i < 4356; i++) {
         int t = w.top[i];
         w.fluidTop[i] = t != Integer.MIN_VALUE && t < this.seaLevel && !this.defaultFluid.isAir() ? this.seaLevel : Integer.MIN_VALUE;
      }
   }

   private static @Nullable ThreadLocal<Object> tieMemory(BiomeSource source) {
      if (!(source instanceof MultiNoiseBiomeSource)) {
         return null;
      }

      try {
         Method parameters = MultiNoiseBiomeSource.class.getDeclaredMethod("parameters");
         parameters.setAccessible(true);
         Object list = parameters.invoke(source);
         Field index = ParameterList.class.getDeclaredField("index");
         index.setAccessible(true);
         Object tree = index.get(list);
         Field last = tree.getClass().getDeclaredField("lastResult");
         last.setAccessible(true);
         return (ThreadLocal<Object>)(last.get(tree) instanceof ThreadLocal<?> local ? local : null);
      } catch (ReflectiveOperationException | RuntimeException e) {
         Farlands.LOG.debug("Biome search memory not reachable", e);
         return null;
      }
   }

   private void survey(int level, int tx, int tz, int[] biomes) {
      NoiseTerrain.Worker w = this.workers.take();

      try {
         int step = 1 << level;
         int x0 = tx * 64 * step - step;
         int z0 = tz * 64 * step - step;
         this.sampleTerrain(w, x0, z0, step, false);
         this.carveColumns(w, x0, z0, step, Integer.MAX_VALUE);
         w.shallow = true;
         this.buildColumns(w, x0, z0, step, level, false);
         w.shallow = false;
         LodTile base = w.builder.build(level, tx, tz, 0L, false);

         for (int z = 0; z < 64; z++) {
            for (int x = 0; x < 64; x++) {
               biomes[z * 64 + x] = base.count(x, z) == 0 ? -1 : Span.biome(base.spans[base.first(x, z)]);
            }
         }
      } finally {
         if (this.serverBiomes != null) {
            w.biomeCache.clear();
         }

         w.pool.garbageCollect();
         this.workers.give(w);
      }
   }

   private void probe(int blockX, int blockZ, FeatureCatalog.Sink sink) {
      Vegetation vegetation = this.vegetation;
      if (vegetation != null) {
         NoiseTerrain.Worker w = this.workers.take();

         try {
            int tx = Math.floorDiv(blockX, 64);
            int tz = Math.floorDiv(blockZ, 64);
            int x0 = tx * 64 - 1;
            int z0 = tz * 64 - 1;
            this.sampleTerrain(w, x0, z0, 1);
            this.carveColumns(w, x0, z0, 1, 0);
            w.shallow = false;
            this.buildColumns(w, x0, z0, 1, 0, false);
            LodTile base = w.builder.build(0, tx, tz, 0L, true);
            Int2IntOpenHashMap dry = new Int2IntOpenHashMap();
            Int2IntOpenHashMap wet = new Int2IntOpenHashMap();

            for (int z = 0; z < 64; z++) {
               for (int x = 0; x < 64; x++) {
                  if (base.count(x, z) != 0) {
                     long top = base.spans[base.first(x, z)];
                     (Block.stateById(Span.state(top)).getFluidState().isEmpty() ? dry : wet).addTo(Span.biome(top), 1);
                  }
               }
            }

            IntOpenHashSet seen = new IntOpenHashSet(dry.keySet());
            seen.addAll(wet.keySet());
            IntIterator var21 = seen.iterator();

            while (var21.hasNext()) {
               int biome = (Integer)var21.next();
               sink.columns(biome, dry.get(biome), wet.get(biome));
            }

            vegetation.probe(base, x0, z0, w.biomeManager, (feature, kind, positions, gen) -> this.capture(base, feature, kind, positions, gen, sink));
         } finally {
            if (this.serverBiomes != null) {
               w.biomeCache.clear();
            }

            w.pool.garbageCollect();
            this.workers.give(w);
         }
      }
   }

   private void capture(LodTile base, PlacedFeature feature, int kind, LongArrayList positions, LodGenLevel gen, FeatureCatalog.Sink sink) {
      LongOpenHashSet open = new LongOpenHashSet(positions);
      List<LongArrayList> clusters = new ArrayList<>();
      LongArrayList queue = new LongArrayList();

      for (int p = 0; p < positions.size() && clusters.size() < 256; p++) {
         long seedKey = positions.getLong(p);
         if (open.remove(seedKey)) {
            LongArrayList cluster = new LongArrayList();
            queue.clear();
            queue.add(seedKey);

            while (!queue.isEmpty() && cluster.size() < 8192) {
               long key = queue.removeLong(queue.size() - 1);
               cluster.add(key);
               int x = BlockPos.getX(key);
               int y = BlockPos.getY(key);
               int z = BlockPos.getZ(key);

               for (int dy = -1; dy <= 1; dy++) {
                  for (int dz = -1; dz <= 1; dz++) {
                     for (int dx = -1; dx <= 1; dx++) {
                        long next = BlockPos.asLong(x + dx, y + dy, z + dz);
                        if (open.remove(next)) {
                           queue.add(next);
                        }
                     }
                  }
               }
            }

            clusters.add(cluster);
         }
      }

      int count = clusters.size();
      boolean[] cover = new boolean[count];
      long[] lowestOf = new long[count];
      int[][] box = new int[count][];

      for (int c = 0; c < count; c++) {
         LongArrayList cluster = clusters.get(c);
         int minX = Integer.MAX_VALUE;
         int minZ = Integer.MAX_VALUE;
         int maxX = Integer.MIN_VALUE;
         int maxZ = Integer.MIN_VALUE;
         int minY = Integer.MAX_VALUE;
         int maxY = Integer.MIN_VALUE;
         boolean loose = true;
         long lowest = cluster.getLong(0);

         for (int i = 0; i < cluster.size(); i++) {
            long key = cluster.getLong(i);
            minX = Math.min(minX, BlockPos.getX(key));
            maxX = Math.max(maxX, BlockPos.getX(key));
            minZ = Math.min(minZ, BlockPos.getZ(key));
            maxZ = Math.max(maxZ, BlockPos.getZ(key));
            minY = Math.min(minY, BlockPos.getY(key));
            maxY = Math.max(maxY, BlockPos.getY(key));
            if (BlockPos.getY(key) < BlockPos.getY(lowest)) {
               lowest = key;
            }

            BlockState state = (BlockState)gen.placed.get(key);
            if (loose && state != null && !state.isAir() && !FeatureStamper.loose(Block.getId(state))) {
               loose = false;
            }
         }

         cover[c] = loose && maxY - minY < 2;
         lowestOf[c] = lowest;
         box[c] = new int[]{minX, minZ, maxX, maxZ};
      }

      boolean[] merged = new boolean[count];

      for (int c = 0; c < count; c++) {
         if (cover[c]) {
            int best = -1;
            int bestGap = 7;

            for (int o = 0; o < count; o++) {
               if (!cover[o]) {
                  int gap = Math.max(Math.max(box[o][0] - box[c][2], box[c][0] - box[o][2]), Math.max(box[o][1] - box[c][3], box[c][1] - box[o][3]));
                  if (gap < bestGap) {
                     bestGap = gap;
                     best = o;
                  }
               }
            }

            if (best >= 0) {
               clusters.get(best).addAll((LongList)clusters.get(c));
               merged[c] = true;
            }
         }
      }

      for (int c = 0; c < count; c++) {
         if (!merged[c]) {
            LongArrayList cluster = clusters.get(c);
            long lowest = lowestOf[c];
            int ax = BlockPos.getX(lowest);
            int az = BlockPos.getZ(lowest);
            int cx = ax - base.originX();
            int cz = az - base.originZ();
            if (cx >= -1 && cz >= -1 && cx <= 64 && cz <= 64) {
               int from = base.first(cx, cz);
               int ground = Integer.MIN_VALUE;
               int fluid = Integer.MIN_VALUE;
               Block soil = Blocks.AIR;

               for (int i = 0; i < base.count(cx, cz); i++) {
                  long span = base.spans[from + i];
                  BlockState state = Block.stateById(Span.state(span));
                  if (state.getFluidState().isEmpty()) {
                     ground = Span.top(span);
                     soil = state.getBlock();
                     break;
                  }

                  if (fluid == Integer.MIN_VALUE) {
                     fluid = Span.top(span);
                  }
               }

               int lowY = BlockPos.getY(lowest);
               int baseY;
               int anchor;
               if (ground == Integer.MIN_VALUE) {
                  anchor = 2;
                  baseY = 0;
               } else {
                  if (lowY > ground + 2 && (fluid == Integer.MIN_VALUE || lowY > fluid + 1)) {
                     continue;
                  }

                  if (fluid != Integer.MIN_VALUE && lowY >= fluid - 1) {
                     anchor = 1;
                     baseY = fluid;
                  } else {
                     anchor = 0;
                     baseY = ground;
                  }
               }

               FeatureTemplate template = FeatureTemplate.capture(cluster, gen, ax, az, baseY, anchor == 0 && buried(base, cluster));
               if (template != null) {
                  sink.instance(
                     feature,
                     kind,
                     base.count(cx, cz) > 0 ? Span.biome(base.spans[from]) : Vegetation.nearBiome(base, cx, cz),
                     anchor,
                     soil,
                     fluid != Integer.MIN_VALUE && ground != Integer.MIN_VALUE && fluid > ground,
                     template
                  );
               }
            }
         }
      }
   }

   private static boolean buried(LodTile base, LongArrayList cluster) {
      int inside = 0;

      for (int i = 0; i < cluster.size(); i++) {
         long key = cluster.getLong(i);
         int cx = BlockPos.getX(key) - base.originX();
         int cz = BlockPos.getZ(key) - base.originZ();
         if (cx >= -1 && cz >= -1 && cx <= 64 && cz <= 64 && base.count(cx, cz) != 0) {
            int from = base.first(cx, cz);

            for (int k = 0; k < base.count(cx, cz); k++) {
               long span = base.spans[from + k];
               if (Block.stateById(Span.state(span)).getFluidState().isEmpty()) {
                  if (BlockPos.getY(key) < Span.top(span)) {
                     inside++;
                  }
                  break;
               }
            }
         }
      }

      return inside * 4 >= cluster.size() * 3;
   }

   private static @Nullable BiomeGrid await(@Nullable CompletableFuture<@Nullable BiomeGrid> pending) {
      if (pending == null) {
         return null;
      }

      try {
         return pending.get(5L, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         return null;
      } catch (ExecutionException | TimeoutException e) {
         return null;
      }
   }

   private void minSurface(NoiseTerrain.Worker w, int x0, int z0, int step) {
      w.minSurfaceReady = false;
      Bound sampler = w.minSurfaceLattice;
      if (sampler != null) {
         int cell = this.minSurfaceCell;
         if (step % cell == 0 && Math.floorMod(x0, cell) == 0 && Math.floorMod(z0, cell) == 0) {
            DensityVolume grid = new DensityVolume(66, 1, 66, x0, 0, z0, step, 1, step);
            ScopedDensityBuffer buffer = sampler.sampleVolume(grid);

            try {
               for (int z = 0; z < 66; z++) {
                  for (int x = 0; x < 66; x++) {
                     w.minSurface[z * 66 + x] = buffer.get(grid.indexUnchecked(x, 0, z));
                  }
               }
            } catch (Throwable var28) {
               if (buffer != null) {
                  try {
                     buffer.close();
                  } catch (Throwable var27) {
                     var28.addSuppressed(var27);
                  }
               }

               throw var28;
            }

            if (buffer != null) {
               buffer.close();
            }

            w.minSurfaceReady = true;
         } else {
            int last = 65 * step;
            int lx0 = Math.floorDiv(x0, cell) * cell;
            int lz0 = Math.floorDiv(z0, cell) * cell;
            int nx = (x0 + last - lx0) / cell + 2;
            int nz = (z0 + last - lz0) / cell + 2;
            DensityVolume volume = new DensityVolume(nx, 1, nz, lx0, 0, lz0, cell, 1, cell);
            ScopedDensityBuffer buffer = sampler.sampleVolume(volume);

            try {
               for (int z = 0; z < 66; z++) {
                  int rz = z0 + z * step - lz0;
                  int iz = rz / cell;
                  int zIn = rz - iz * cell;

                  for (int x = 0; x < 66; x++) {
                     int rx = x0 + x * step - lx0;
                     int ix = rx / cell;
                     int xIn = rx - ix * cell;
                     float v00 = buffer.get(volume.indexUnchecked(ix, 0, iz));
                     if (xIn == 0 && zIn == 0) {
                        w.minSurface[z * 66 + x] = v00;
                     } else {
                        float v10 = buffer.get(volume.indexUnchecked(ix + 1, 0, iz));
                        float v01 = buffer.get(volume.indexUnchecked(ix, 0, iz + 1));
                        float v11 = buffer.get(volume.indexUnchecked(ix + 1, 0, iz + 1));
                        w.minSurface[z * 66 + x] = Mth.lerp3((float)xIn / cell, 0.0F, (float)zIn / cell, v00, v10, v00, v10, v01, v11, v01, v11);
                     }
                  }
               }
            } catch (Throwable var29) {
               if (buffer != null) {
                  try {
                     buffer.close();
                  } catch (Throwable var26) {
                     var29.addSuppressed(var26);
                  }
               }

               throw var29;
            }

            if (buffer != null) {
               buffer.close();
            }

            w.minSurfaceReady = true;
         }
      }
   }

   private void estimate(NoiseTerrain.Worker w, int x0, int z0, int step) {
      if (step < this.cellXz) {
         this.estimateLattice(w, x0, z0, step);
      } else {
         DensityVolume volume = new DensityVolume(66, 1, 66, x0, 0, z0, step, 1, step);
         ScopedDensityBuffer buffer = w.estimate.sampleVolume(volume);

         try {
            for (int z = 0; z < 66; z++) {
               for (int x = 0; x < 66; x++) {
                  w.estimates[z * 66 + x] = buffer.get(volume.indexUnchecked(x, 0, z));
               }
            }
         } catch (Throwable var10) {
            if (buffer != null) {
               try {
                  buffer.close();
               } catch (Throwable var9) {
                  var10.addSuppressed(var9);
               }
            }

            throw var10;
         }

         if (buffer != null) {
            buffer.close();
         }
      }
   }

   private int fixedGroups(int[] starts) {
      int count = 0;

      for (int g = 0; g < 66; g += 11) {
         starts[count++] = g;
      }

      starts[count] = 66;
      return count;
   }

   private int exactGroups(int origin, int step, int[] starts) {
      int span = 16 * step;
      if (span % this.cellXz != 0) {
         return this.fixedGroups(starts);
      }

      int count = 0;
      starts[count++] = 0;

      for (int i = 1; i < 66; i++) {
         if (Math.floorMod(origin + i * step, span) == 0) {
            starts[count++] = i;
         }
      }

      if (count > 1 && starts[1] < 4) {
         System.arraycopy(starts, 2, starts, 1, count - 2);
         count--;
      }

      if (count > 1 && 66 - starts[count - 1] < 4) {
         count--;
      }

      starts[count] = 66;
      return count;
   }

   private void estimateLattice(NoiseTerrain.Worker w, int x0, int z0, int step) {
      int lattice = this.cellXz;
      int last = 65 * step;
      int lx0 = Math.floorDiv(x0, lattice) * lattice;
      int lz0 = Math.floorDiv(z0, lattice) * lattice;
      int nx = Math.floorDiv(x0 + last - lx0 + lattice - 1, lattice) + 1;
      int nz = Math.floorDiv(z0 + last - lz0 + lattice - 1, lattice) + 1;
      DensityVolume volume = new DensityVolume(nx, 1, nz, lx0, 0, lz0, lattice, 1, lattice);
      ScopedDensityBuffer buffer = w.estimate.sampleVolume(volume);

      try {
         for (int z = 0; z < 66; z++) {
            int bz = z0 + z * step - lz0;
            int iz = bz / lattice;
            int iz1 = Math.min(nz - 1, iz + 1);
            float fz = (float)(bz - iz * lattice) / lattice;

            for (int x = 0; x < 66; x++) {
               int bx = x0 + x * step - lx0;
               int ix = bx / lattice;
               int ix1 = Math.min(nx - 1, ix + 1);
               float fx = (float)(bx - ix * lattice) / lattice;
               float a = buffer.get(volume.indexUnchecked(ix, 0, iz));
               float b = buffer.get(volume.indexUnchecked(ix1, 0, iz));
               float c = buffer.get(volume.indexUnchecked(ix, 0, iz1));
               float d = buffer.get(volume.indexUnchecked(ix1, 0, iz1));
               float top = a + (b - a) * fx;
               float bottom = c + (d - c) * fx;
               w.estimates[z * 66 + x] = top + (bottom - top) * fz;
            }
         }
      } catch (Throwable var30) {
         if (buffer != null) {
            try {
               buffer.close();
            } catch (Throwable var29) {
               var30.addSuppressed(var29);
            }
         }

         throw var30;
      }

      if (buffer != null) {
         buffer.close();
      }
   }

   private void sampleExact(NoiseTerrain.Worker w, int x0, int z0, int step) {
      int groupsX = this.exactGroups(x0, step, w.startsX);
      int groupsZ = this.exactGroups(z0, step, w.startsZ);

      for (int iz = 0; iz < groupsZ; iz++) {
         for (int ix = 0; ix < groupsX; ix++) {
            int gx = w.startsX[ix];
            int gz = w.startsZ[iz];
            int sx = w.startsX[ix + 1] - gx;
            int sz = w.startsZ[iz + 1] - gz;
            float lo = Float.MAX_VALUE;
            float hi = -Float.MAX_VALUE;

            for (int z = 0; z < sz; z++) {
               for (int x = 0; x < sx; x++) {
                  float e = w.estimates[(gz + z) * 66 + gx + x];
                  lo = Math.min(lo, e);
                  hi = Math.max(hi, e);
               }
            }

            int yHi = Math.min(this.maxY - 1, (int)Math.ceil(hi) + 8);
            if (!this.surfaceKnown || Math.max(this.minY, (int)Math.floor(lo) - 16) > yHi) {
               yHi = this.maxY - 1;
            }

            this.exactTopDown(w, x0, z0, step, gx, gz, sx, sz, yHi);
         }
      }
   }

   private void exactTopDown(NoiseTerrain.Worker w, int x0, int z0, int step, int gx, int gz, int sx, int sz, int start) {
      int yHi = start;
      int low = Math.max(this.minY, this.alignDown(yHi - 16));
      this.sampleExactRange(w, x0, z0, step, gx, gz, sx, sz, low, yHi);

      for (int attempt = 0; attempt < 48; attempt++) {
         boolean up = false;

         for (int z = 0; z < sz && !up; z++) {
            for (int x = 0; x < sx && !up; x++) {
               up = w.profile[((gz + z) * 66 + gx + x) * (w.height + 1) + yHi - this.minY] > 0.0F;
            }
         }

         if (!up || yHi >= this.maxY - 1) {
            break;
         }

         int next = Math.min(this.maxY - 1, yHi + 24);
         this.sampleExactRange(w, x0, z0, step, gx, gz, sx, sz, yHi + 1, next);
         yHi = next;
      }

      while (true) {
         boolean open = false;

         for (int z = 0; z < sz; z++) {
            for (int x = 0; x < sx; x++) {
               int i = (gz + z) * 66 + gx + x;
               int base = i * (w.height + 1);
               int found = Integer.MIN_VALUE;

               for (int y = yHi; y >= low; y--) {
                  if (w.profile[base + y - this.minY] > 0.0F) {
                     found = y + 1;
                     break;
                  }
               }

               w.top[i] = found;
               w.profileLow[i] = low;
               if (found == Integer.MIN_VALUE) {
                  open = true;
               }
            }
         }

         if (!open || low <= this.minY) {
            return;
         }

         int next = Math.max(this.minY, this.alignDown(low - 32 - 1));
         this.sampleExactRange(w, x0, z0, step, gx, gz, sx, sz, next, low - 1);
         low = next;
      }
   }

   private void sampleExactRange(NoiseTerrain.Worker w, int x0, int z0, int step, int gx, int gz, int sx, int sz, int yFrom, int yTo) {
      int sizeY = yTo - yFrom + 1;
      if (sizeY > 0) {
         DensityVolume volume = new DensityVolume(sx, sizeY, sz, x0 + gx * step, yFrom, z0 + gz * step, step, 1, step);
         ScopedDensityBuffer buffer = w.exact.sampleVolume(volume);

         try {
            for (int z = 0; z < sz; z++) {
               for (int x = 0; x < sx; x++) {
                  int base = ((gz + z) * 66 + gx + x) * (w.height + 1) + yFrom - this.minY;

                  for (int y = 0; y < sizeY; y++) {
                     w.profile[base + y] = buffer.get(volume.indexUnchecked(x, y, z));
                  }
               }
            }
         } catch (Throwable var19) {
            if (buffer != null) {
               try {
                  buffer.close();
               } catch (Throwable var18) {
                  var19.addSuppressed(var18);
               }
            }

            throw var19;
         }

         if (buffer != null) {
            buffer.close();
         }
      }
   }

   private void sampleCorners(NoiseTerrain.Worker w, int x0, int z0, int step) {
      Bound sampler = step == this.cellXz ? w.coarse : w.corner;

      for (int gz = 0; gz < 66; gz += 6) {
         for (int gx = 0; gx < 66; gx += 6) {
            int sx = Math.min(6, 66 - gx);
            int sz = Math.min(6, 66 - gz);
            float lo = Float.MAX_VALUE;
            float hi = -Float.MAX_VALUE;

            for (int z = 0; z < sz; z++) {
               for (int x = 0; x < sx; x++) {
                  float e = w.estimates[(gz + z) * 66 + gx + x];
                  lo = Math.min(lo, e);
                  hi = Math.max(hi, e);
               }
            }

            int yHi = this.alignDown(Math.min(this.maxY - 1, (int)Math.ceil(hi) + 8));
            if (!this.surfaceKnown || this.alignDown(Math.max(this.minY, (int)Math.floor(lo) - 16)) > yHi) {
               yHi = this.alignDown(this.maxY - 1);
            }

            this.cornersTopDown(w, sampler, x0, z0, step, gx, gz, sx, sz, yHi);
         }
      }
   }

   private void cornersTopDown(NoiseTerrain.Worker w, Bound sampler, int x0, int z0, int step, int gx, int gz, int sx, int sz, int start) {
      int origin = this.alignDown(this.minY);
      int yHi = start;
      int low = Math.max(origin, yHi - 2 * this.cellY);
      this.sampleCornerRange(w, sampler, x0, z0, step, gx, gz, sx, sz, low, yHi);

      for (int attempt = 0; attempt < 48; attempt++) {
         boolean up = false;

         for (int z = 0; z < sz && !up; z++) {
            for (int x = 0; x < sx && !up; x++) {
               up = w.profile[((gz + z) * 66 + gx + x) * (w.height + 1) + (yHi - origin) / this.cellY] > 0.0F;
            }
         }

         if (!up || yHi + this.cellY >= this.maxY) {
            break;
         }

         int next = this.alignDown(Math.min(this.maxY - 1, yHi + 24));
         this.sampleCornerRange(w, sampler, x0, z0, step, gx, gz, sx, sz, yHi + this.cellY, next);
         yHi = next;
      }

      while (true) {
         boolean open = false;

         for (int z = 0; z < sz; z++) {
            for (int x = 0; x < sx; x++) {
               int i = (gz + z) * 66 + gx + x;
               w.top[i] = this.cornerTop(w, i * (w.height + 1), low, yHi);
               w.profileLow[i] = low;
               if (w.top[i] == Integer.MIN_VALUE) {
                  open = true;
               }
            }
         }

         if (!open || low <= origin) {
            return;
         }

         int next = Math.max(origin, low - 5 * this.cellY);
         this.sampleCornerRange(w, sampler, x0, z0, step, gx, gz, sx, sz, next, low - this.cellY);
         low = next;
      }
   }

   private int cornerTop(NoiseTerrain.Worker w, int base, int yLo, int yHi) {
      int origin = this.alignDown(this.minY);
      float above = Float.NaN;

      for (int y = yHi; y >= yLo; y -= this.cellY) {
         float d = w.profile[base + (y - origin) / this.cellY];
         if (d > 0.0F) {
            if (Float.isNaN(above)) {
               return Math.min(this.maxY, y + this.cellY);
            }

            int k = (int)Math.ceil(d * this.cellY / (d - above)) - 1;
            return Math.max(this.minY, y + Math.max(0, Math.min(this.cellY - 1, k)) + 1);
         }

         above = d;
      }

      return Integer.MIN_VALUE;
   }

   private void sampleCornerRange(NoiseTerrain.Worker w, Bound sampler, int x0, int z0, int step, int gx, int gz, int sx, int sz, int yFrom, int yTo) {
      int sizeY = (yTo - yFrom) / this.cellY + 1;
      if (sizeY > 0) {
         int origin = this.alignDown(this.minY);
         DensityVolume volume = new DensityVolume(sx, sizeY, sz, x0 + gx * step, yFrom, z0 + gz * step, step, this.cellY, step);
         ScopedDensityBuffer buffer = sampler.sampleVolume(volume);

         try {
            for (int z = 0; z < sz; z++) {
               for (int x = 0; x < sx; x++) {
                  int base = ((gz + z) * 66 + gx + x) * (w.height + 1) + (yFrom - origin) / this.cellY;

                  for (int y = 0; y < sizeY; y++) {
                     w.profile[base + y] = buffer.get(volume.indexUnchecked(x, y, z));
                  }
               }
            }
         } catch (Throwable var21) {
            if (buffer != null) {
               try {
                  buffer.close();
               } catch (Throwable var20) {
                  var21.addSuppressed(var20);
               }
            }

            throw var21;
         }

         if (buffer != null) {
            buffer.close();
         }
      }
   }

   private void carve(NoiseTerrain.Worker w, int x0, int z0, int step) {
      int origin = this.alignDown(this.minY);

      for (int i = 0; i < 4356; i++) {
         int top = w.top[i];
         if (top != Integer.MIN_VALUE && top > this.minY) {
            int bx = x0 + i % 66 * step;
            int bz = z0 + i / 66 * step;
            int y0 = this.alignDown(top - 1);
            float uncut = w.profile[i * (w.height + 1) + (y0 - origin) / this.cellY];
            float cut = w.carved.sampleValue(bx, y0, bz);
            if (!(cut >= uncut)) {
               float above = w.carved.sampleValue(bx, y0 + this.cellY, bz);
               float d = cut;
               int y = y0;

               for (int steps = 0; d <= 0.0F && y - this.cellY >= origin && steps++ < 24; d = w.carved.sampleValue(bx, y, bz)) {
                  above = d;
                  y -= this.cellY;
               }

               if (!(d <= 0.0F)) {
                  int k = (int)Math.ceil(d * this.cellY / (d - above)) - 1;
                  w.top[i] = Math.max(this.minY, y + Math.max(0, Math.min(this.cellY - 1, k)) + 1);
               }
            }
         }
      }
   }

   private int runStep(int step) {
      return Math.max(this.cellY, Math.min(step, 16));
   }

   private void scanRuns(NoiseTerrain.Worker w, int x0, int z0, int step) {
      int s = this.runStep(step);
      Arrays.fill(w.bottom, this.minY);
      Arrays.fill(w.runCount, 0);
      int lowGrid = Math.floorDiv(this.minY + s - 1, s) * s;
      int group = step < this.cellXz ? 66 : 8;

      for (int gz = 0; gz < 66; gz += group) {
         for (int gx = 0; gx < 66; gx += group) {
            int sx = Math.min(group, 66 - gx);
            int sz = Math.min(group, 66 - gz);
            boolean any = false;

            for (int z = 0; z < sz; z++) {
               for (int x = 0; x < sx; x++) {
                  int i = (gz + z) * 66 + gx + x;
                  int t = w.top[i];
                  w.scanSolid[i] = true;
                  w.scanAbove[i] = Float.NaN;
                  w.scanTop[i] = t;
                  w.scanSkip[i] = t == Integer.MIN_VALUE || t <= this.minY;
                  if (!w.scanSkip[i]) {
                     int limit = lowGrid;
                     float estimate = w.estimates[i];
                     if (this.surfaceKnown && !Float.isNaN(estimate)) {
                        limit = Math.max(lowGrid, Math.floorDiv((int)Math.floor(estimate) - 0, s) * s);
                     }

                     w.scanLimit[i] = limit;
                     if (Math.floorDiv(t - 1, s) * s >= limit && (!this.surfaceKnown || step >= this.cellXz || !this.solidDown(w, i, limit))) {
                        any = true;
                     } else {
                        w.scanSkip[i] = true;
                     }
                  }
               }
            }

            if (any) {
               if (this.scanSlab(w, x0, z0, step, gx, gz, sx, sz, s, false)) {
                  this.scanSlab(w, x0, z0, step, gx, gz, sx, sz, s, true);
               }

               for (int z = 0; z < sz; z++) {
                  for (int x = 0; x < sx; x++) {
                     int i = (gz + z) * 66 + gx + x;
                     if (w.top[i] != Integer.MIN_VALUE && w.top[i] > this.minY && w.scanSolid[i]) {
                        if (w.runCount[i] == 0) {
                           w.bottom[i] = this.minY;
                        } else {
                           w.runs[(i * 8 + w.runCount[i] - 1) * 2 + 1] = this.minY;
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private boolean solidDown(NoiseTerrain.Worker w, int i, int limit) {
      if (w.profileLow[i] > limit) {
         return false;
      }

      int base = i * (w.height + 1);

      for (int y = w.top[i] - 1; y >= limit; y--) {
         if (w.profile[base + y - this.minY] <= 0.0F) {
            return false;
         }
      }

      return true;
   }

   private boolean scanSlab(NoiseTerrain.Worker w, int x0, int z0, int step, int gx, int gz, int sx, int sz, int s, boolean below) {
      int lowGrid = Math.floorDiv(this.minY + s - 1, s) * s;
      int bx0 = x0 + gx * step;
      int bz0 = z0 + gz * step;
      int cell = this.cellXz;
      boolean aligned = step % cell == 0 && Math.floorMod(bx0, cell) == 0 && Math.floorMod(bz0, cell) == 0;
      boolean reuse = aligned && step >= cell && s % this.cellY == 0;
      int origin = this.alignDown(this.minY);
      int from = Integer.MAX_VALUE;
      int to = Integer.MIN_VALUE;

      for (int z = 0; z < sz; z++) {
         for (int x = 0; x < sx; x++) {
            int i = (gz + z) * 66 + gx + x;
            if (!w.scanSkip[i] && (!below || !w.scanSolid[i] && w.scanLimit[i] > lowGrid)) {
               int lo = below ? lowGrid : w.scanLimit[i];
               int hi = below ? w.scanLimit[i] - s : Math.floorDiv(w.top[i] - 1, s) * s;
               if (reuse) {
                  hi = Math.min(hi, Math.floorDiv(w.profileLow[i] - 1, s) * s);
               }

               if (hi >= lo) {
                  from = Math.min(from, lo);
                  to = Math.max(to, hi);
               }
            }
         }
      }

      int ny = to >= from ? (to - from) / s + 1 : 0;
      int lx0 = aligned ? bx0 : Math.floorDiv(bx0, cell) * cell;
      int lz0 = aligned ? bz0 : Math.floorDiv(bz0, cell) * cell;
      int nx = aligned ? sx : Math.floorDiv(bx0 + (sx - 1) * step - lx0, cell) + 2;
      int nz = aligned ? sz : Math.floorDiv(bz0 + (sz - 1) * step - lz0, cell) + 2;
      int stride = aligned ? step : cell;
      boolean open = false;
      DensityVolume volume = ny > 0 ? new DensityVolume(nx, ny, nz, lx0, from, lz0, stride, s, stride) : null;
      ScopedDensityBuffer buffer = volume != null ? w.shape.sampleVolume(volume) : null;

      try {
         for (int z = 0; z < sz; z++) {
            int rz = bz0 + z * step - lz0;
            int iz = aligned ? z : rz / cell;
            float fz = aligned ? 0.0F : (float)(rz - iz * cell) / cell;

            for (int x = 0; x < sx; x++) {
               int i = (gz + z) * 66 + gx + x;
               if (!w.scanSkip[i] && (!below || !w.scanSolid[i] && w.scanLimit[i] > lowGrid)) {
                  int rx = bx0 + x * step - lx0;
                  int ix = aligned ? x : rx / cell;
                  float fx = aligned ? 0.0F : (float)(rx - ix * cell) / cell;
                  int base = i * (w.height + 1);
                  int yTop = below ? w.scanLimit[i] - s : Math.floorDiv(w.top[i] - 1, s) * s;
                  int yLow = below ? lowGrid : w.scanLimit[i];

                  for (int y = yTop; y >= yLow; y -= s) {
                     float d;
                     if (reuse && y >= w.profileLow[i]) {
                        d = w.profile[base + (y - origin) / this.cellY];
                     } else {
                        if (buffer == null || y < from || y > to) {
                           continue;
                        }

                        int k = (y - from) / s;
                        if (aligned) {
                           d = buffer.get(volume.indexUnchecked(ix, k, iz));
                        } else {
                           float v00 = buffer.get(volume.indexUnchecked(ix, k, iz));
                           float v10 = buffer.get(volume.indexUnchecked(ix + 1, k, iz));
                           float v01 = buffer.get(volume.indexUnchecked(ix, k, iz + 1));
                           float v11 = buffer.get(volume.indexUnchecked(ix + 1, k, iz + 1));
                           float a = v00 + (v10 - v00) * fx;
                           float c = v01 + (v11 - v01) * fx;
                           d = a + (c - a) * fz;
                        }
                     }

                     this.scanStep(w, i, y, d, s);
                     if (w.runCount[i] < 0) {
                        break;
                     }
                  }

                  if (w.runCount[i] < 0) {
                     w.runCount[i] = 8;
                  } else if (!w.scanSolid[i] && w.scanLimit[i] > lowGrid) {
                     open = true;
                  }
               }
            }
         }
      } catch (Throwable var51) {
         if (buffer != null) {
            try {
               buffer.close();
            } catch (Throwable var50) {
               var51.addSuppressed(var50);
            }
         }

         throw var51;
      }

      if (buffer != null) {
         buffer.close();
      }

      return open;
   }

   private void scanStep(NoiseTerrain.Worker w, int i, int y, float d, int s) {
      float above = w.scanAbove[i];
      if (w.scanSolid[i]) {
         if (d <= 0.0F) {
            int b = Math.min(Float.isNaN(above) ? y + 1 : underside(y, d, above, s), w.scanTop[i] - 1);
            int runs = w.runCount[i];
            if (runs == 0) {
               w.bottom[i] = b;
            } else {
               w.runs[(i * 8 + runs - 1) * 2 + 1] = b;
            }

            w.scanSolid[i] = false;
         }
      } else if (d > 0.0F) {
         int runTop = Float.isNaN(above) ? y + 1 : upperside(y, d, above, s);
         int runs = w.runCount[i];
         if (runs >= 8) {
            w.runs[(i * 8 + 8 - 1) * 2 + 1] = this.minY;
            w.scanSolid[i] = false;
            w.runCount[i] = -1;
            return;
         }

         w.runs[(i * 8 + runs) * 2] = runTop;
         w.runs[(i * 8 + runs) * 2 + 1] = this.minY;
         w.runCount[i] = runs + 1;
         w.scanTop[i] = runTop;
         w.scanSolid[i] = true;
      }

      w.scanAbove[i] = d;
   }

   private void carveColumns(NoiseTerrain.Worker w, int x0, int z0, int step, int level) {
      System.arraycopy(w.top, 0, w.cut, 0, 4356);
      Carving carving = this.carving;
      if (carving != null && level <= (this.surfaceKnown ? 1 : 2)) {
         Function<ChunkPos, Holder<Biome>> biomeAt = source -> w.localBiomes
            .getNoiseBiome(QuartPos.fromBlock(source.getMinBlockX()), 0, QuartPos.fromBlock(source.getMinBlockZ()));
         long lastChunk = Long.MIN_VALUE;
         Carving.Mask mask = null;

         for (int i = 0; i < 4356; i++) {
            int top = w.top[i];
            if (top != Integer.MIN_VALUE && top > this.minY) {
               int bx = x0 + i % 66 * step;
               int bz = z0 + i / 66 * step;
               long chunk = ChunkPos.pack(bx >> 4, bz >> 4);
               if (chunk != lastChunk) {
                  mask = carving.mask(bx >> 4, bz >> 4, biomeAt);
                  lastChunk = chunk;
               }

               if (mask != null) {
                  int bottom = w.bottom[i];
                  int t = top;

                  while (t - 1 >= bottom && mask.carved(bx, t - 1, bz)) {
                     t--;
                  }

                  w.cut[i] = t;
                  if (bottom > this.minY) {
                     while (bottom < t && mask.carved(bx, bottom, bz)) {
                        bottom++;
                     }

                     w.bottom[i] = bottom;
                  }

                  for (int r = 0; r < Math.max(0, w.runCount[i]); r++) {
                     int at = (i * 8 + r) * 2;
                     int runTop = w.runs[at];
                     int runBottom = w.runs[at + 1];

                     while (runTop - 1 >= runBottom && mask.carved(bx, runTop - 1, bz)) {
                        runTop--;
                     }

                     if (runBottom > this.minY) {
                        while (runBottom < runTop && mask.carved(bx, runBottom, bz)) {
                           runBottom++;
                        }
                     }

                     w.runs[at] = runTop;
                     w.runs[at + 1] = Math.min(runBottom, runTop);
                  }
               }
            }
         }
      }
   }

   private static int underside(int y, float d, float above, int s) {
      double t = (double)(-d) * s / (above - d);
      return Math.min(y + s, (int)Math.floor(y + t) + 1);
   }

   private static int upperside(int y, float d, float above, int s) {
      int k = (int)Math.ceil((double)d * s / (d - above)) - 1;
      return y + Math.max(0, Math.min(s - 1, k)) + 1;
   }

   private int alignDown(int y) {
      return Math.floorDiv(y, this.cellY) * this.cellY;
   }

   private void addIceberg(TileBuilder b, SurfaceColumn column, int top, int fluid, boolean cold, int biomeId, int iceId, int fluidId) {
      int runId = Integer.MIN_VALUE;
      int runTop = 0;

      for (int y = Math.max(column.high(), fluid - 1); y >= top; y--) {
         BlockState placed = column.placed(y);
         int id;
         if (placed != null) {
            id = Block.getId(placed);
         } else if (y < fluid) {
            id = y == fluid - 1 && cold ? iceId : fluidId;
         } else {
            id = Integer.MIN_VALUE;
         }

         if (id != runId) {
            if (runId != Integer.MIN_VALUE) {
               b.add(Span.pack(runTop + 1, y + 1, runId, biomeId, y + 1 >= fluid - 1 ? 15 : 14, 0));
            }

            runId = id;
            runTop = y;
         }
      }

      if (runId != Integer.MIN_VALUE) {
         b.add(Span.pack(runTop + 1, top, runId, biomeId, top >= fluid - 1 ? 15 : 14, 0));
      }
   }

   private boolean buildColumns(NoiseTerrain.Worker w, int x0, int z0, int step, int level, boolean snowCover) {
      w.prefill.open();

      try {
         return this.fillColumns(w, x0, z0, step, level, snowCover);
      } finally {
         w.prefill.close();
      }
   }

   private boolean fillColumns(NoiseTerrain.Worker w, int x0, int z0, int step, int level, boolean snowCover) {
      boolean degraded = false;
      boolean exactBiomes = step == 1;
      Function<BlockPos, Holder<Biome>> biomeGetter = exactBiomes
         ? w.biomeManager::getBiome
         : posx -> w.cachedBiomes.getNoiseBiome(posx.getX() >> 2, posx.getY() >> 2, posx.getZ() >> 2);
      MaterialRuleContext context = null;
      RuleEvaluator rule = null;
      if (MaterialAccess.AVAILABLE) {
         try {
            DensityVolume volume = step <= 2
               ? this.materialVolume(w, x0, z0, step)
               : (w.minSurfaceReady ? new DensityVolume(1, 1, 1, x0, 0, z0) : new DensityVolume(66, 1, 66, x0, 0, z0, 1, 1, 1));
            context = MaterialAccess.create(
               this.randomState.surfaceSystem(), this.randomState, volume, w.samplers, biomeGetter, this.generationContext, this.possibleBiomes
            );
            rule = this.materialRule.compile(context);
         } catch (RuntimeException e) {
            context = null;
            rule = null;
            degraded = true;
         }
      }

      MutableBlockPos pos = new MutableBlockPos();
      int defaultId = Block.getId(this.defaultBlock);
      int fluidId = Block.getId(this.defaultFluid);
      int lavaId = Block.getId(Blocks.LAVA.defaultBlockState());
      int iceId = Block.getId(Blocks.ICE.defaultBlockState());
      int snowId = Block.getId(Blocks.SNOW.defaultBlockState());

      for (int cz = -1; cz <= 64; cz++) {
         for (int cx = -1; cx <= 64; cx++) {
            int i = (cz + 1) * 66 + cx + 1;
            TileBuilder b = w.builder;
            b.begin();
            if (w.top[i] == Integer.MIN_VALUE) {
               b.end(cx, cz);
            } else {
               int bx = x0 + (cx + 1) * step;
               int bz = z0 + (cz + 1) * step;
               boolean border = cx < 0 || cz < 0 || cx >= 64 || cz >= 64;
               Holder<Biome> biome = biomeGetter.apply(pos.set(bx, Math.max(w.top[i], w.fluidTop[i]) - 1, bz));
               int biomeId = this.biomeIds.id(biome);
               int fluid = w.fluidTop[i];
               boolean cold = !((Biome)biome.value()).warmEnoughToRain(pos.set(bx, Math.max(w.top[i], fluid), bz), this.seaLevel);
               int gradientX = border ? 0 : Math.round((float)(this.surface(w, cx + 1, cz) - this.surface(w, cx - 1, cz)) / step);
               int gradientZ = border ? 0 : Math.round((float)(this.surface(w, cx, cz + 1) - this.surface(w, cx, cz - 1)) / step);
               boolean materials = rule != null;
               if (materials) {
                  try {
                     MaterialAccess.updateXZ(context, bx, bz, gradientX, gradientZ);
                     if (w.minSurfaceReady) {
                        MaterialAccess.presetMinSurface(context, w.minSurface[i]);
                     }
                  } catch (RuntimeException e) {
                     materials = false;
                     degraded = true;
                  }
               }

               int top = w.top[i];
               boolean iceberg = false;
               if (materials && MaterialAccess.EXTENSIONS) {
                  try {
                     if (fluid == Integer.MIN_VALUE && biome.is(Biomes.ERODED_BADLANDS)) {
                        w.surfaceColumn.reset(top, fluid, this.defaultBlock, this.defaultFluid);
                        MaterialAccess.erodedBadlands(this.randomState.surfaceSystem(), w.surfaceColumn, bx, bz, top, this.heights);
                        if (w.surfaceColumn.changed()) {
                           top = Math.max(top, w.surfaceColumn.high() + 1);
                        }
                     } else if (fluid != Integer.MIN_VALUE && (biome.is(Biomes.FROZEN_OCEAN) || biome.is(Biomes.DEEP_FROZEN_OCEAN))) {
                        w.surfaceColumn.reset(top, fluid, this.defaultBlock, this.defaultFluid);
                        MaterialAccess.frozenOcean(
                           this.randomState.surfaceSystem(), context, (Biome)biome.value(), w.surfaceColumn, pos, bx, bz, Math.max(top, fluid)
                        );
                        iceberg = w.surfaceColumn.changed();
                     }
                  } catch (RuntimeException e) {
                     iceberg = false;
                  }
               }

               int cut = w.cut[i] < w.top[i] ? w.cut[i] : top;
               int floorTop = Math.min(top, cut);
               boolean snow = cold && !border && snowCover && !iceberg && fluid == Integer.MIN_VALUE && cut >= top;
               if (iceberg) {
                  this.addIceberg(b, w.surfaceColumn, top, fluid, cold, biomeId, iceId, Block.getId(this.defaultFluid));
               } else if (fluid != Integer.MIN_VALUE) {
                  int state = fluid - 1 < Math.min(-54, this.seaLevel) ? lavaId : fluidId;
                  if (cold && state == fluidId && this.defaultFluid.is(Blocks.WATER)) {
                     b.add(Span.pack(fluid, fluid - 1, iceId, biomeId, 15, 0));
                     if (fluid - 1 > floorTop) {
                        b.add(Span.pack(fluid - 1, floorTop, state, biomeId, 14, 0));
                     }
                  } else {
                     b.add(Span.pack(fluid, floorTop, state, biomeId, 15, state == lavaId ? 15 : 0));
                  }
               }

               int groundSky = fluid == Integer.MIN_VALUE ? 15 : Math.max(0, 15 - (fluid - top));
               if (!border && materials) {
                  int waterHeight = fluid == Integer.MIN_VALUE ? Integer.MIN_VALUE : fluid;
                  int fullDepth = w.shallow ? 2 : (step <= 2 ? 8 : 5);
                  if (snowCover && top == w.top[i]) {
                     int low = Math.min(
                        Math.min(this.ground(w, cx + 1, cz), this.ground(w, cx - 1, cz)), Math.min(this.ground(w, cx, cz + 1), this.ground(w, cx, cz - 1))
                     );
                     int edge = cx != 0 && cz != 0 && cx != 63 && cz != 63 ? 0 : Math.max(2, 2 * step);
                     fullDepth = Math.min(fullDepth, Math.max(2, top - low + 1 + edge));
                  }

                  int materialDepth = fullDepth + top - w.top[i];
                  if (this.materialRun(b, context, rule, bx, bz, top, floorTop, w.bottom[i], materialDepth, waterHeight, biomeId, groundSky, snow, snowId)) {
                     degraded = true;
                  }

                  for (int r = 0; r < w.runCount[i]; r++) {
                     int runTop = w.runs[(i * 8 + r) * 2];
                     int runBottom = w.runs[(i * 8 + r) * 2 + 1];
                     int above = r == 0 ? w.bottom[i] : w.runs[(i * 8 + r - 1) * 2 + 1];
                     int gapWater = this.gapFluid(b, runTop, above, biomeId, fluidId);
                     int runBiome = this.biomeIds.id(biomeGetter.apply(pos.set(bx, runTop - 1, bz)));
                     if (this.materialRun(b, context, rule, bx, bz, runTop, runTop, runBottom, fullDepth, gapWater, runBiome, groundSky, false, snowId)) {
                        degraded = true;
                     }
                  }

                  b.end(cx, cz);
               } else {
                  if (snow && holdsSnow(this.defaultBlock)) {
                     b.add(Span.pack(top + 1, top, snowId, biomeId, 15, 0));
                  }

                  if (floorTop > w.bottom[i]) {
                     b.add(Span.pack(floorTop, w.bottom[i], defaultId, biomeId, groundSky, 0));
                  }

                  for (int r = 0; r < w.runCount[i]; r++) {
                     int runTop = w.runs[(i * 8 + r) * 2];
                     int above = r == 0 ? w.bottom[i] : w.runs[(i * 8 + r - 1) * 2 + 1];
                     this.gapFluid(b, runTop, above, biomeId, fluidId);
                     if (runTop > w.runs[(i * 8 + r) * 2 + 1]) {
                        b.add(Span.pack(runTop, w.runs[(i * 8 + r) * 2 + 1], defaultId, biomeId, groundSky, 0));
                     }
                  }

                  b.end(cx, cz);
               }
            }
         }
      }

      return degraded;
   }

   private int gapFluid(TileBuilder b, int runTop, int above, int biomeId, int fluidId) {
      if (!this.defaultFluid.isAir() && runTop < this.seaLevel) {
         int fluidTop = Math.min(above, this.seaLevel);
         if (fluidTop <= runTop) {
            return Integer.MIN_VALUE;
         }

         b.add(Span.pack(fluidTop, runTop, fluidId, biomeId, 14, 0));
         return fluidTop;
      } else {
         return Integer.MIN_VALUE;
      }
   }

   private boolean materialRun(
      TileBuilder b,
      MaterialRuleContext context,
      RuleEvaluator rule,
      int bx,
      int bz,
      int top,
      int cut,
      int bottom,
      int materialDepth,
      int waterHeight,
      int biomeId,
      int groundSky,
      boolean snow,
      int snowId
   ) {
      if (cut <= bottom) {
         return false;
      }

      boolean floating = bottom > this.minY;
      int depth = 0;
      int nulls = 0;
      boolean degraded = false;
      BlockState last = this.defaultBlock;

      while (depth < materialDepth) {
         int y = top - 1 - depth;
         if (y < bottom) {
            break;
         }

         try {
            MaterialAccess.updateY(context, depth + 1, floating ? Math.min(64, y - bottom + 1) : 64, waterHeight, y);
         } catch (RuntimeException e) {
            degraded = true;
            break;
         }

         depth++;

         BlockState state;
         try {
            state = rule.tryApply(bx, y, bz);
         } catch (RuntimeException e) {
            state = null;
         }

         if (state == null) {
            state = this.defaultBlock;
            nulls++;
         } else {
            nulls = 0;
         }

         last = state;
         if (depth == 1 && snow && holdsSnow(state)) {
            b.add(Span.pack(top + 1, top, snowId, biomeId, 15, 0));
         }

         int id = Block.getId(state);
         if (y < cut) {
            b.add(Span.pack(y + 1, y, id, biomeId, groundSky, state.getLightEmission()));
         }

         if (nulls >= 2 && depth >= 3) {
            break;
         }
      }

      int restTop = Math.min(cut, top - depth);
      if (restTop > bottom) {
         BlockState rest = depth >= materialDepth ? last : this.defaultBlock;
         b.add(Span.pack(restTop, bottom, Block.getId(rest), biomeId, groundSky, 0));
      }

      return degraded;
   }

   private DensityVolume materialVolume(NoiseTerrain.Worker w, int x0, int z0, int step) {
      int low = Integer.MAX_VALUE;
      int high = Integer.MIN_VALUE;

      for (int i = 0; i < 4356; i++) {
         int t = w.top[i];
         if (t != Integer.MIN_VALUE) {
            low = Math.min(low, t);
            high = Math.max(high, t);
         }
      }

      int from = low == Integer.MAX_VALUE ? 0 : Math.max(this.minY, low - 8 - 2);
      int height = high == Integer.MIN_VALUE ? 1 : high - from;
      return height >= 1 && height <= 48 ? new DensityVolume(66, height, 66, x0, from, z0, step, 1, step) : new DensityVolume(66, 1, 66, x0, 0, z0, 1, 1, 1);
   }

   static boolean holdsSnow(BlockState below) {
      Boolean known = SNOW_SUPPORT.get(below);
      if (known != null) {
         return known;
      }

      boolean holds = !below.is(BlockTags.CANNOT_SUPPORT_SNOW_LAYER)
         && (
            below.is(BlockTags.SUPPORT_OVERRIDE_SNOW_LAYER)
               || Block.isFaceFull(below.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO), Direction.UP)
         );
      SNOW_SUPPORT.put(below, holds);
      return holds;
   }

   private int ground(NoiseTerrain.Worker w, int cx, int cz) {
      int x = Math.max(-1, Math.min(64, cx));
      int z = Math.max(-1, Math.min(64, cz));
      int t = w.top[(z + 1) * 66 + x + 1];
      return t == Integer.MIN_VALUE ? this.minY : t;
   }

   private int surface(NoiseTerrain.Worker w, int cx, int cz) {
      int x = Math.max(-1, Math.min(64, cx));
      int z = Math.max(-1, Math.min(64, cz));
      int i = (z + 1) * 66 + x + 1;
      return Math.max(w.top[i], w.fluidTop[i]);
   }

   static int[] copy(int[] values) {
      return Arrays.copyOf(values, values.length);
   }

   private static final class BiomeTable {
      private static final int BITS = 21;
      private static final int XZ = 524288;
      private static final int Y = 256;
      private static final VarHandle SLOTS = MethodHandles.arrayElementVarHandle(long[].class);
      private final long[] slots = new long[2097152];
      private final List<Holder<Biome>> biomes;
      private final Map<Holder<Biome>, Integer> index = new IdentityHashMap<>();

      BiomeTable(Set<Holder<Biome>> possible) {
         this.biomes = List.copyOf(possible);

         for (int i = 0; i < this.biomes.size() && i < 32766; i++) {
            this.index.put(this.biomes.get(i), i + 1);
         }
      }

      private static long tag(int qx, int qy, int qz) {
         return qx >= -524288 && qx < 524288 && qz >= -524288 && qz < 524288 && qy >= -256 && qy < 256
            ? (long)(qx + 524288) << 29 | (long)(qz + 524288) << 9 | qy + 256
            : -1L;
      }

      private static int slot(long tag) {
         return (int)(tag * -7046029254386353131L >>> 43);
      }

      @Nullable Holder<Biome> get(int qx, int qy, int qz) {
         long tag = tag(qx, qy, qz);
         if (tag < 0L) {
            return null;
         }

         long value = (long)SLOTS.getOpaque((long[])this.slots, (int)slot(tag));
         return value != 0L && value >>> 15 == tag ? this.biomes.get((int)(value & 32767L) - 1) : null;
      }

      void put(int qx, int qy, int qz, Holder<Biome> biome) {
         long tag = tag(qx, qy, qz);
         Integer id = this.index.get(biome);
         if (tag >= 0L && id != null) {
            SLOTS.setOpaque((long[])this.slots, (int)slot(tag), (long)(tag << 15 | id.intValue()));
         }
      }
   }

   private final class Worker {
      final DensityBufferPool pool = new DensityBufferPool(256);
      final PrefillBuffers prefill = new PrefillBuffers(209088);
      final CornerCache cornerCache = new CornerCache(NoiseTerrain.this.minY, NoiseTerrain.this.maxY);
      final SamplerContext context = SamplerContext.builder()
         .setUserFields(ContextMap.builder().set(Beardifier.CONTEXT_KEY, Beardifier.EMPTY).set(CornerCache.KEY, this.cornerCache).build())
         .useBufferArena(this.pool)
         .enableCaches()
         .build();
      final DensitySamplerSet samplers = NoiseTerrain.this.randomState.samplersWithContext(this.context);
      final Bound exact = this.samplers.get(NoiseTerrain.this.exactCached);
      final Bound corner = this.samplers.get(NoiseTerrain.this.cornerDensity);
      final Bound coarse = this.samplers.get(NoiseTerrain.this.coarseDensity);
      final Bound carved = this.samplers.get(NoiseTerrain.this.carvedDensity);
      final Bound shape = this.samplers.get(NoiseTerrain.this.shapeDensity);
      final Bound estimate = this.samplers.get(NoiseTerrain.this.surfaceEstimate);
      final Long2ObjectOpenHashMap<Holder<Biome>> biomeCache = new Long2ObjectOpenHashMap();
      final Sampler climate = NoiseTerrain.this.randomState.createClimateSampler(this.context);
      final long[] climateKeys = NoiseTerrain.filled(new long[4096]);
      final float[] climateValues = new float[20480];
      final BiomeResolver sourceBiomes = NoiseTerrain.this.climateColumns ? this::columnBiome : NoiseTerrain.this.biomeSource.createResolver(this.climate);
      final BiomeResolver localBiomes = NoiseTerrain.this.biomeTies != null && !NoiseTerrain.this.climateColumns ? (qx, qy, qz) -> {
         NoiseTerrain.this.biomeTies.remove();
         return this.sourceBiomes.getNoiseBiome(qx, qy, qz);
      } : this.sourceBiomes;
      final Long2ObjectOpenHashMap<Object> tieCandidates = new Long2ObjectOpenHashMap();
      @Nullable BiomeGrid grid;
      final BiomeResolver rawBiomes = (qx, qy, qz) -> {
         Holder<Biome> local = this.localBiomes.getNoiseBiome(qx, qy, qz);
         BiomeGrid current = this.grid;
         if (current == null) {
            return local;
         } else {
            int index = current.index(qx, qz);
            if (index < 0) {
               return local;
            } else {
               Holder<Biome> server = current.biome(index);
               if (server != null && server != local) {
                  int surfaceY = current.quartY(index);
                  Holder<Biome> surface = surfaceY == qy ? local : this.localBiomes.getNoiseBiome(qx, surfaceY, qz);
                  return surface == local ? server : local;
               } else {
                  return local;
               }
            }
         }
      };
      final BiomeResolver cachedBiomes = (qx, qy, qz) -> {
         long key = BlockPos.asLong(qx, qy, qz);
         NoiseTerrain.BiomeTable shared = NoiseTerrain.this.sharedBiomes;
         if (shared != null) {
            Holder<Biome> biome = shared.get(qx, qy, qz);
            if (biome == null) {
               biome = this.localBiomes.getNoiseBiome(qx, qy, qz);
               shared.put(qx, qy, qz, biome);
            }

            return biome;
         } else {
            Holder<Biome> biome = (Holder<Biome>)this.biomeCache.get(key);
            if (biome == null) {
               biome = this.rawBiomes.getNoiseBiome(qx, qy, qz);
               if (this.biomeCache.size() > 262144) {
                  this.biomeCache.clear();
               }

               this.biomeCache.put(key, biome);
            }

            return biome;
         }
      };
      final BiomeManager biomeManager = new ZoomBiomes(this.cachedBiomes, NoiseTerrain.this.zoomSeed);
      final @Nullable Bound minSurfaceLattice = NoiseTerrain.this.minSurfaceInput == null ? null : this.samplers.get(NoiseTerrain.this.minSurfaceInput);
      final float[] minSurface = new float[4356];
      boolean minSurfaceReady;
      final int height = NoiseTerrain.this.maxY - NoiseTerrain.this.minY;
      final float[] profile = new float[4356 * (this.height + 1)];
      final float[] estimates = new float[4356];
      final int[] top = new int[4356];
      final int[] bottom = new int[4356];
      final int[] cut = new int[4356];
      final int[] profileLow = new int[4356];
      final int[] runs = new int[69696];
      final int[] runCount = new int[4356];
      final boolean[] scanSolid = new boolean[4356];
      final boolean[] scanSkip = new boolean[4356];
      final int[] scanLimit = new int[4356];
      final float[] scanAbove = new float[4356];
      final int[] scanTop = new int[4356];
      final int[] fluidTop = new int[4356];
      boolean shallow;
      final int[] startsX = new int[67];
      final int[] startsZ = new int[67];
      final long[] column = new long[12];
      final SurfaceColumn surfaceColumn = new SurfaceColumn(NoiseTerrain.this.minY, NoiseTerrain.this.maxY - NoiseTerrain.this.minY);
      final TileBuilder builder = new TileBuilder();

      Object tieCandidate(MultiNoiseBiomeSource source, ThreadLocal<Object> ties, TargetPoint target) {
         long t = target.temperature() >> 10;
         long h = target.humidity() >> 10;
         long c = target.continentalness() >> 10;
         long e = target.erosion() >> 10;
         long d = target.depth() >> 10;
         long w = target.weirdness() >> 10;
         long key = t + 512L & 1023L
            | (h + 512L & 1023L) << 10
            | (c + 512L & 1023L) << 20
            | (e + 512L & 1023L) << 30
            | (d + 512L & 1023L) << 40
            | (w + 512L & 1023L) << 50;
         Object leaf = this.tieCandidates.get(key);
         if (leaf == null) {
            long half = 512L;
            ties.remove();
            source.getNoiseBiome(new TargetPoint((t << 10) + 512L, (h << 10) + 512L, (c << 10) + 512L, (e << 10) + 512L, (d << 10) + 512L, (w << 10) + 512L));
            leaf = ties.get();
            if (leaf == null) {
               leaf = NoiseTerrain.NO_LEAF;
            }

            if (this.tieCandidates.size() >= 65536) {
               this.tieCandidates.clear();
            }

            this.tieCandidates.put(key, leaf);
         }

         return leaf;
      }

      Holder<Biome> columnBiome(int qx, int qy, int qz) {
         int bx = QuartPos.toBlock(qx);
         int by = QuartPos.toBlock(qy);
         int bz = QuartPos.toBlock(qz);
         long key = (long)qx << 32 | qz & 4294967295L;
         int slot = (int)(key * -7046029254386353131L >>> 52);
         int base = slot * 5;
         if (this.climateKeys[slot] != key) {
            this.climateValues[base] = this.climate.temperature().sampleValue(bx, by, bz);
            this.climateValues[base + 1] = this.climate.humidity().sampleValue(bx, by, bz);
            this.climateValues[base + 2] = this.climate.continentalness().sampleValue(bx, by, bz);
            this.climateValues[base + 3] = this.climate.erosion().sampleValue(bx, by, bz);
            this.climateValues[base + 4] = this.climate.weirdness().sampleValue(bx, by, bz);
            this.climateKeys[slot] = key;
         }

         float depth = this.climate.depth().sampleValue(bx, by, bz);
         MultiNoiseBiomeSource source = (MultiNoiseBiomeSource)NoiseTerrain.this.biomeSource;
         TargetPoint target = Climate.target(
            this.climateValues[base],
            this.climateValues[base + 1],
            this.climateValues[base + 2],
            this.climateValues[base + 3],
            depth,
            this.climateValues[base + 4]
         );
         ThreadLocal<Object> ties = NoiseTerrain.this.biomeTies;
         if (ties != null) {
            Object leaf = this.tieCandidate(source, ties, target);
            if (leaf == NoiseTerrain.NO_LEAF) {
               ties.remove();
            } else {
               ties.set(leaf);
            }
         }

         return source.getNoiseBiome(target);
      }
   }
}
