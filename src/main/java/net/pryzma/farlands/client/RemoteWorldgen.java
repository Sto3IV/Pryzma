package net.pryzma.farlands.client;

import com.mojang.serialization.DataResult;
import net.pryzma.farlands.LoaderHooks;
import net.pryzma.farlands.Farlands;
import net.pryzma.farlands.FarlandsSettings;
import net.pryzma.farlands.gen.GenWorld;
import net.pryzma.farlands.net.LimitsPayload;
import net.pryzma.farlands.net.WorldgenPayload;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.HolderLookup.RegistryLookup;
import net.minecraft.core.RegistryAccess.Frozen;
import net.minecraft.core.RegistryAccess.ImmutableRegistryAccess;
import net.minecraft.core.RegistrySynchronization.PackedRegistryEntry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.RegistryDataLoader.NetworkedRegistryData;
import net.minecraft.resources.RegistryDataLoader.RegistryData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.CloseableResourceManager;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.tags.TagNetworkSerialization.NetworkPayload;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess;
import org.jspecify.annotations.Nullable;

public final class RemoteWorldgen {
   private static final int LOCAL_DELAY_TICKS = 100;
   private static @Nullable Object connection;
   private static RemoteWorldgen.@Nullable Data data;
   private static volatile int version;
   private static int ticksConnected;
   private static int session = -1;
   private static byte[][] parts;
   private static int received;
   private static int generation;
   private static boolean localStarted;
   private static String localSeedText = "";
   private static boolean serverDataPending;

   private RemoteWorldgen() {
   }

   public static int version() {
      return version;
   }

   public static boolean fromServer() {
      RemoteWorldgen.Data current = data;
      return current != null && current.fromServer();
   }

   public static boolean active() {
      return data != null;
   }

   public static String cacheTag() {
      RemoteWorldgen.Data current = data;
      return current == null
         ? ""
         : "_"
            + Long.toHexString(Long.rotateLeft(current.seed() * -7046029254386353131L, 17) & 4294967295L)
            + (current.fromServer() ? "s" : "l")
            + (current.biomeService() ? "b" : "");
   }

   public static @Nullable GenWorld forLevel(Level level) {
      RemoteWorldgen.Data current = data;
      if (current == null) {
         return null;
      }

      RemoteWorldgen.Generator generator = current.generators().get(level.dimension());
      if (generator == null) {
         return null;
      }

      GenWorld world = GenWorld.remote(level, current.registries(), generator.seed(), generator.generator(), generator.structures(), current.templates());
      if (current.biomeService()) {
         world.serverBiomes = new RemoteBiomes(
            level.dimension().identifier().toString(),
            current.registries().lookupOrThrow(Registries.BIOME),
            generator.generator().getBiomeSource().possibleBiomes()
         );
      }

      return world;
   }

   private static @Nullable ClientPacketListener syncConnection(Minecraft mc) {
      ClientPacketListener listener = mc.getConnection();
      if (listener != connection) {
         reset();
         connection = listener;
         ticksConnected = 0;
      }

      return listener;
   }

   public static void tick(Minecraft mc) {
      ClientPacketListener listener = syncConnection(mc);
      if (listener != null && mc.getSingleplayerServer() == null) {
         ticksConnected++;
         String seedText = FarlandsSettings.serverSeed.trim();
         RemoteWorldgen.Data current = data;
         if (current == null || !current.fromServer()) {
            if (current != null && !seedText.equals(localSeedText)) {
               install(null);
               localStarted = false;
            }

            if (!seedText.isEmpty() && !localStarted && !serverDataPending && ticksConnected >= 100) {
               OptionalLong seed = WorldOptions.parseSeed(seedText);
               if (!seed.isEmpty()) {
                  localStarted = true;
                  localSeedText = seedText;
                  Farlands.LOG.info("Distant terrain: loading the installed world generation for the configured seed");
                  int expected = generation;
                  Path gameDir = mc.gameDirectory.toPath();
                  CompletableFuture.<RemoteWorldgen.Data>supplyAsync(() -> loadLocal(seed.getAsLong(), gameDir), Util.backgroundExecutor())
                     .whenComplete(
                        (loaded, error) -> mc.execute(
                           () -> {
                              if (error != null) {
                                 Farlands.LOG.warn("Distant terrain from the configured seed is unavailable", error);
                              } else if (expected == generation
                                 && (data == null || !data.fromServer())
                                 && localSeedText.equals(FarlandsSettings.serverSeed.trim())) {
                                 install(loaded);
                                 Farlands.LOG
                                    .info(
                                       "Distant terrain uses the configured seed and the installed world generation ({} dimensions)",
                                       loaded.generators().size()
                                    );
                              } else {
                                 loaded.resources().close();
                              }
                           }
                        )
                     );
               }
            }
         }
      }
   }

