package net.pryzma.farlands.lod;

import net.pryzma.farlands.Farlands;
import java.io.EOFException;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;
import java.util.zip.Inflater;
import org.jspecify.annotations.Nullable;

final class TileStore {
   static final int FORMAT = 8;
   private static final String META = "cache.meta";
   private static final String LOCK = "cache.lock";
   private static final int QUEUE_SOFT = 384;
   private static final int QUEUE_HARD = 1536;
   private static final long BLOCK_NANOS = 25000000L;
   private static final long COALESCE_NANOS = 400000000L;
   private static final long IDLE_NANOS = 1000000000L;
   private static final long IDLE_CLOSE_NANOS = 20000000000L;
   private static final long COMPACT_RETRY_NANOS = 60000000000L;
   private static final long READY_WAIT_MILLIS = 1000L;
   private static final long LOCK_WAIT_NANOS = 3000000000L;
   private static final long SHUTDOWN_WAIT_NANOS = 3000000000L;
   private static final long CLEANUP_DELAY_MILLIS = 3000L;
   private static final long WARN_INTERVAL_NANOS = 30000000000L;
   private static final int MAX_OPEN_FILES = 48;
   private static final int KEEP_OPEN_FILES = 32;
   private static final int MAX_POOLED_INFLATERS = 8;
   private static final int MAX_META_BYTES = 1048576;
   private static final Pattern DATA_DIR = Pattern.compile("v\\d+-[0-9a-f]{16}");
   private static final Pattern OLD_LEVEL_DIR = Pattern.compile("L\\d+");
   private static final Map<Path, TileStore> OPEN = new HashMap<>();
   private static final Map<Path, TileStore> ENDING = new HashMap<>();
   private static final AtomicLong NEXT_WARN = new AtomicLong(System.nanoTime());
   private static final AtomicLong SUPPRESSED = new AtomicLong();
   private static volatile boolean shutdown;
   private static boolean hookInstalled;
   private static final TileStore.Job IDLE = new TileStore.Job(0L, null);
   final Path root;
   final Path dir;
   private final String fingerprint;
   private final @Nullable TileStore predecessor;
   private final Thread writer;
   private final CountDownLatch ready = new CountDownLatch(1);
   private final CountDownLatch terminated = new CountDownLatch(1);
   private final ReentrantLock queueLock = new ReentrantLock();
   private final Condition queued = this.queueLock.newCondition();
   private final Condition space = this.queueLock.newCondition();
   private final Condition idle = this.queueLock.newCondition();
   private final HashMap<Long, TileStore.Entry> pending = new HashMap<>();
   private final ArrayDeque<Long> order = new ArrayDeque<>();
   private final ConcurrentHashMap<Long, TileRegion> regions = new ConcurrentHashMap<>();
   private final AtomicInteger openFiles = new AtomicInteger();
   private final ConcurrentLinkedQueue<Inflater> inflaters = new ConcurrentLinkedQueue<>();
   private final AtomicInteger pooled = new AtomicInteger();
   private final AtomicLong written = new AtomicLong();
   private final AtomicLong dropped = new AtomicLong();
   private final AtomicLong corrupted = new AtomicLong();
   private final AtomicLong compactions = new AtomicLong();
   private final AtomicLong failures = new AtomicLong();
   private int refs;
   private boolean busy;
   private long busyKey;
   private int flushWaiters;
   private volatile boolean draining;
   private volatile boolean disabled;
   private volatile boolean finished;
   private volatile boolean shut;
   private volatile Set<String> files = Set.of();
   private @Nullable FileChannel lockChannel;
   private @Nullable FileLock fileLock;

   private TileStore(Path root, Path dir, String fingerprint, @Nullable TileStore predecessor) {
      this.root = root;
      this.dir = dir;
      this.fingerprint = fingerprint;
      this.predecessor = predecessor;
      this.writer = new Thread(this::run, "Farlands Cache Writer");
      this.writer.setDaemon(true);
      this.writer.setPriority(2);
   }

