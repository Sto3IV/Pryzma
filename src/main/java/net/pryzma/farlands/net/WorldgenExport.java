package net.pryzma.farlands.net;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.DataResult;
import net.pryzma.farlands.LoaderHooks;
import net.pryzma.farlands.Farlands;
import net.pryzma.farlands.FarlandsServerConfig;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.RegistryDataLoader.RegistryData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;

public final class WorldgenExport {
   public static final int FORMAT = 1;
   public static final Set<ResourceKey<? extends Registry<?>>> KEYS = Set.of(
      Registries.BIOME,
      Registries.CARVER,
      Registries.FEATURE,
      Registries.PLACED_FEATURE,
      Registries.STRUCTURE,
      Registries.STRUCTURE_SET,
      Registries.PROCESSOR_LIST,
      Registries.TEMPLATE_POOL,
      Registries.NOISE_SETTINGS,
      Registries.NOISE,
      Registries.DENSITY_FUNCTION,
      Registries.MATERIAL_RULE,
      Registries.MATERIAL_CONDITION,
      Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST
   );
   private static final Map<MinecraftServer, CompletableFuture<List<WorldgenPayload>>> CACHE = new WeakHashMap<>();
   private static final AtomicInteger SESSIONS = new AtomicInteger((int)(System.nanoTime() & 65535L));

   private WorldgenExport() {
   }

   public static void onJoin(ServerPlayer player) {
      MinecraftServer server = player.level().getServer();
      if (server != null) {
         GameProfile owner = server.getSingleplayerProfile();
         if (owner == null || !owner.id().equals(player.getUUID())) {
            if (LoaderHooks.canReceive.test(player, LimitsPayload.TYPE)) {
               LoaderHooks.sender.accept(player, new LimitsPayload(FarlandsServerConfig.maxLodDistance(), FarlandsServerConfig.tilesPerSecond()));
            }

            if (FarlandsServerConfig.share() && LoaderHooks.canReceive.test(player, WorldgenPayload.TYPE)) {
               prepare(server).thenAcceptAsync(parts -> {
                  if (!player.hasDisconnected()) {
                     for (WorldgenPayload part : parts) {
                        PlayerUpload.send(server, player, part, part.data().length + 16);
                     }
                  }
               }, server).exceptionally(e -> {
                  Farlands.LOG.warn("Could not send world generation data to {}", player.getName().getString(), e);
                  return null;
               });
            }
         }
      }
   }

   public static void onStopped(MinecraftServer server) {
      synchronized (CACHE) {
         CACHE.remove(server);
      }
   }

   private static CompletableFuture<List<WorldgenPayload>> prepare(MinecraftServer server) {
      synchronized (CACHE) {
         CompletableFuture<List<WorldgenPayload>> known = CACHE.get(server);
         if (known != null && !known.isCompletedExceptionally()) {
            return known;
         }

         WorldgenExport.Snapshot snapshot = snapshot(server);
         CompletableFuture<List<WorldgenPayload>> future = CompletableFuture.supplyAsync(() -> build(snapshot), Util.backgroundExecutor());
         CACHE.put(server, future);
         return future;
      }
   }

   private static WorldgenExport.Snapshot snapshot(MinecraftServer server) {
      List<WorldgenExport.Dimension> dimensions = new ArrayList<>();
      long seed = 0L;

      for (ServerLevel level : server.getAllLevels()) {
         if (level.dimension().equals(Level.OVERWORLD)) {
            seed = level.getSeed();
         }

         dimensions.add(
            new WorldgenExport.Dimension(
               level.dimension().identifier().toString(),
               level.getSeed(),
               level.getChunkSource().getGenerator(),
               level.structureManager().shouldGenerateStructures()
            )
         );
      }

      return new WorldgenExport.Snapshot(server.registryAccess(), seed, List.copyOf(server.getPackRepository().getSelectedIds()), dimensions);
   }

