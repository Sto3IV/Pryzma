package net.pryzma.farlands.lod;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jspecify.annotations.Nullable;

public final class TileCache {
   public static final int FORMAT = 8;
   private static final long CLOSE_WAIT_MILLIS = 100L;
   private final TileStore store;
   private final AtomicBoolean closed = new AtomicBoolean();

   public TileCache(Path root) {
      this(root, "");
   }

   public TileCache(Path root, @Nullable String fingerprint) {
      this.store = TileStore.acquire(root, fingerprint == null ? "" : fingerprint);
   }

   public Path directory() {
      return this.store.dir;
   }

   public boolean contains(long key) {
      return !this.closed.get() && this.store.contains(key, false);
   }

   public boolean containsComplete(long key) {
      return !this.closed.get() && this.store.contains(key, true);
   }

   public @Nullable LodTile load(long key) {
      return this.closed.get() ? null : this.store.load(key);
   }

   public @Nullable LodTile loadComplete(long key) {
      LodTile tile = this.load(key);
      return tile != null && tile.complete ? tile : null;
   }

   public void save(LodTile tile) {
      if (!this.closed.get()) {
         this.store.save(tile);
      }
   }

   public boolean flush(long timeoutMillis) {
      return this.store.flush(timeoutMillis);
   }

   public TileCache.Stats stats() {
      return this.store.stats();
   }

   public boolean isClosed() {
      return this.closed.get();
   }

   public void close() {
      if (this.closed.compareAndSet(false, true)) {
         if (this.store.release()) {
            this.store.awaitTerminated(100L);
         }
      }
   }

   public boolean awaitClosed(long timeoutMillis) {
      return this.closed.get() && this.store.awaitTerminated(timeoutMillis);
   }

   public record Stats(int queued, long written, long dropped, long corrupt, long compactions, long failures, int openFiles, int regions, boolean disabled) {
   }
}