   static TileStore acquire(Path root, String fingerprint) {
      Path base = root.toAbsolutePath().normalize();
      Path dir = base.resolve(dirName(fingerprint));
      synchronized (OPEN) {
         if (!hookInstalled) {
            hookInstalled = true;

            try {
               Runtime.getRuntime().addShutdownHook(new Thread(TileStore::shutdownAll, "Farlands Cache Shutdown"));
            } catch (IllegalStateException | SecurityException var8) {
            }
         }

         TileStore known = OPEN.get(dir);
         if (known != null && !known.finished) {
            known.refs++;
            known.draining = false;
            return known;
         } else {
            TileStore store = new TileStore(base, dir, fingerprint, known != null ? known : ENDING.get(dir));
            store.refs = 1;
            OPEN.put(dir, store);
            store.writer.start();
            return store;
         }
      }
   }

   static String dirName(String fingerprint) {
      long hash = -3750763034362895579L;

      for (byte b : ("8\n" + fingerprint).getBytes(StandardCharsets.UTF_8)) {
         hash ^= b & 0xFF;
         hash *= 1099511628211L;
      }

      String hex = Long.toHexString(hash);
      return "v8-" + "0".repeat(16 - hex.length()) + hex;
   }

   boolean release() {
      synchronized (OPEN) {
         if (this.refs <= 0 || --this.refs > 0) {
            return false;
         }

         this.draining = true;
      }

      this.wake();
      return true;
   }

   private void wake() {
      this.queueLock.lock();

      try {
         this.queued.signalAll();
         this.space.signalAll();
      } finally {
         this.queueLock.unlock();
      }
   }

   boolean awaitTerminated(long millis) {
      try {
         return this.terminated.await(millis, TimeUnit.MILLISECONDS);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         return false;
      }
   }

   private boolean awaitReady() {
      if (this.ready.getCount() > 0L) {
         try {
            if (!this.ready.await(1000L, TimeUnit.MILLISECONDS)) {
               return false;
            }
         } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
         }
      }

