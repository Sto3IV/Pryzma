package net.pryzma.farlands.lod;

import net.pryzma.farlands.gen.TerrainSource;
import net.pryzma.farlands.work.WorkerPool;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

public final class LodWorld {
   private static final int MAX_TILES = 1200;
   private static final int MAX_REAL_CHUNKS = 8000;
   private static final long STALE_MILLIS = 600L;
   private static final long IDLE_MILLIS = 4000L;
   private static final long CROWDED_IDLE_MILLIS = 600L;
   private static final long RETRY_MILLIS = 10000L;
   private static final long RETRY_WINDOW_MILLIS = 60000L;
   private static final long TRIM_MILLIS = 10000L;
   private final LongKeyMap<LodTile> tiles = new LongKeyMap<>();
   private final LongKeyMap<Long> lastUse = new LongKeyMap<>();
   private final LongKeySet pending = new LongKeySet();
   private final LongKeySet previews = new LongKeySet();
   private final LongKeyMap<ChunkColumns> real = new LongKeyMap<>();
   private final LongKeyMap<Long> realUse = new LongKeyMap<>();
   private final ConcurrentLinkedQueue<Long> changed = new ConcurrentLinkedQueue<>();
   private final LongKeySet dirty = new LongKeySet();
   private final LongKeyMap<Long> incomplete = new LongKeyMap<>();
   private final LongKeyMap<long[]> failures = new LongKeyMap<>();
   private final Object writeLock = new Object();
   private final WorkerPool pool;
   private final @Nullable TerrainSource source;
   private final @Nullable ChunkSupplier saved;
   private final @Nullable TileCache cache;
   private final LongKeySet absent = new LongKeySet();
   private final AtomicLong generated = new AtomicLong();
   private final AtomicLong generationNanos = new AtomicLong();
   private final AtomicLong cacheHits = new AtomicLong();
   private final AtomicLong cacheMisses = new AtomicLong();
   private final AtomicLong cacheStale = new AtomicLong();
   private volatile boolean allowGenerate = true;
   private volatile long lastGenerated = now();
   private volatile boolean trimmed;
   private volatile boolean closed;
   private volatile @Nullable Map<Long, LodTile> warm;
   private volatile long warmUntil;

   public LodWorld(WorkerPool pool, @Nullable TerrainSource source, @Nullable ChunkSupplier saved, @Nullable TileCache cache) {
      this.pool = pool;
      this.source = source;
      this.saved = saved;
      this.cache = cache;
   }

   private static long now() {
      return System.nanoTime() / 1000000L;
   }

   public boolean canGenerate() {
      return this.source != null;
   }

   public void warm(Map<Long, LodTile> tiles, long keepMillis) {
      this.warmUntil = now() + keepMillis;
      this.warm = tiles;
   }

   private @Nullable LodTile takeWarm(long key) {
      Map<Long, LodTile> tiles = this.warm;
      if (tiles == null) {
         return null;
      } else if (now() > this.warmUntil) {
         this.warm = null;
         tiles.clear();
         return null;
      } else {
         return tiles.remove(key);
      }
   }

   public void tick(long frameNumber) {
      if (frameNumber % 60L == 0L && !this.incomplete.isEmpty()) {
         long now = now();
         LongArrayList retry = new LongArrayList();

         for (Entry<Long, Long> entry : this.incomplete.entrySet()) {
            if (now - entry.getValue() >= 10000L) {
               retry.add(entry.getKey());
            }
         }

         for (int i = 0; i < retry.size(); i++) {
            long key = retry.getLong(i);
            this.incomplete.remove(key);
            Long used = this.lastUse.get(key);
            if (used != null && now - used <= 60000L) {
               this.lastUse.set(key, now);
               this.submit(key, 1000.0, this.source != null && this.allowGenerate);
            }
         }
      }
   }

   public @Nullable LodTile get(long key) {
      LodTile tile = this.tiles.get(key);
      if (tile != null) {
         this.lastUse.set(key, now());
      }

      return tile;
   }

   public @Nullable Long pollChanged() {
      return this.changed.poll();
   }

   public int pendingCount() {
      return this.pending.size();
   }

   public long generatedCount() {
      return this.generated.get();
   }

   public long generationNanos() {
      return this.generationNanos.get();
   }

   public long cacheHits() {
      return this.cacheHits.get();
   }

   public long cacheMisses() {
      return this.cacheMisses.get();
   }

   public long cacheStale() {
      return this.cacheStale.get();
   }

   public TileCache.@Nullable Stats cacheStats() {
      return this.cache == null ? null : this.cache.stats();
   }

   public int tileCount() {
      return this.tiles.size();
   }

   public int realChunkCount() {
      return this.real.size();
   }