   public static void receive(WorldgenPayload payload) {
      Minecraft mc = Minecraft.getInstance();
      if (syncConnection(mc) != null && mc.getSingleplayerServer() == null) {
         if (payload.session() != session || parts == null || parts.length != payload.total()) {
            session = payload.session();
            parts = new byte[Math.max(1, payload.total())][];
            received = 0;
         }

         if (payload.index() >= 0 && payload.index() < parts.length && parts[payload.index()] == null) {
            parts[payload.index()] = payload.data();
            received++;
            serverDataPending = true;
            if (received >= parts.length) {
               ByteArrayOutputStream joined = new ByteArrayOutputStream();

               for (byte[] part : parts) {
                  joined.writeBytes(part);
               }

               parts = null;
               byte[] bytes = joined.toByteArray();
               Frozen context = mc.getConnection().registryAccess();
               Path gameDir = mc.gameDirectory.toPath();
               int expected = generation;
               long start = System.nanoTime();
               CompletableFuture.<RemoteWorldgen.Data>supplyAsync(() -> decode(bytes, context, gameDir), Util.backgroundExecutor())
                  .whenComplete(
                     (loaded, error) -> mc.execute(
                        () -> {
                           serverDataPending = false;
                           if (error != null) {
                              Farlands.LOG.warn("World generation data from the server could not be read, distant terrain uses explored chunks only", error);
                           } else if (expected != generation) {
                              loaded.resources().close();
                           } else {
                              install(loaded);
                              Farlands.LOG
                                 .info(
                                    "Distant terrain uses the world generation shared by the server ({} KB, {} dimensions, {} ms)",
                                    new Object[]{bytes.length / 1024, loaded.generators().size(), (System.nanoTime() - start) / 1000000L}
                                 );
                           }
                        }
                     )
                  );
            }
         }
      }
   }

   public static void receiveLimits(LimitsPayload payload) {
      Minecraft mc = Minecraft.getInstance();
      if (syncConnection(mc) != null && mc.getSingleplayerServer() == null) {
         FarlandsSettings.limitLod(Math.max(16, payload.maxLodDistance()));
         Farlands.LOG.info("Distant terrain: this server allows up to {} LOD chunks", payload.maxLodDistance());
      }
   }

   private static void reset() {
      if (FarlandsSettings.serverLodLimit != Integer.MAX_VALUE) {
         FarlandsSettings.limitLod(Integer.MAX_VALUE);
      }

      generation++;
      session = -1;
      parts = null;
      received = 0;
      localStarted = false;
      localSeedText = "";
      serverDataPending = false;
      install(null);
   }

   private static void install(RemoteWorldgen.@Nullable Data next) {
      RemoteWorldgen.Data previous = data;
      if (previous != null || next != null) {
         RemoteBiomes.reset();
         data = next;
         version++;
         if (previous != null) {
            previous.resources().close();
         }
      }
   }

   private static RemoteWorldgen.Data decode(byte[] bytes, Frozen context, Path gameDir) {
      CompoundTag root;
      try {
         root = NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.unlimitedHeap());
      } catch (IOException e) {
         throw new IllegalStateException(e);
      }

      if (root.getIntOr("format", 0) != 1) {
         throw new IllegalStateException("Unsupported world generation data format " + root.getIntOr("format", 0));
      }

      Map<ResourceKey<? extends Registry<?>>, NetworkedRegistryData> entries = new HashMap<>();
      ListTag registries = root.getListOrEmpty("registries");