      return !this.disabled && !this.shut;
   }

   boolean knownFile(String name) {
      return this.files.contains(name);
   }

   boolean isShut() {
      return this.shut;
   }

   void fileOpened() {
      this.openFiles.incrementAndGet();
   }

   void fileClosed() {
      this.openFiles.decrementAndGet();
   }

   private TileRegion region(long key) {
      return this.regions.computeIfAbsent(TileRegion.regionKey(key), k -> new TileRegion(this, k));
   }

   void save(LodTile tile) {
      if (!this.finished && !this.disabled) {
         long key = tile.key();
         this.queueLock.lock();

         try {
            long budget = 25000000L;

            while (true) {
               if (this.finished) {
                  return;
               }

               TileStore.Entry known = this.pending.get(key);
               if (known != null) {
                  if (tile.complete || !known.tile.complete) {
                     known.tile = tile;
                  }
                  break;
               }

               if (this.pending.size() >= 384) {
                  if (this.dropOldestProvisional()) {
                     continue;
                  }

                  if (!tile.complete) {
                     this.dropped.incrementAndGet();
                     return;
                  }

                  if (budget > 0L) {
                     try {
                        budget = this.space.awaitNanos(budget);
                     } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        budget = 0L;
                     }
                     continue;
                  }
               }

               if (this.pending.size() < 1536) {
                  this.pending.put(key, new TileStore.Entry(tile, System.nanoTime()));
                  this.order.addLast(key);
                  this.queued.signal();
                  return;
               }

               this.dropped.incrementAndGet();
               warn("write queue overflow, tile " + describe(key) + " was not cached", null);
               return;
            }
         } finally {
            this.queueLock.unlock();
         }
      }
   }

   private boolean dropOldestProvisional() {
      Iterator<Long> it = this.order.iterator();

      while (it.hasNext()) {
         long key = it.next();
         if (!this.busy || key != this.busyKey) {
            TileStore.Entry entry = this.pending.get(key);
            if (entry != null && !entry.tile.complete) {
               it.remove();
               this.pending.remove(key);
               this.dropped.incrementAndGet();
               return true;
            }
         }
      }

      return false;
   }

   @Nullable LodTile load(long key) {
      if (!this.awaitReady()) {
         return null;
      }

      LodTile queuedTile = this.queuedTile(key);
      TileRegion region = this.region(key);
      region.ensureLoaded();
      int index = TileRegion.slotIndex(key);
      if (queuedTile != null) {
         TileRegion.Slot slot = region.slot(index);
         if (queuedTile.complete || slot == null || !slot.complete()) {
            return queuedTile;
         }
      }

      TileRegion.Blob blob;
      try {
         blob = region.read(index);
      } catch (IOException e) {
         if (e instanceof EOFException) {
            this.corrupt("record cut off at tile " + describe(key), region.path);
         } else {
            this.failures.incrementAndGet();
            warn("could not read " + region.path + ": " + e, null);
         }

         return null;
      }

      if (blob == null) {
         return null;
      }

      Inflater inflater = this.borrowInflater();

      try {
         return TileCodec.decode(key, blob.data(), blob.slot().crc(), inflater);
      } catch (TileCodec.Corrupt e) {
         region.invalidate(index, blob.slot());
         this.corrupt(e.getMessage() + " at tile " + describe(key), region.path);
         return null;
      } finally {
         this.returnInflater(inflater);
      }
   }

   boolean contains(long key, boolean completeOnly) {
      if (!this.awaitReady()) {
         return false;
      } else {
         LodTile queuedTile = this.queuedTile(key);
         if (queuedTile == null || !queuedTile.complete && completeOnly) {
            TileRegion region = this.region(key);
            region.ensureLoaded();
            TileRegion.Slot slot = region.slot(TileRegion.slotIndex(key));
            return slot != null && (slot.complete() || !completeOnly);
         } else {
            return true;
         }
      }
   }

   private @Nullable LodTile queuedTile(long key) {
      this.queueLock.lock();

      try {
         TileStore.Entry entry = this.pending.get(key);
         return entry == null ? null : entry.tile;
      } finally {
         this.queueLock.unlock();
      }
   }

   boolean flush(long timeoutMillis) {
      this.queueLock.lock();

      try {
         this.flushWaiters++;
         this.queued.signalAll();
         long nanos = TimeUnit.MILLISECONDS.toNanos(timeoutMillis);

         while ((!this.order.isEmpty() || this.busy) && !this.finished && nanos > 0L) {
            try {
               nanos = this.idle.awaitNanos(nanos);
            } catch (InterruptedException e) {
               Thread.currentThread().interrupt();
               break;
            }
         }

         return this.order.isEmpty() && !this.busy;
      } finally {
         this.flushWaiters--;
         this.queueLock.unlock();
      }
   }

   TileCache.Stats stats() {
      this.queueLock.lock();

      int queuedCount;
      try {
         queuedCount = this.pending.size();
      } finally {
         this.queueLock.unlock();
      }

      return new TileCache.Stats(
         queuedCount,
         this.written.get(),
         this.dropped.get(),
         this.corrupted.get(),
         this.compactions.get(),
         this.failures.get(),
         this.openFiles.get(),
         this.regions.size(),
         this.disabled
      );
   }

   private Inflater borrowInflater() {
      Inflater inflater = this.inflaters.poll();
      if (inflater != null) {
         this.pooled.decrementAndGet();
         return inflater;
      } else {
         return new Inflater();
      }
   }

   private void returnInflater(Inflater inflater) {
      if (!this.shut && this.pooled.incrementAndGet() <= 8) {
         this.inflaters.offer(inflater);
         if (this.shut) {
            this.endInflaters();
         }
      } else {
         if (!this.shut) {
            this.pooled.decrementAndGet();
         }

         inflater.end();
      }
   }

   private void endInflaters() {
      Inflater inflater;
      while ((inflater = this.inflaters.poll()) != null) {
         inflater.end();
      }
   }

   void corrupt(String reason, Path path) {
      this.corrupted.incrementAndGet();
      warn(reason + " in " + path + ", treated as missing", null);
   }

   private void run() {
      TileCodec codec = null;

      try {
         try {
            this.init();
         } catch (IOException | RuntimeException e) {
            this.disabled = true;
            warn("cache disabled for " + this.dir + ": " + e, null);
         } finally {
            this.ready.countDown();
         }

         if (!this.disabled) {
            codec = new TileCodec();
            this.startCleanup();
         }

         long lastWork = System.nanoTime();

         while (true) {
            TileStore.Job job = this.next();
            if (job == null) {
               break;
            }

            if (job != IDLE) {
               if (codec != null) {
                  this.write(codec, job);
               }

               this.finish(job);
               lastWork = System.nanoTime();
            }

            if (codec != null) {
               if (job == IDLE && System.nanoTime() - lastWork > 20000000000L) {
                  this.closeIdleFiles();
               } else {
                  this.trimFiles();
               }
            }
         }
      } catch (Throwable t) {
         warn("cache writer stopped unexpectedly: " + t, t);
      } finally {
         this.terminate(codec);
      }
   }

   private TileStore.@Nullable Job next() {
      this.queueLock.lock();

      try {
         while (true) {
            if (!this.order.isEmpty()) {
               long key = this.order.peekFirst();
               TileStore.Entry entry = this.pending.get(key);
               if (entry == null) {
                  this.order.pollFirst();
               } else {
                  long wait = entry.since + 400000000L - System.nanoTime();
                  if (wait <= 0L || this.urgent()) {
                     this.busy = true;
                     this.busyKey = key;
                     return new TileStore.Job(key, entry.tile);
                  }

                  try {
                     this.queued.awaitNanos(wait);
                  } catch (InterruptedException var12) {
                  }
               }
            } else {
               this.idle.signalAll();
               this.space.signalAll();
               if (this.draining || shutdown) {
                  synchronized (OPEN) {
                     if (this.refs <= 0 || shutdown) {
                        this.finished = true;
                        OPEN.remove(this.dir, this);
                        ENDING.put(this.dir, this);
                        return null;
                     }
                  }
               }

               try {
                  if (this.queued.awaitNanos(1000000000L) <= 0L && this.order.isEmpty()) {
                     return IDLE;
                  }
               } catch (InterruptedException var13) {
               }
            }
         }
      } finally {
         this.queueLock.unlock();
      }
   }

   private boolean urgent() {
      return this.draining || shutdown || this.flushWaiters > 0 || this.pending.size() >= 192;
   }

   private void finish(TileStore.Job job) {
      this.queueLock.lock();

      try {
         this.busy = false;
         Long head = this.order.peekFirst();
         if (head != null && head == job.key()) {
            this.order.pollFirst();
         } else {
            this.order.remove(job.key());
         }

         TileStore.Entry entry = this.pending.get(job.key());
         if (entry != null) {
            if (entry.tile == job.tile()) {
               this.pending.remove(job.key());
            } else {
               entry.since = System.nanoTime();
               this.order.addLast(job.key());
            }
         }

         this.space.signalAll();
         if (this.order.isEmpty()) {
            this.idle.signalAll();
         }
      } finally {
         this.queueLock.unlock();
      }
   }

   private void write(TileCodec codec, TileStore.Job job) {
      long key = job.key();
      LodTile tile = job.tile();
      if (tile != null) {
         try {
            TileRegion region = this.region(key);
            region.ensureLoaded();
            int index = TileRegion.slotIndex(key);
            TileRegion.Slot old = region.slot(index);
            if (!tile.complete && old != null && old.complete()) {
               return;
            }

            String problem = codec.encode(tile);
            if (problem != null) {
               this.failures.incrementAndGet();
               warn("tile " + describe(key) + " was not cached: " + problem, null);
               return;
            }

            region.append(index, codec.blob(), codec.blobLength(), codec.blobCrc(), tile.complete);
            this.written.incrementAndGet();
            if (!this.draining && !shutdown && region.needsCompaction() && System.nanoTime() - region.compactAfter >= 0L) {
               try {
                  region.compact();
                  this.compactions.incrementAndGet();
               } catch (IOException e) {
                  region.compactAfter = System.nanoTime() + 60000000000L;
                  warn("could not compact " + region.path + ": " + e, null);
               }
            }
         } catch (IOException | RuntimeException e) {
            this.failures.incrementAndGet();
            warn("could not write tile " + describe(key) + ": " + e, null);
         }
      }
   }

   private void trimFiles() {
      if (this.openFiles.get() > 48) {
         List<TileRegion> open = new ArrayList<>();

         for (TileRegion region : this.regions.values()) {
            if (region.isOpen()) {
               open.add(region);
            }
         }

         open.sort((a, b) -> Long.compare(a.lastUse, b.lastUse));

         for (TileRegion region : open) {
            if (this.openFiles.get() <= 32) {
               break;
            }

            region.tryClose();
         }
      }
   }

   private void closeIdleFiles() {
      long now = System.nanoTime();

      for (TileRegion region : this.regions.values()) {
         if (region.isOpen() && now - region.lastUse > 20000000000L) {
            region.tryClose();
         }
      }
   }

   private void init() throws IOException {
      TileStore before = this.predecessor;
      if (before != null && !before.awaitTerminated(10000L)) {
         warn("previous cache writer for " + this.dir + " is still busy", null);
      }

      Files.createDirectories(this.dir);
      if (this.lock()) {
         Path meta = this.dir.resolve("cache.meta");
         byte[] expected = this.metaBytes();
         boolean valid = false;

         try {
            valid = Files.size(meta) <= 1048576L && Arrays.equals(Files.readAllBytes(meta), expected);
         } catch (NoSuchFileException var12) {
         }

         Set<String> names = new HashSet<>();

         try (DirectoryStream<Path> stream = Files.newDirectoryStream(this.dir)) {
            for (Path path : stream) {
               String name = path.getFileName().toString();
               if (name.endsWith(".compact") || name.endsWith(".tmp")) {
                  deleteQuietly(path);
               } else if (name.endsWith(".prtr") && (valid || !deleteQuietly(path))) {
                  names.add(name);
               }
            }
         }

         if (!valid) {
            Path temp = this.dir.resolve("cache.meta.tmp");
            Files.write(temp, expected);
            Files.move(temp, meta, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            if (!names.isEmpty()) {
               warn("stale region files in " + this.dir + " could not be removed", null);
               names.clear();
            }
         }

         this.files = Set.copyOf(names);
      }
   }

   private boolean lock() throws IOException {
      Path path = this.dir.resolve("cache.lock");
      long deadline = System.nanoTime() + 3000000000L;

      while (true) {
         FileChannel channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
         FileLock held = null;

         try {
            held = channel.tryLock();
         } catch (OverlappingFileLockException var7) {
         } catch (IOException e) {
            channel.close();
            throw e;
         }

         if (held != null) {
            this.lockChannel = channel;
            this.fileLock = held;
            return true;
         }

         channel.close();
         if (System.nanoTime() - deadline > 0L) {
            this.disabled = true;
            warn("cache folder " + this.dir + " is in use by another game instance, tiles will not be cached", null);
            return false;
         }

         try {
            Thread.sleep(50L);
         } catch (InterruptedException var9) {
         }
      }
   }

   private byte[] metaBytes() {
      String escaped = this.fingerprint.replace("\\", "\\\\").replace("\r", "\\r").replace("\n", "\\n");
      return ("farlands tile cache\nformat=8\nregion=16\nfingerprint=" + escaped + "\n").getBytes(StandardCharsets.UTF_8);
   }

   private void startCleanup() {
      Thread thread = new Thread(this::cleanup, "Farlands Cache Cleanup");
      thread.setDaemon(true);
      thread.setPriority(1);
      thread.start();
   }

   private void cleanup() {
      try {
         Thread.sleep(3000L);
         if (this.finished) {
            return;
         }

         List<Path> folders = new ArrayList<>();

         try (DirectoryStream<Path> stream = Files.newDirectoryStream(this.root)) {
            for (Path path : stream) {
               if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                  folders.add(path);
               }
            }
         }

         int removed = 0;

         for (Path folder : folders) {
            if (this.finished) {
               break;
            }

            String name = folder.getFileName().toString();
            if (OLD_LEVEL_DIR.matcher(name).matches()) {
               removed += deleteOldTiles(folder);
            } else if (DATA_DIR.matcher(name).matches() && !folder.equals(this.dir) && !inUse(folder)) {
               removed += deleteDataDir(folder);
            }
         }

         if (removed > 0) {
            Farlands.LOG.info("Tile cache: removed {} outdated cache files in {}", removed, this.root);
         }
      } catch (InterruptedException var8) {
      } catch (IOException | RuntimeException e) {
         warn("cleanup of outdated cache data in " + this.root + " failed: " + e, null);
      }
   }

   private static boolean inUse(Path folder) {
      synchronized (OPEN) {
         return OPEN.containsKey(folder) || ENDING.containsKey(folder);
      }
   }

   private static int deleteOldTiles(Path folder) throws IOException {
      final int[] count = new int[1];
      Files.walkFileTree(folder, EnumSet.noneOf(FileVisitOption.class), 3, new SimpleFileVisitor<Path>() {
         public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            String name = file.getFileName().toString();
            if (attrs.isRegularFile() && (name.endsWith(".bin") || name.endsWith(".tmp")) && TileStore.deleteQuietly(file)) {
               count[0]++;
            }

            return FileVisitResult.CONTINUE;
         }

         public FileVisitResult visitFileFailed(Path file, IOException e) {
            return FileVisitResult.CONTINUE;
         }

         public FileVisitResult postVisitDirectory(Path dir, @Nullable IOException e) {
            try {
               Files.deleteIfExists(dir);
            } catch (IOException var4) {
            }

            return FileVisitResult.CONTINUE;
         }
      });
      return count[0];
   }

   private static int deleteDataDir(Path folder) throws IOException {
      Path lockPath = folder.resolve("cache.lock");
      int count = 0;

      try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
         FileLock held;
         try {
            held = channel.tryLock();
         } catch (OverlappingFileLockException e) {
            return 0;
         }

         if (held == null) {
            return 0;
         }

         try {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder)) {
               for (Path path : stream) {
                  String name = path.getFileName().toString();
                  if ((name.endsWith(".prtr") || name.endsWith(".compact") || name.equals("cache.meta") || name.equals("cache.meta.tmp")) && deleteQuietly(path)
                     )
                   {
                     count++;
                  }
               }
            }

            deleteQuietly(lockPath);
         } finally {
            held.release();
         }
      }

      deleteQuietly(lockPath);
      deleteQuietly(folder);
      return count;
   }

   private static boolean deleteQuietly(Path path) {
      try {
         return Files.deleteIfExists(path);
      } catch (DirectoryNotEmptyException e) {
         return false;
      } catch (IOException e) {
         return false;
      }
   }

   private void terminate(@Nullable TileCodec codec) {
      this.queueLock.lock();

      try {
         this.finished = true;
         if (!this.pending.isEmpty()) {
            this.dropped.addAndGet(this.pending.size());
            warn(this.pending.size() + " queued tiles were not cached for " + this.dir, null);
         }

         this.pending.clear();
         this.order.clear();
         this.busy = false;
         this.idle.signalAll();
         this.space.signalAll();
      } finally {
         this.queueLock.unlock();
      }

      synchronized (OPEN) {
         OPEN.remove(this.dir, this);
         ENDING.put(this.dir, this);
      }

      this.shut = true;
      if (codec != null) {
         codec.end();
      }

      for (TileRegion region : this.regions.values()) {
         region.close();
      }

      try {
         FileLock held = this.fileLock;
         if (held != null) {
            held.release();
         }
      } catch (IOException var12) {
      }

      try {
         FileChannel channel = this.lockChannel;
         if (channel != null) {
            channel.close();
         }
      } catch (IOException var11) {
      }

      this.endInflaters();
      synchronized (OPEN) {
         ENDING.remove(this.dir, this);
      }

      this.terminated.countDown();
   }

   private static void shutdownAll() {
      shutdown = true;
      List<TileStore> live;
      synchronized (OPEN) {
         live = new ArrayList<>(OPEN.values());
         live.addAll(ENDING.values());
      }

      for (TileStore store : live) {
         store.wake();
      }

      long deadline = System.nanoTime() + 3000000000L;

      for (TileStore store : live) {
         long left = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
         if (left <= 0L) {
            break;
         }

         store.awaitTerminated(left);
      }
   }

   static String describe(long key) {
      return "L" + LodTile.keyLevel(key) + " " + LodTile.keyX(key) + "," + LodTile.keyZ(key);
   }

   static void warn(String message, @Nullable Throwable error) {
      long now = System.nanoTime();
      long next = NEXT_WARN.get();
      if (now - next >= 0L && NEXT_WARN.compareAndSet(next, now + 30000000000L)) {
         long skipped = SUPPRESSED.getAndSet(0L);
         String text = skipped > 0L ? "Tile cache: " + message + " (" + skipped + " similar messages suppressed)" : "Tile cache: " + message;
         if (error != null) {
            Farlands.LOG.warn(text, error);
         } else {
            Farlands.LOG.warn(text);
         }
      } else {
         SUPPRESSED.incrementAndGet();
      }
   }

   private static final class Entry {
      LodTile tile;
      long since;

      Entry(LodTile tile, long since) {
         this.tile = tile;
         this.since = since;
      }
   }

   private record Job(long key, @Nullable LodTile tile) {
   }
}