   public void request(long key, double priority, boolean generateAllowed) {
      this.request(key, priority, generateAllowed, true);
   }

   public boolean isPreview(long key) {
      return this.previews.contains(key);
   }

   public void upgrade(long key, double priority) {
      this.request(key, priority, this.allowGenerate, true);
   }

   public void request(long key, double priority, boolean generateAllowed, boolean leaf) {
      long now = now();
      this.lastUse.set(key, now);
      this.allowGenerate = generateAllowed;
      boolean canGenerate = this.source != null && generateAllowed;
      LodTile existing = this.tiles.get(key);
      boolean preview = !leaf;
      if (existing == null || !existing.complete && canGenerate && !this.incomplete.containsKey(key) && (!preview || !this.previews.contains(key))) {
         long[] failure = this.failures.get(key);
         if (failure == null || now >= failure[0]) {
            this.submit(key, priority, canGenerate, preview);
         }
      }
   }

   private void submit(long key, double priority, boolean canGenerate) {
      this.submit(key, priority, canGenerate, false);
   }

   private void submit(long key, double priority, boolean canGenerate, boolean preview) {
      if (this.pending.add(key)) {
         this.pool.submit(priority, () -> this.generate(key, canGenerate, preview), () -> this.pending.remove(key));
      }
   }

   private void generate(long key, boolean canGenerate, boolean preview) {
      try {
         if (this.closed) {
            return;
         }

         Long used = this.lastUse.get(key);
         if (used == null || now() - used > 600L) {
            return;
         }

         LodTile existing = this.tiles.get(key);
         if (existing == null || !existing.complete) {
            LodTile cached = this.cache == null ? null : this.cache.load(key);
            boolean fresh = false;
            LodTile tile;
            if (cached == null || !cached.complete && canGenerate && !preview) {
               if (canGenerate) {
                  if (this.cache != null) {
                     (cached == null ? this.cacheMisses : this.cacheStale).incrementAndGet();
                  }

                  if (cached != null && existing == null) {
                     this.publish(key, cached);
                  }

                  long start = System.nanoTime();
                  LodWorld.SavedRead saved = this.requestSaved(key);
                  LodTile early = preview ? null : this.takeWarm(key);

                  try {
                     tile = early != null ? early : this.source.generate(LodTile.keyLevel(key), LodTile.keyX(key), LodTile.keyZ(key), preview);
                  } finally {
                     this.applySaved(saved);
                  }

                  this.generationNanos.addAndGet(System.nanoTime() - start);
                  this.generated.incrementAndGet();
                  this.lastGenerated = now();
                  this.trimmed = false;
                  fresh = true;
               } else {
                  this.loadSaved(key);
                  tile = new TileBuilder().build(LodTile.keyLevel(key), LodTile.keyX(key), LodTile.keyZ(key), 0L, false);
               }
            } else {
               this.cacheHits.incrementAndGet();
               tile = cached;
            }

            if (tile == null || this.closed) {
               return;
            }

            this.failures.remove(key);
            boolean previewTile = preview && !tile.complete;
            if (previewTile) {
               this.previews.add(key);
            } else {
               this.previews.remove(key);
            }

            LodTile applied;
            synchronized (this.writeLock) {
               applied = this.applyRealArea(tile);
               this.tiles.set(key, applied);
            }

            this.changed.add(key);
            if (!applied.complete && canGenerate && !previewTile) {
               this.incomplete.set(key, now());
            } else {
               this.incomplete.remove(key);
            }

            if (this.cache != null && (fresh || applied.realMask != tile.realMask)) {
               this.cache.save(applied);
            }

            return;
         }
      } catch (RuntimeException | StackOverflowError e) {
         this.fail(key);
         throw e;
      } finally {
         this.pending.remove(key);
      }
   }

   private void publish(long key, LodTile tile) {
      synchronized (this.writeLock) {
         if (this.tiles.containsKey(key)) {
            return;
         }

         LodTile applied = this.applyRealArea(tile);
         this.tiles.set(key, applied);
      }

      this.changed.add(key);
   }

   private void fail(long key) {
      long[] failure = this.failures.computeIfMissing(key, k -> new long[2]);
      long attempts = ++failure[1];
      failure[0] = now() + Math.min(120000L, 2000L << (int)Math.min(6L, (attempts - 1L) * 2L));
      if (this.failures.size() > 50000) {
         this.failures.clear();
      }
   }

   private void loadSaved(long key) {
      this.applySaved(this.requestSaved(key));
   }

