package net.pryzma.farlands.client;

import net.pryzma.farlands.LoaderHooks;
import net.pryzma.farlands.gen.BiomeGrid;
import net.pryzma.farlands.gen.ServerBiomes;
import net.pryzma.farlands.lod.LodTile;
import net.pryzma.farlands.net.BiomeGridPayload;
import net.pryzma.farlands.net.BiomeRequestPayload;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.Nullable;

public final class RemoteBiomes implements ServerBiomes {
   private static final Map<Integer, CompletableFuture<@Nullable BiomeGridPayload>> PENDING = new ConcurrentHashMap<>();
   private static final AtomicInteger IDS = new AtomicInteger();
   private final String dimension;
   private final Registry<Biome> biomes;
   private final Map<ResourceKey<Biome>, Holder<Biome>> canonical = new HashMap<>();
   private final Map<Long, CompletableFuture<@Nullable BiomeGrid>> cache = Collections.synchronizedMap(
      new LinkedHashMap<Long, CompletableFuture<BiomeGrid>>(256, 0.75F, true) {
         @Override
         protected boolean removeEldestEntry(Entry<Long, CompletableFuture<@Nullable BiomeGrid>> eldest) {
            return this.size() > 4096;
         }
      }
   );

   RemoteBiomes(String dimension, Registry<Biome> biomes, Set<Holder<Biome>> sourceBiomes) {
      this.dimension = dimension;
      this.biomes = biomes;

      for (Holder<Biome> holder : sourceBiomes) {
         holder.unwrapKey().ifPresent(key -> this.canonical.put((ResourceKey<Biome>)key, holder));
      }
   }

   public static void receive(BiomeGridPayload payload) {
      CompletableFuture<BiomeGridPayload> future = PENDING.remove(payload.id());
      if (future != null) {
         future.complete(payload);
      }
   }

   static void reset() {
      for (CompletableFuture<BiomeGridPayload> future : PENDING.values()) {
         future.complete(null);
      }

      PENDING.clear();
   }

   @Override
   public CompletableFuture<@Nullable BiomeGrid> request(int level, int tx, int tz) {
      long key = LodTile.key(level, tx, tz);
      return this.cache.computeIfAbsent(key, k -> {
         int id = IDS.incrementAndGet();
         CompletableFuture<BiomeGridPayload> reply = new CompletableFuture<>();
         PENDING.put(id, reply);
         BiomeRequestPayload request = new BiomeRequestPayload(id, this.dimension, level, tx, tz);
         Minecraft.getInstance().execute(() -> LoaderHooks.clientSender.accept(request));
         return reply.thenApply(payload -> payload == null ? null : this.decode(payload, BiomeGrid.layout(level, tx, tz)));
      });
   }

   private @Nullable BiomeGrid decode(BiomeGridPayload payload, BiomeGrid.Layout layout) {
      if (payload.packed().length != 0 && !payload.palette().isEmpty()) {
         int cells = layout.size() * layout.size();
         byte[] raw = new byte[cells * 2];
         Inflater inflater = new Inflater();

         try {
            inflater.setInput(payload.packed());
            int read = 0;

            while (read < raw.length && !inflater.finished()) {
               int n = inflater.inflate(raw, read, raw.length - read);
               if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                  break;
               }

               read += n;
            }

            if (read != raw.length) {
               return null;
            }
         } catch (DataFormatException e) {
            return null;
         } finally {
            inflater.end();
         }

         Holder[] var15 = new Holder[payload.palette().size()];

         for (int var16 = 0; var16 < var15.length; var16++) {
            Identifier id = Identifier.tryParse(payload.palette().get(var16));
            if (id != null) {
               ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, id);
               Holder<Biome> known = this.canonical.get(key);
               var15[var16] = known != null ? known : this.biomes.get(key).map(reference -> (Holder)reference).orElse(null);
            }
         }

         Holder<Biome>[] grid = new Holder[cells];
         int[] quartY = new int[cells];

         for (int i = 0; i < cells; i++) {
            int index = raw[i] & 255;
            grid[i] = index < var15.length ? var15[index] : null;
            quartY[i] = payload.minQuart() + (raw[cells + i] & 255);
         }

         return new BiomeGrid(layout, grid, quartY);
      } else {
         return null;
      }
   }
}
