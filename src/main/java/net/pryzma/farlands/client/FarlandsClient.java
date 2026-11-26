package net.pryzma.farlands.client;

import com.mojang.renderpearl.api.commands.RenderPass;
import net.pryzma.farlands.Farlands;
import net.pryzma.farlands.FarlandsSettings;
import net.pryzma.PryzmaConfig;
import net.pryzma.farlands.client.iris.IrisLodPath;
import net.pryzma.farlands.client.iris.IrisState;
import net.pryzma.farlands.gen.BaseColumnTerrain;
import net.pryzma.farlands.gen.BiomeIds;
import net.pryzma.farlands.gen.GenWorld;
import net.pryzma.farlands.gen.NoiseTerrain;
import net.pryzma.farlands.gen.TerrainSource;
import net.pryzma.farlands.gen.Vegetation;
import net.pryzma.farlands.lod.ChunkSupplier;
import net.pryzma.farlands.lod.LodTile;
import net.pryzma.farlands.lod.LodWorld;
import net.pryzma.farlands.lod.TileCache;
import net.pryzma.farlands.work.WorkerPool;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Util;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelData.RespawnData;
import org.jspecify.annotations.Nullable;

public final class FarlandsClient {
   private static @Nullable FarlandsClient instance;
   private final WorkerPool pool;
   private final SpriteTable sprites;
   private final BlockLooks looks;
   private final LodRenderer renderer;
   private final LongOpenHashSet loadedChunks;
   private final VanillaCoverage coverage;
   private static final String GENERATOR_VERSION = "g10";
   private long lastFlush;
   private int scanTicks;
   private int stuckTicks;
   private volatile boolean flushQueued;
   private long prepareNanos;
   private long prepareNanosMax;
   private int prepareCount;
   private long prepareWindowStart;
   private double lastPrepareAverage;
   private double lastPrepareMax;
   private @Nullable ClientLevel level;
   private @Nullable LodWorld world;
   private @Nullable BiomeIds clientBiomes;
   private int settingsVersion;
   private String generationKey;
   private int worldgenVersion;
   private int threads;
   private long cameraChunk;
   private boolean prepared;
   private final @Nullable ShaderPath shaderPath;
   private float shaderNear;
   private boolean solidDrawn;
   private boolean fluidDrawn;
   private static final int WARM_TILES = 192;
   private static final int WARM_SURVEY_TILES = 4;
   private static final double WARM_REACH = 256.0;
   private static final long WARM_KEEP_MILLIS = 60000L;
   private final Map<Long, LodTile> warmTiles;
   private @Nullable MinecraftServer warmServer;
   private boolean warmTried;
   private boolean warmRing;
   private volatile @Nullable Object warmToken;
   private @Nullable TerrainSource warmSource;
   private @Nullable ResourceKey<Level> warmDimension;
   private @Nullable Registry<Biome> warmBiomes;
   private boolean warmVegetation;

   private FarlandsClient() {
      this.pool = new WorkerPool("Farlands", FarlandsSettings.threads);
      this.sprites = new SpriteTable();
      this.looks = new BlockLooks(this.sprites);
      this.renderer = new LodRenderer(this.pool, this.looks, this.sprites);
      this.loadedChunks = new LongOpenHashSet();
      this.coverage = new VanillaCoverage();
      this.prepareWindowStart = System.nanoTime();
      this.settingsVersion = -1;
      this.generationKey = "";
      this.threads = FarlandsSettings.threads;
      this.cameraChunk = Long.MIN_VALUE;
      this.shaderPath = createShaderPath();
      this.shaderNear = 16.0F;
      this.warmTiles = new ConcurrentHashMap<>();
      this.renderer.setShaderPath(this.shaderPath);
      net.pryzma.Pryzma.LOGGER.info("Farlands initialized: {} worker threads at priority 2 (budget mode: {})",
              this.threads, PryzmaConfig.prFarlandsThreads == 0 ? "Sync" : "Manual");
   }