   private LodWorld.@Nullable SavedRead requestSaved(long key) {
      ChunkSupplier supplier = this.saved;
      int level = LodTile.keyLevel(key);
      if (supplier != null && level <= 2) {
         int size = 64 << level;
         int step = 1 << level;
         int x0 = LodTile.keyX(key) * size - step;
         int z0 = LodTile.keyZ(key) * size - step;
         int cx0 = Math.floorDiv(x0, 16);
         int cz0 = Math.floorDiv(z0, 16);
         int cx1 = Math.floorDiv(x0 + size + 2 * step, 16);
         int cz1 = Math.floorDiv(z0 + size + 2 * step, 16);
         if (this.absent.size() > 400000) {
            this.absent.clear();
         }

         LongArrayList wanted = new LongArrayList();

         for (int cz = cz0; cz <= cz1; cz++) {
            for (int cx = cx0; cx <= cx1; cx++) {
               long chunkKey = ChunkPos.pack(cx, cz);
               if (!this.real.containsKey(chunkKey) && !this.absent.contains(chunkKey)) {
                  wanted.add(chunkKey);
               }
            }
         }

         if (!wanted.isEmpty() && !this.closed) {
            long[] chunks = wanted.toLongArray();
            return new LodWorld.SavedRead(chunks, supplier.request(chunks));
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private void applySaved(LodWorld.@Nullable SavedRead read) {
      if (read != null && !this.closed) {
         long[] chunks = read.chunks();
         ChunkColumns[] loaded = read.result().get();
         long now = now();

         for (int i = 0; i < chunks.length; i++) {
            if (this.closed) {
               return;
            }

            long chunkKey = chunks[i];
            if (!this.real.containsKey(chunkKey)) {
               ChunkColumns columns = loaded[i];
               if (columns == null) {
                  this.absent.add(chunkKey);
               } else {
                  this.real.setIfAbsent(chunkKey, columns);
                  this.realUse.set(chunkKey, now);
               }
            }
         }
      }
   }

   public void ingest(ChunkColumns columns) {
      if (!this.closed) {
         long chunkKey = ChunkPos.pack(columns.chunkX, columns.chunkZ);
         this.real.set(chunkKey, columns);
         this.realUse.set(chunkKey, now());

         for (int level = 0; level <= 7; level++) {
            int tileBlocks = 64 << level;
            int bx = columns.chunkX * 16;
            int bz = columns.chunkZ * 16;
            int step = 1 << level;
            int minTx = Math.floorDiv(bx - step, tileBlocks);
            int maxTx = Math.floorDiv(bx + 16 + step, tileBlocks);
            int minTz = Math.floorDiv(bz - step, tileBlocks);
            int maxTz = Math.floorDiv(bz + 16 + step, tileBlocks);

            for (int tz = minTz; tz <= maxTz; tz++) {
               for (int tx = minTx; tx <= maxTx; tx++) {
                  long key = LodTile.key(level, tx, tz);
                  if (this.source == null || level <= 2 || this.tiles.containsKey(key)) {
                     this.dirty.add(key);
                  }
               }
            }
         }
      }
   }

   public boolean hasDirty() {
      return !this.dirty.isEmpty();
   }

   public void flushReal() {
      if (!this.closed && !this.dirty.isEmpty()) {
         LongArrayList keys = new LongArrayList();

         for (Long key : this.dirty) {
            keys.add(key);
         }

         for (int i = 0; i < keys.size(); i++) {
            this.dirty.remove(keys.getLong(i));
         }

         List<LodTile> saves = new ArrayList<>();

         for (int i = 0; i < keys.size(); i++) {
            long key = keys.getLong(i);
            synchronized (this.writeLock) {
               LodTile tile = this.tiles.get(key);
               if (tile == null) {
                  this.changed.add(key);
               } else {
                  LodTile updated = this.applyRealArea(tile);
                  if (updated != tile) {
                     this.tiles.set(key, updated);
                     this.lastUse.setIfAbsent(key, now());
                     this.changed.add(key);
                     saves.add(updated);
                  }
               }
            }
         }

         if (this.cache != null) {
            for (LodTile tile : saves) {
               this.cache.save(tile);
            }
         }
      }
   }

   private LodTile applyRealArea(LodTile tile) {
      if (this.real.isEmpty()) {
         return tile;
      }

      int step = 1 << tile.level;
      int x0 = tile.originX() - step;
      int z0 = tile.originZ() - step;
      int x1 = tile.originX() + tile.blockSize() + step;
      int z1 = tile.originZ() + tile.blockSize() + step;
      int cx0 = Math.floorDiv(x0, 16);
      int cz0 = Math.floorDiv(z0, 16);
      int cx1 = Math.floorDiv(x1, 16);
      int cz1 = Math.floorDiv(z1, 16);
      long area = (long)(cx1 - cx0 + 1) * (cz1 - cz0 + 1);
      TileBuilder builder = null;
      long mask = tile.realMask;
      if (area <= this.real.size()) {
         for (int cz = cz0; cz <= cz1; cz++) {
            for (int cx = cx0; cx <= cx1; cx++) {
               ChunkColumns columns = this.real.get(ChunkPos.pack(cx, cz));
               if (columns != null) {
                  if (builder == null) {
                     builder = TileBuilder.borrow();
                     builder.copyFrom(tile);
                  }

                  mask |= applyReal(builder, tile, columns);
               }
            }
         }
      } else {
         for (ChunkColumns columns : this.real.values()) {
            if (columns.chunkX >= cx0 && columns.chunkX <= cx1 && columns.chunkZ >= cz0 && columns.chunkZ <= cz1) {
               if (builder == null) {
                  builder = TileBuilder.borrow();
                  builder.copyFrom(tile);
               }

               mask |= applyReal(builder, tile, columns);
            }
         }
      }

      return builder == null ? tile : builder.build(tile.level, tile.tx, tile.tz, mask, tile.complete);
   }

   private static long applyReal(TileBuilder builder, LodTile tile, ChunkColumns columns) {
      int step = 1 << tile.level;
      int originX = tile.originX();
      int originZ = tile.originZ();
      int bx = columns.chunkX * 16;
      int bz = columns.chunkZ * 16;
      int cx0 = Math.max(-1, Math.floorDiv(bx - originX + step - 1, step));
      int cz0 = Math.max(-1, Math.floorDiv(bz - originZ + step - 1, step));
      int cx1 = Math.min(64, Math.floorDiv(bx + 15 - originX, step));
      int cz1 = Math.min(64, Math.floorDiv(bz + 15 - originZ, step));
      long mask = 0L;

      for (int cz = cz0; cz <= cz1; cz++) {
         for (int cx = cx0; cx <= cx1; cx++) {
            int lx = originX + cx * step - bx;
            int lz = originZ + cz * step - bz;
            builder.set(cx, cz, columns.spans, columns.first(lx, lz), columns.count(lx, lz));
            int mx = Math.max(0, Math.min(63, cx));
            int mz = Math.max(0, Math.min(63, cz));
            mask |= 1L << mz * 8 / 64 * 8 + mx * 8 / 64;
         }
      }

      return mask;
   }

   public void evict(long frameNumber) {
      long now = now();
      if (this.source != null && !this.trimmed && this.pending.isEmpty() && now - this.lastGenerated > 10000L) {
         this.trimmed = true;
         this.source.trim();
      }

      boolean crowded = this.tiles.size() > 1200;
      LongArrayList victims = new LongArrayList();

      for (Long key : this.tiles.keySet()) {
         Long used = this.lastUse.get(key);
         long idle = used == null ? Long.MAX_VALUE : now - used;
         if (idle > 4000L || crowded && idle > 600L) {
            victims.add(key);
         }
      }

      for (int i = 0; i < victims.size(); i++) {
         long key = victims.getLong(i);
         if (!this.pending.contains(key) && !this.incomplete.containsKey(key)) {
            this.tiles.remove(key);
            this.previews.remove(key);
         }
      }

      if (this.lastUse.size() > this.tiles.size() + 4096) {
         this.lastUse
            .entrySet()
            .removeIf(
               entry -> now - entry.getValue() > 60000L
                  && !this.tiles.containsKey(entry.getKey())
                  && !this.pending.contains(entry.getKey())
                  && !this.incomplete.containsKey(entry.getKey())
            );
      }

      if (this.real.size() > 8000) {
         long[] times = new long[this.realUse.size()];
         int n = 0;

         for (Long time : this.realUse.values()) {
            if (n == times.length) {
               break;
            }

            times[n++] = time;
         }

         Arrays.sort(times, 0, n);
         int remove = this.real.size() - 6000;
         if (remove > 0 && n > 0) {
            long cutoff = times[Math.min(n - 1, remove - 1)];
            this.realUse.entrySet().removeIf(entry -> {
               if (entry.getValue() > cutoff) {
                  return false;
               }

               this.real.remove(entry.getKey());
               return true;
            });
         }
      }
   }

   public void close() {
      this.closed = true;
      if (this.source != null) {
         this.source.close();
      }

      this.tiles.clear();
      this.real.clear();
      this.realUse.clear();
      this.dirty.clear();
      this.pending.clear();
      this.changed.clear();
      this.incomplete.clear();
      this.failures.clear();
      if (this.cache != null) {
         this.cache.close();
      }
   }

   private record SavedRead(long[] chunks, Supplier<@Nullable ChunkColumns[]> result) {
   }
}