      for (int r = 0; r < registries.size(); r++) {
         CompoundTag registry = registries.getCompoundOrEmpty(r);
         ResourceKey<? extends Registry<?>> key = ResourceKey.createRegistryKey(Identifier.parse(registry.getStringOr("key", "")));
         ListTag ids = registry.getListOrEmpty("ids");
         ListTag values = registry.getListOrEmpty("data");
         List<PackedRegistryEntry> elements = new ArrayList<>(ids.size());

         for (int i = 0; i < ids.size(); i++) {
            elements.add(new PackedRegistryEntry(Identifier.parse(ids.getStringOr(i, "")), Optional.of(values.get(i))));
         }

         CompoundTag tagData = registry.getCompoundOrEmpty("tags");
         Map<Identifier, IntList> tags = new HashMap<>();

         for (String tag : tagData.keySet()) {
            tags.put(Identifier.parse(tag), IntArrayList.wrap(tagData.getIntArray(tag).orElse(new int[0])));
         }

         entries.put(key, new NetworkedRegistryData(elements, new NetworkPayload(tags)));
      }

      List<RegistryData<?>> toLoad = RegistryDataLoader.WORLD_REGISTRIES.stream().filter(d -> entries.containsKey(d.key())).toList();
      List<RegistryLookup<?>> lookups = new ArrayList<>();
      context.registries().filter(e -> !entries.containsKey(e.key())).forEach(e -> lookups.add(e.value()));
      Frozen loaded = (Frozen)RegistryDataLoader.load(entries, ResourceProvider.EMPTY, lookups, toLoad, Util.backgroundExecutor()).join();
      Map<ResourceKey<? extends Registry<?>>, Registry<?>> all = new LinkedHashMap<>();
      context.registries().forEach(e -> all.put(e.key(), e.value()));
      loaded.registries().forEach(e -> all.put(e.key(), e.value()));
      Frozen combined = new ImmutableRegistryAccess(all).freeze();
      RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, combined);
      Map<ResourceKey<Level>, RemoteWorldgen.Generator> generators = new HashMap<>();
      ListTag dimensions = root.getListOrEmpty("dimensions");

      for (int d = 0; d < dimensions.size(); d++) {
         CompoundTag dimension = dimensions.getCompoundOrEmpty(d);
         String id = dimension.getStringOr("id", "");
         DataResult<ChunkGenerator> parsed = ChunkGenerator.CODEC.parse(ops, dimension.get("generator"));
         if (parsed.isError()) {
            Farlands.LOG.warn("Distant terrain for {} is unavailable: {}", id, parsed.error().map(Object::toString).orElse("?"));
         } else {
            generators.put(
               ResourceKey.create(Registries.DIMENSION, Identifier.parse(id)),
               new RemoteWorldgen.Generator(
                  (ChunkGenerator)parsed.getOrThrow(), dimension.getLongOr("seed", root.getLongOr("seed", 0L)), dimension.getBooleanOr("structures", true)
               )
            );
         }
      }

      List<String> packs = new ArrayList<>();
      ListTag packList = root.getListOrEmpty("packs");

      for (int i = 0; i < packList.size(); i++) {
         packs.add(packList.getStringOr(i, ""));
      }

      RemoteWorldgen.Resources resources = new RemoteWorldgen.Resources();
      StructureTemplateManager templates = templates(resources, packs, combined, gameDir);
      return new RemoteWorldgen.Data(combined, root.getLongOr("seed", 0L), generators, templates, resources, true, root.getBooleanOr("biomes", false));
   }

   private static RemoteWorldgen.Data loadLocal(long seed, Path gameDir) {
      RemoteWorldgen.Resources resources = new RemoteWorldgen.Resources();

      try {
         PackRepository repository = ServerPacksSource.createVanillaTrustedRepository();
         MinecraftServer.configurePackRepository(repository, WorldDataConfiguration.DEFAULT, true, false);
         CloseableResourceManager manager = new MultiPackResourceManager(PackType.SERVER_DATA, repository.openAllSelected());
         resources.manager = manager;
         List<RegistryLookup<?>> context = RegistryLayer.createRegistryAccess().getAccessForLoading(RegistryLayer.WORLD).listRegistries().toList();
         Frozen worldgen = (Frozen)RegistryDataLoader.load(manager, context, LoaderHooks.worldRegistries.get(), Util.backgroundExecutor()).join();
         List<RegistryLookup<?>> dimensionContext = Stream.concat(context.stream(), worldgen.listRegistries()).toList();
         Frozen loadedDimensions = (Frozen)RegistryDataLoader.load(
               manager, dimensionContext, RegistryDataLoader.DIMENSION_REGISTRIES, Util.backgroundExecutor()
            )
            .join();
         WorldDimensions preset = WorldPresets.createNormalWorldDimensions(Provider.create(dimensionContext.stream()));
         Registry<LevelStem> stems = preset.bake(loadedDimensions.lookupOrThrow(Registries.LEVEL_STEM)).dimensions();
         Map<ResourceKey<? extends Registry<?>>, Registry<?>> all = new LinkedHashMap<>();
         RegistryLayer.createRegistryAccess().getAccessForLoading(RegistryLayer.WORLD).registries().forEach(ex -> all.put(ex.key(), ex.value()));
         worldgen.registries().forEach(ex -> all.put(ex.key(), ex.value()));
         all.put(Registries.LEVEL_STEM, stems);
         Frozen combined = new ImmutableRegistryAccess(all).freeze();

         try {
            LoaderHooks.biomeModifiers.accept(combined);
         } catch (RuntimeException e) {
            Farlands.LOG.warn("Biome changes of installed mods could not be applied to distant terrain", e);
         }

         Map<ResourceKey<Level>, RemoteWorldgen.Generator> generators = new HashMap<>();
         stems.listElements()
            .forEach(
               stem -> generators.put(
                  ResourceKey.create(Registries.DIMENSION, stem.key().identifier()),
                  new RemoteWorldgen.Generator(((LevelStem)stem.value()).generator(), seed, true)
               )
            );
         StructureTemplateManager templates = templates(resources, null, combined, gameDir);
         return new RemoteWorldgen.Data(combined, seed, generators, templates, resources, false, false);
      } catch (RuntimeException e) {
         resources.close();
         throw e;
      }
   }

   private static @Nullable StructureTemplateManager templates(
      RemoteWorldgen.Resources resources, @Nullable List<String> packs, RegistryAccess registries, Path gameDir
   ) {
      try {
         CloseableResourceManager manager = resources.manager;
         if (manager == null) {
            PackRepository repository = ServerPacksSource.createVanillaTrustedRepository();
            repository.reload();
            List<String> selected = packs == null ? List.of() : packs.stream().filter(repository::isAvailable).toList();
            if (selected.isEmpty()) {
               MinecraftServer.configurePackRepository(repository, WorldDataConfiguration.DEFAULT, true, false);
            } else {
               repository.setSelected(selected);
            }

            manager = new MultiPackResourceManager(PackType.SERVER_DATA, repository.openAllSelected());
            resources.manager = manager;
         }

         resources.storage = LevelStorageSource.createDefault(gameDir.resolve("pryzma").resolve("templates")).createAccess("remote");
         return new StructureTemplateManager(manager, resources.storage, DataFixers.getDataFixer(), registries.lookupOrThrow(Registries.BLOCK));
      } catch (Exception e) {
         Farlands.LOG.warn("Distant structures are unavailable", e);
         return null;
      }
   }

   private record Data(
      Frozen registries,
      long seed,
      Map<ResourceKey<Level>, RemoteWorldgen.Generator> generators,
      @Nullable StructureTemplateManager templates,
      RemoteWorldgen.Resources resources,
      boolean fromServer,
      boolean biomeService
   ) {
   }

   private record Generator(ChunkGenerator generator, long seed, boolean structures) {
   }

   private static final class Resources implements AutoCloseable {
      @Nullable CloseableResourceManager manager;
      LevelStorageAccess/* @Nullable */ storage;

      @Override
      public void close() {
         if (this.manager != null) {
            this.manager.close();
         }

         if (this.storage != null) {
            try {
               this.storage.close();
            } catch (Exception e) {
               Farlands.LOG.debug("Template storage close failed", e);
            }
         }
      }
   }
}