   private static @Nullable ShaderPath createShaderPath() {
      ClassLoader loader = FarlandsClient.class.getClassLoader();
      if (loader.getResource("net/irisshaders/iris/api/v0/IrisApi.class") == null) {
         return null;
      }

      if (loader.getResource("com/seibel/distanthorizons/api/DhApi.class") != null) {
         return null;
      }

      try {
         return new IrisLodPath();
      } catch (LinkageError | RuntimeException e) {
         Farlands.LOG.warn("Shader pack support unavailable", e);
         return null;
      }
   }

   public static FarlandsClient get() {
      if (instance == null) {
         instance = new FarlandsClient();
      }

      return instance;
   }

   public static @Nullable FarlandsClient peek() {
      return instance;
   }

   public LodRenderer renderer() {
      return this.renderer;
   }

   public @Nullable LodWorld world() {
      return this.world;
   }

   public BlockLooks looks() {
      return this.looks;
   }

   public @Nullable BiomeIds clientBiomes() {
      return this.clientBiomes;
   }

   public WorkerPool pool() {
      return this.pool;
   }

   public void tick() {
      Minecraft mc = Minecraft.getInstance();
      ClientLevel current = mc.level;
      RemoteWorldgen.tick(mc);
      if (current == null) {
         this.warm(mc);
      }

      if (current != this.level) {
         this.switchLevel(current);
         this.worldgenVersion = RemoteWorldgen.version();
      } else if (this.worldgenVersion != RemoteWorldgen.version()) {
         this.worldgenVersion = RemoteWorldgen.version();
         if (this.level != null) {
            this.switchLevel(this.level, true);
         }
      }

      if (this.shaderPath != null) {
         this.shaderPath.enabledChanged(FarlandsSettings.enabled);
      }

      if (current != null) {
         this.renderer.probePipelines();
      }

      if (++this.stuckTicks >= 200) {
         this.stuckTicks = 0;
         this.pool.reportStuck();
      }

      this.settle();
      if (this.settingsVersion != FarlandsSettings.version()) {
         boolean firstApply = this.settingsVersion == -1;
         this.settingsVersion = FarlandsSettings.version();
         if (this.threads != FarlandsSettings.threads) {
            this.threads = FarlandsSettings.threads;
            this.pool.resize(this.threads);
            net.pryzma.Pryzma.LOGGER.info("Farlands worker pool resized to {} threads (budget mode: {})",
                    this.threads, PryzmaConfig.prFarlandsThreads == 0 ? "Sync" : "Manual");
         }

         String generation = FarlandsSettings.generate + "|" + FarlandsSettings.vegetation;
         boolean regenerate = !firstApply && !generation.equals(this.generationKey);
         this.generationKey = generation;
         if (regenerate && this.level != null) {
            this.switchLevel(this.level, true);
         }
      }
   }

   private void settle() {
      long now = Util.getMillis();
      ClientLevel current = this.level;
      if (current != null) {
         this.reconcile(current);
         this.coverage.update(Minecraft.getInstance(), current, this.loadedChunks);
      }

      LodWorld w = this.world;
      if (w != null && w.hasDirty() && !this.flushQueued && now - this.lastFlush >= 1000L) {
         this.lastFlush = now;
         this.flushQueued = true;
         this.pool.submit(-200.0, () -> {
            try {
               w.flushReal();
            } finally {
               this.flushQueued = false;
            }
         }, () -> this.flushQueued = false);
      }
   }