   private static List<WorldgenPayload> build(WorldgenExport.Snapshot snapshot) {
      long start = System.nanoTime();
      RegistryOps<Tag> ops = snapshot.access().createSerializationContext(NbtOps.INSTANCE);
      CompoundTag root = new CompoundTag();
      root.putInt("format", 1);
      root.putLong("seed", snapshot.seed());
      root.putBoolean("biomes", true);
      ListTag packs = new ListTag();

      for (String pack : snapshot.packs()) {
         packs.add(StringTag.valueOf(pack));
      }

      root.put("packs", packs);
      ListTag registries = new ListTag();

      for (RegistryData<?> data : RegistryDataLoader.WORLD_REGISTRIES) {
         if (KEYS.contains(data.key())) {
            registries.add(encode(data, snapshot.access(), ops));
         }
      }

      root.put("registries", registries);
      ListTag dimensions = new ListTag();

      for (WorldgenExport.Dimension dimension : snapshot.dimensions()) {
         DataResult<Tag> generator = ChunkGenerator.CODEC.encodeStart(ops, dimension.generator());
         if (generator.isError()) {
            Farlands.LOG.warn("Distant terrain for {} cannot be shared: {}", dimension.id(), generator.error().map(Object::toString).orElse("?"));
         } else {
            CompoundTag entry = new CompoundTag();
            entry.putString("id", dimension.id());
            entry.putLong("seed", dimension.seed());
            entry.putBoolean("structures", dimension.structures());
            entry.put("generator", (Tag)generator.getOrThrow());
            dimensions.add(entry);
         }
      }

      root.put("dimensions", dimensions);

      byte[] bytes;
      try {
         ByteArrayOutputStream out = new ByteArrayOutputStream(1048576);
         NbtIo.writeCompressed(root, out);
         bytes = out.toByteArray();
      } catch (IOException e) {
         throw new IllegalStateException(e);
      }

      int session = SESSIONS.incrementAndGet();
      int total = Math.max(1, (bytes.length + 262144 - 1) / 262144);
      List<WorldgenPayload> parts = new ArrayList<>(total);

      for (int i = 0; i < total; i++) {
         int from = i * 262144;
         parts.add(new WorldgenPayload(session, i, total, Arrays.copyOfRange(bytes, from, Math.min(bytes.length, from + 262144))));
      }

      Farlands.LOG
         .info(
            "World generation data for players with Farlands ready: {} KB in {} parts, {} dimensions, {} ms",
            new Object[]{bytes.length / 1024, total, dimensions.size(), (System.nanoTime() - start) / 1000000L}
         );
      return parts;
   }

   private static <T> CompoundTag encode(RegistryData<T> data, RegistryAccess access, RegistryOps<Tag> ops) {
      Registry<T> registry = access.lookupOrThrow(data.key());
      ListTag ids = new ListTag();
      ListTag values = new ListTag();
      int[] remap = new int[registry.size()];
      Arrays.fill(remap, -1);
      boolean biome = data.key().equals(Registries.BIOME);
      int sent = 0;
      int failed = 0;

      for (int id = 0; id < registry.size(); id++) {
         T value = (T)registry.byId(id);
         if (value != null) {
            Identifier key = registry.getKey(value);
            if (key != null) {
               DataResult<Tag> encoded = biome ? LoaderHooks.biomeEncoder.apply((Biome)value, ops) : data.elementCodec().encodeStart(ops, value);
               if (encoded.isError()) {
                  if (failed++ < 4) {
                     Farlands.LOG.warn("Cannot share {} {}: {}", new Object[]{data.key().identifier(), key, encoded.error().map(Object::toString).orElse("?")});
                  }
               } else {
                  ids.add(StringTag.valueOf(key.toString()));
                  values.add((Tag)encoded.getOrThrow());
                  remap[id] = sent++;
               }
            }
         }
      }

      CompoundTag tags = new CompoundTag();
      registry.getTags().forEach(named -> {
         int[] members = named.stream().mapToInt(holder -> {
            int id = registry.getId(holder.value());
            return id >= 0 && id < remap.length ? remap[id] : -1;
         }).filter(id -> id >= 0).toArray();
         tags.putIntArray(named.key().location().toString(), members);
      });
      CompoundTag out = new CompoundTag();
      out.putString("key", data.key().identifier().toString());
      out.put("ids", ids);
      out.put("data", values);
      out.put("tags", tags);
      return out;
   }

   private record Dimension(String id, long seed, ChunkGenerator generator, boolean structures) {
   }

   private record Snapshot(RegistryAccess access, long seed, List<String> packs, List<WorldgenExport.Dimension> dimensions) {
   }
}
