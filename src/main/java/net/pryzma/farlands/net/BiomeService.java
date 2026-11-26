package net.pryzma.farlands.net;

import net.pryzma.farlands.LoaderHooks;
import net.pryzma.farlands.Farlands;
import net.pryzma.farlands.FarlandsServerConfig;
import net.pryzma.farlands.gen.BiomeGrid;
import net.pryzma.farlands.gen.SurfaceBiomes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.Map.Entry;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.zip.Deflater;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;

public final class BiomeService {
   private static final int CACHE_SIZE = 8192;
   private static final Map<ServerLevel, Optional<SurfaceBiomes>> SOURCES = new WeakHashMap<>();
   private static final Map<String, BiomeGridPayload> CACHE = new LinkedHashMap<String, BiomeGridPayload>(256, 0.75F, true) {
      @Override
      protected boolean removeEldestEntry(Entry<String, BiomeGridPayload> eldest) {
         return this.size() > 8192;
      }
   };
   private static long computed;
   private static long cached;
   private static long computeNanos;
   private static long bytesSent;

   private BiomeService() {
   }

   public static void onRequest(ServerPlayer player, BiomeRequestPayload request) {
      MinecraftServer server = player.level().getServer();
      if (server != null) {
         if (!FarlandsServerConfig.share()) {
            LoaderHooks.sender.accept(player, BiomeGridPayload.empty(request.id()));
         } else {
            Identifier id = Identifier.tryParse(request.dimension());
            ServerLevel level = id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
            if (level != null && request.level() >= 0 && request.level() <= 7) {
               PlayerUpload.Budget budget = PlayerUpload.budget(player);
               long delay = PlayerUpload.reserveRequest(budget);
               if (delay < 0L) {
                  LoaderHooks.sender.accept(player, BiomeGridPayload.empty(request.id()));
               } else {
                  try {
                     PlayerUpload.EXECUTOR.schedule(() -> {
                        BiomeGridPayload result;
                        try {
                           result = player.hasDisconnected() ? null : compute(level, request).withId(request.id());
                        } catch (RuntimeException e) {
                           Farlands.LOG.debug("Distant biome request failed", e);
                           result = BiomeGridPayload.empty(request.id());
                        } finally {
                           PlayerUpload.finishRequest(budget);
                        }

                        if (result != null) {
                           PlayerUpload.send(server, player, result, result.packed().length + 16 * result.palette().size() + 16);
                        }
                     }, delay, TimeUnit.NANOSECONDS);
                  } catch (RejectedExecutionException e) {
                     PlayerUpload.finishRequest(budget);
                     LoaderHooks.sender.accept(player, BiomeGridPayload.empty(request.id()));
                  }
               }
            } else {
               LoaderHooks.sender.accept(player, BiomeGridPayload.empty(request.id()));
            }
         }
      }
   }

   public static void onStopped(MinecraftServer server) {
      try {
         PlayerUpload.EXECUTOR.execute(() -> {
            SOURCES.clear();
            CACHE.clear();
            PlayerUpload.clear();
         });
      } catch (RejectedExecutionException var2) {
      }
   }

   private static BiomeGridPayload compute(ServerLevel level, BiomeRequestPayload request) {
      String key = request.dimension() + "/" + request.level() + "/" + request.tx() + "/" + request.tz();
      BiomeGridPayload known = CACHE.get(key);
      if (known != null) {
         cached++;
         return known;
      }

      SurfaceBiomes source = SOURCES.computeIfAbsent(level, l -> Optional.ofNullable(SurfaceBiomes.create(l))).orElse(null);
      if (source == null) {
         return BiomeGridPayload.empty(0);
      }

      long start = System.nanoTime();
      BiomeGrid.Layout layout = BiomeGrid.layout(request.level(), request.tx(), request.tz());
      SurfaceBiomes.Result result = source.compute(layout);
      int cells = layout.size() * layout.size();
      byte[] raw = new byte[cells * 2];
      List<String> palette = new ArrayList<>();
      Map<Holder<Biome>, Integer> indices = new HashMap<>();

      for (int i = 0; i < cells; i++) {
         Holder<Biome> biome = result.biomes()[i];
         Integer index = indices.get(biome);
         if (index == null) {
            index = palette.size();
            indices.put(biome, index);
            palette.add(biome.unwrapKey().map(k -> k.identifier().toString()).orElse(""));
         }

         if (index > 255) {
            return BiomeGridPayload.empty(0);
         }

         raw[i] = (byte)index.intValue();
         raw[cells + i] = (byte)(result.quartY()[i] - source.minQuart());
      }

      Deflater deflater = new Deflater(6);
      deflater.setInput(raw);
      deflater.finish();
      byte[] buffer = new byte[raw.length + 64];
      int length = deflater.deflate(buffer);
      deflater.end();
      byte[] packed = Arrays.copyOf(buffer, length);
      BiomeGridPayload payload = new BiomeGridPayload(0, source.minQuart(), List.copyOf(palette), packed);
      CACHE.put(key, payload);
      computeNanos = computeNanos + (System.nanoTime() - start);
      bytesSent += packed.length;
      if (++computed % 100L == 0L) {
         Farlands.LOG
            .info(
               "Distant biomes for players: {} tiles computed ({} ms each on average, {} KB), {} served from cache",
               new Object[]{computed, String.format(Locale.ROOT, "%.1f", computeNanos / 1000000.0 / computed), bytesSent / 1024L, cached}
            );
      }

      return payload;
   }
}