   private void reconcile(ClientLevel level) {
      if (++this.scanTicks >= 20) {
         this.scanTicks = 0;
         if (this.world != null && this.clientBiomes != null) {
            Minecraft mc = Minecraft.getInstance();
            Entity viewer = mc.getCameraEntity();
            if (viewer != null) {
               ClientChunkCache source = level.getChunkSource();
               int radius = mc.options.getEffectiveRenderDistance() + 2;
               int cx = SectionPos.blockToSectionCoord(viewer.getBlockX());
               int cz = SectionPos.blockToSectionCoord(viewer.getBlockZ());

               for (int dz = -radius; dz <= radius; dz++) {
                  for (int dx = -radius; dx <= radius; dx++) {
                     if (!this.loadedChunks.contains(ChunkPos.pack(cx + dx, cz + dz))) {
                        LevelChunk chunk = source.getChunkNow(cx + dx, cz + dz);
                        if (chunk != null) {
                           this.onChunkLoad(chunk);
                        }
                     }
                  }
               }

               LongArrayList gone = new LongArrayList();
               LongIterator it = this.loadedChunks.iterator();

               while (it.hasNext()) {
                  long key = it.nextLong();
                  if (source.getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key)) == null) {
                     gone.add(key);
                  }
               }

               for (int i = 0; i < gone.size(); i++) {
                  long key = gone.getLong(i);
                  this.loadedChunks.remove(key);
                  this.coverage.unloaded(key);
               }
            }
         }
      }
   }

   private void switchLevel(@Nullable ClientLevel next) {
      this.switchLevel(next, false);
   }

   private void switchLevel(@Nullable ClientLevel next, boolean force) {
      if (force || next != this.level) {
         if (this.world != null) {
            this.world.close();
         }

         this.pool.clear();
         this.world = null;
         this.level = next;
         this.renderer.setWorld(null, null);
         this.loadedChunks.clear();
         this.flushQueued = false;
         if (next == null || !force) {
            this.coverage.clear();
         }

         if (next == null) {
            this.renderer.releaseGpu();
         } else {
            Registry<Biome> biomes = next.registryAccess().lookupOrThrow(Registries.BIOME);
            this.clientBiomes = new BiomeIds(biomes);
            TerrainSource source = force ? null : this.adoptWarm(next, biomes);
            boolean adopted = source != null;
            if (source == null) {
               source = this.createSource(next, biomes);
            }

            this.world = new LodWorld(this.pool, source, this.createSaved(next, biomes), this.createCache(next));
            if (adopted) {
               this.world.warm(this.warmTiles, 60000L);
            }

            this.renderer.setWorld(this.world, biomes, next.getMinY());
            this.renderer.markMaskDirty();
         }
      }
   }

   private void warm(Minecraft mc) {
      MinecraftServer server = mc.getSingleplayerServer();
      if (server != this.warmServer) {
         this.dropWarm();
         this.warmServer = server;
         this.warmTried = false;
         this.warmRing = false;
      }

      if (server != null && FarlandsSettings.enabled && FarlandsSettings.generate) {
         try {
            if (!this.warmTried) {
               this.startWarm(server);
            }

            if (this.warmSource != null && !this.warmRing) {
               this.warmRing(mc, server);
            }
         } catch (RuntimeException e) {
            this.warmTried = true;
            this.warmRing = true;
            this.dropWarm();
            Farlands.LOG.debug("Early distant terrain start skipped", e);
         }
      }
   }

   private void startWarm(MinecraftServer server) {
      ServerLevel level = server.overworld();
      if (level != null) {
         this.warmTried = true;
         if (!level.dimensionType().hasCeiling()) {
            Registry<Biome> biomes = server.registryAccess().lookupOrThrow(Registries.BIOME);
            TerrainSource source = this.createSource(GenWorld.of(level), level.dimension(), biomes);
            if (source != null) {
               Object token = new Object();
               this.warmToken = token;
               this.warmSource = source;
               this.warmDimension = level.dimension();
               this.warmBiomes = biomes;
               this.warmVegetation = FarlandsSettings.vegetation;
               if (source instanceof NoiseTerrain noise) {
                  this.pool.submit(-3000.0, () -> {
                     if (this.warmToken == token) {
                        for (int biome : noise.catalogBiomes(4)) {
                           if (!noise.catalogReady(biome)) {
                              this.pool.submit(-2000.0, () -> {
                                 if (this.warmToken == token) {
                                    noise.buildCatalog(biome);
                                 }
                              });
                           }
                        }
                     }
                  });
               }
            }
         }
      }
   }

   private void warmRing(Minecraft mc, MinecraftServer server) {
      if (server.getWorldData().overworldData().isInitialized()) {
         this.warmRing = true;
         RespawnData spawn = server.getRespawnData();
         TerrainSource source = this.warmSource;
         Object token = this.warmToken;
         if (source != null && token != null && spawn.dimension().equals(this.warmDimension)) {
            int size = 64;
            double cx = spawn.pos().getX() + 0.5;
            double cz = spawn.pos().getZ() + 0.5;
            double inner = Math.max(0, mc.options.getEffectiveRenderDistance() - 1) * 16.0;
            double outer = inner + 16.0 + 256.0;
            List<double[]> ring = new ArrayList<>();

            for (int tz = (int)Math.floor((cz - outer) / 64.0); tz <= (int)Math.floor((cz + outer) / 64.0); tz++) {
               for (int tx = (int)Math.floor((cx - outer) / 64.0); tx <= (int)Math.floor((cx + outer) / 64.0); tx++) {
                  double x0 = tx * 64.0;
                  double z0 = tz * 64.0;
                  double dx = Math.max(0.0, Math.max(x0 - cx, cx - (x0 + 64.0)));
                  double dz = Math.max(0.0, Math.max(z0 - cz, cz - (z0 + 64.0)));
                  double near = Math.sqrt(dx * dx + dz * dz);
                  if (near >= inner && near <= outer) {
                     ring.add(new double[]{near, tx, tz});
                  }
               }
            }

            ring.sort((a, b) -> Double.compare(a[0], b[0]));

            for (int i = 0; i < Math.min(192, ring.size()); i++) {
               double[] tile = ring.get(i);
               int tx = (int)tile[1];
               int tz = (int)tile[2];
               this.pool.submit(tile[0] / 64.0, () -> {
                  if (this.warmToken == token) {
                     LodTile made = source.generate(0, tx, tz, false);
                     if (made != null && this.warmToken == token) {
                        this.warmTiles.put(LodTile.key(0, tx, tz), made);
                     }
                  }
               });
            }
         }
      }
   }

   private @Nullable TerrainSource adoptWarm(ClientLevel next, Registry<Biome> biomes) {
      TerrainSource source = this.warmSource;
      this.warmSource = null;
      if (source != null
         && Minecraft.getInstance().getSingleplayerServer() == this.warmServer
         && next.dimension().equals(this.warmDimension)
         && this.warmVegetation == FarlandsSettings.vegetation
         && FarlandsSettings.generate
         && sameIds(this.warmBiomes, biomes)) {
         return source;
      }

      this.warmToken = null;
      this.warmTiles.clear();
      if (source != null) {
         source.close();
      }

      return null;
   }

   private void dropWarm() {
      this.warmToken = null;
      this.warmTiles.clear();
      TerrainSource source = this.warmSource;
      this.warmSource = null;
      if (source != null) {
         source.close();
      }
   }

   private static boolean sameIds(@Nullable Registry<Biome> server, Registry<Biome> client) {
      if (server != null && server.size() == client.size()) {
         for (Biome biome : client) {
            Optional<Reference<Biome>> other = server.get(client.getKey(biome));
            if (other.isEmpty() || server.getId((Biome)other.get().value()) != client.getId(biome)) {
               return false;
            }
         }

         return true;
      } else {
         return false;
      }
   }

   private @Nullable TileCache createCache(ClientLevel client) {
      Minecraft mc = Minecraft.getInstance();
      MinecraftServer server = mc.getSingleplayerServer();
      String dimension = client.dimension().identifier().toString().replace(':', '_').replace('/', '_');
      String variant = FarlandsSettings.generate ? (FarlandsSettings.vegetation ? "v1_veg" : "v1_plain") : "v1_real";
      if (server == null && FarlandsSettings.generate) {
         variant = variant + RemoteWorldgen.cacheTag();
      }

      Path root;
      if (server != null) {
         root = server.getWorldPath(LevelResource.ROOT).resolve("pryzma");
      } else {
         ServerData data = mc.getCurrentServer();
         if (data == null) {
            return null;
         }

         root = mc.gameDirectory.toPath().resolve("pryzma").resolve("servers").resolve(data.ip.replaceAll("[^A-Za-z0-9._-]", "_"));
      }

      return new TileCache(root.resolve(dimension).resolve(variant), cacheFingerprint(client));
   }

   private static String cacheFingerprint(ClientLevel client) {
      long hash = -3750763034362895579L;

      for (Block block : BuiltInRegistries.BLOCK) {
         hash = mix(hash, String.valueOf(BuiltInRegistries.BLOCK.getKey(block)).hashCode());
         hash = mix(hash, block.getStateDefinition().getPossibleStates().size());
      }

      Registry<Biome> biomes = client.registryAccess().lookupOrThrow(Registries.BIOME);

      for (Biome biome : biomes) {
         hash = mix(hash, String.valueOf(biomes.getKey(biome)).hashCode());
      }

      return "g10:" + Long.toHexString(hash);
   }

   private static long mix(long hash, int value) {
      return (hash ^ value) * 1099511628211L;
   }

   private @Nullable ChunkSupplier createSaved(ClientLevel client, Registry<Biome> biomes) {
      MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
      if (server == null) {
         return null;
      }

      ServerLevel level = server.getLevel(client.dimension());
      return level == null ? null : new SavedChunks(level, new BiomeIds(biomes), client.dimensionType().hasSkyLight());
   }

   public @Nullable TerrainSource createSource(ClientLevel client, Registry<Biome> biomes) {
      MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
      GenWorld world;
      if (server != null) {
         ServerLevel level = server.getLevel(client.dimension());
         if (level == null || level.dimensionType().hasCeiling()) {
            return null;
         }

         world = GenWorld.of(level);
      } else {
         if (client.dimensionType().hasCeiling()) {
            return null;
         }

         try {
            world = RemoteWorldgen.forLevel(client);
         } catch (RuntimeException e) {
            Farlands.LOG.warn("Distant terrain generation unavailable for {}", client.dimension().identifier(), e);
            return null;
         }

         if (world == null) {
            return null;
         }
      }

      return this.createSource(world, client.dimension(), biomes);
   }

   private @Nullable TerrainSource createSource(GenWorld world, ResourceKey<Level> dimension, Registry<Biome> biomes) {
      ChunkGenerator generator = world.generator;
      BiomeIds ids = new BiomeIds(biomes);

      try {
         if (generator instanceof NoiseBasedChunkGenerator noise) {
            Vegetation vegetation = null;
            if (FarlandsSettings.vegetation) {
               vegetation = Vegetation.create(world, generator, ((NoiseGeneratorSettings)noise.generatorSettings().value()).defaultBlock());
            }

            return new NoiseTerrain(world, noise, ids, vegetation);
         } else {
            return new BaseColumnTerrain(world, generator, ids);
         }
      } catch (RuntimeException e) {
         Farlands.LOG.warn("Distant terrain generation unavailable for {}", dimension.identifier(), e);
         return null;
      }
   }

   public void onChunkLoad(LevelChunk chunk) {
      if (chunk.getLevel() instanceof ClientLevel client && client == this.level && this.world != null && this.clientBiomes != null) {
         long key = ChunkPos.pack(chunk.getPos().x(), chunk.getPos().z());
         this.loadedChunks.add(key);
         this.coverage.loaded();
         ChunkIngest.submit(chunk, this.world, this.pool, this.clientBiomes, client.dimensionType().hasSkyLight());
      }
   }

   public void onChunkUnload(LevelChunk chunk) {
      if (chunk.getLevel() instanceof ClientLevel client && client == this.level) {
         long key = ChunkPos.pack(chunk.getPos().x(), chunk.getPos().z());
         this.loadedChunks.remove(key);
         this.coverage.unloaded(key);
         if (this.world != null && this.clientBiomes != null) {
            ChunkIngest.submit(chunk, this.world, this.pool, this.clientBiomes, client.dimensionType().hasSkyLight());
         }
      }
   }

   public void prepare(CameraRenderState camera) {
      this.prepared = false;
      this.solidDrawn = false;
      this.fluidDrawn = false;
      if (FarlandsSettings.enabled && this.world != null) {
         Minecraft mc = Minecraft.getInstance();
         long chunk = ChunkPos.pack((int)Math.floor(camera.pos.x) >> 4, (int)Math.floor(camera.pos.z) >> 4);
         if (chunk != this.cameraChunk) {
            this.cameraChunk = chunk;
            this.renderer.markMaskDirty();
         }

         int minY = this.level != null ? this.level.getMinY() : -64;
         int vanilla = mc.options.getEffectiveRenderDistance();
         int totalBlocks = (vanilla + FarlandsSettings.lodChunks) * 16;
         IrisState.enabled = true;
         IrisState.near = this.shaderNear;
         IrisState.far = (float)((totalBlocks + 512) * Math.sqrt(2.0));
         IrisState.renderBlocks = totalBlocks;
         long start = System.nanoTime();
         this.renderer.prepare(camera.pos, camera.cullFrustum, camera.viewRotationMatrix, vanilla, this.coverage, minY);
         long took = System.nanoTime() - start;
         this.prepareNanos += took;
         this.prepareNanosMax = Math.max(this.prepareNanosMax, took);
         this.prepareCount++;
         if (start - this.prepareWindowStart > 1000000000L) {
            this.lastPrepareAverage = this.prepareCount == 0 ? 0.0 : this.prepareNanos / 1000000.0 / this.prepareCount;
            this.lastPrepareMax = this.prepareNanosMax / 1000000.0;
            this.prepareNanos = 0L;
            this.prepareNanosMax = 0L;
            this.prepareCount = 0;
            this.prepareWindowStart = start;
         }

         if (this.renderer.shaderMode() && this.shaderPath != null) {
            this.shaderPath.frame(this.shaderNear, IrisState.far);
         }

         this.prepared = true;
      } else {
         IrisState.enabled = false;
      }
   }

   public void render(RenderPass pass) {
      if (this.prepared && FarlandsSettings.enabled && !this.renderer.shaderMode()) {
         this.renderer.render(pass, !Minecraft.getInstance().gameRenderer.useImprovedTransparency());
      }
   }

   public void renderFluid(RenderPass pass) {
      if (this.prepared && FarlandsSettings.enabled) {
         this.renderer.renderFluid(pass);
      }
   }

   public void irisPhase(int phase) {
      if (this.prepared && FarlandsSettings.enabled && this.renderer.shaderMode()) {
         if (phase == 1 && !this.solidDrawn) {
            this.solidDrawn = true;
            this.renderer.renderShader(false, this.shaderNear);
         } else if (phase == 2 && !this.fluidDrawn) {
            this.fluidDrawn = true;
            this.renderer.renderShader(true, this.shaderNear);
         }
      }
   }

   public void onVanillaReset() {
      this.coverage.clear();
      this.renderer.markMaskDirty();
   }

   public void onResourcesReloaded() {
      this.sprites.clear();
      this.looks.clear();
      this.renderer.clearMeshes();
   }

   public float extendFar(float vanillaFar) {
      if (FarlandsSettings.enabled && this.world != null) {
         int vanilla = Minecraft.getInstance().options.getEffectiveRenderDistance();
         return Math.max(vanillaFar, (vanilla + FarlandsSettings.lodChunks) * 16.0F * 1.5F + 512.0F);
      } else {
         return vanillaFar;
      }
   }

   public void adjustFog(FogData fog, FogType type, boolean atmospheric) {
      if (FarlandsSettings.enabled && this.world != null && (type == FogType.ATMOSPHERIC || type == FogType.NONE) && atmospheric) {
         float vanilla = Minecraft.getInstance().options.getEffectiveRenderDistance() * 16.0F;
         SkyFog.remember(fog);

         if (PryzmaConfig.isFogOff()) {
            if (!FarlandsSettings.fog) {
               fog.renderDistanceStart = 1000000.0F;
               fog.renderDistanceEnd = 1000001.0F;
               fog.environmentalStart = 1000000.0F;
               fog.environmentalEnd = 1000001.0F;
               SkyFog.legacy = false;
               return;
            } else {
               float total = vanilla + FarlandsSettings.lodChunks * 16.0F;
               fog.renderDistanceStart = Math.max(vanilla, total * 0.9F);
               fog.renderDistanceEnd = total;
               fog.environmentalStart = Math.max(vanilla, total * 0.9F);
               fog.environmentalEnd = total;
               SkyFog.legacy = false;
               return;
            }
         }

         float total = vanilla + FarlandsSettings.lodChunks * 16.0F;
         boolean foreign = fog.renderDistanceEnd < vanilla - 0.5F
            || fog.environmentalEnd < (Float)EnvironmentAttributes.FOG_END_DISTANCE.defaultValue() - 256.0F;
         boolean legacy = !FarlandsSettings.fog || this.renderer.shaderMode() || FarlandsSettings.scaleModFog && foreign;
         SkyFog.legacy = legacy;
         if (fog.renderDistanceEnd < vanilla - 0.5F) {
            if (FarlandsSettings.fog && legacy) {
               float scale = total / Math.max(1.0F, vanilla);
               fog.renderDistanceStart *= scale;
               fog.renderDistanceEnd *= scale;
            }
         } else if (!legacy) {
            if (fog.renderDistanceEnd < total) {
               fog.renderDistanceStart = Math.max(fog.renderDistanceStart, Math.max(vanilla, total * 0.9F));
               fog.renderDistanceEnd = total;
            }
         } else {
            float span = Math.max(32.0F, total * 0.08F);
            fog.renderDistanceStart = total - span;
            fog.renderDistanceEnd = total;
            if (FarlandsSettings.fog) {
               if (fog.environmentalEnd >= vanilla * 0.9F) {
                  float scale = total / Math.max(1.0F, vanilla);
                  fog.environmentalStart *= scale;
                  fog.environmentalEnd *= scale;
               }
            } else if (fog.environmentalEnd >= vanilla * 0.9F) {
               fog.environmentalStart = Math.max(fog.environmentalStart, total);
               fog.environmentalEnd = Math.max(fog.environmentalEnd, total * 2.0F);
            }
         }
      } else {
         SkyFog.legacy = false;
      }
   }

   public String debugLine() {
      LodWorld w = this.world;
      if (w == null) {
         return "Farlands: off";
      }

      long generated = w.generatedCount();
      double ms = generated == 0L ? 0.0 : w.generationNanos() / 1000000.0 / generated;
      return String.format(
         Locale.ROOT,
         "Farlands: %d tiles, %d meshes, %d drawn, %dk quads, %d queued, %.1f ms/tile, %d MB GPU, frame %.2f ms avg %.2f ms max",
         w.tileCount(),
         this.renderer.meshCount(),
         this.renderer.drawCount(),
         this.renderer.drawnQuads() / 1000L,
         this.pool.queued(),
         ms,
         this.renderer.gpuBytes() >> 20,
         this.lastPrepareAverage,
         this.lastPrepareMax
      );
   }
}
