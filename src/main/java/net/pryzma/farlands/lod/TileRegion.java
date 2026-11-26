package net.pryzma.farlands.lod;

import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.jspecify.annotations.Nullable;

final class TileRegion {
   static final int SHIFT = 4;
   static final int SIDE = 16;
   static final int SLOTS = 256;
   static final int MAGIC = 1448366663;
   static final int FILE_HEAD = 64;
   static final int SLOT_BYTES = 32;
   static final int HEADER_BYTES = 8256;
   static final int FLAG_PRESENT = 1;
   static final String SUFFIX = ".prtr";
   static final String COMPACT_SUFFIX = ".compact";
   static final long COMPACT_MIN_DEAD = 1048576L;
   final TileStore store;
   final int level;
   final int rx;
   final int rz;
   final String name;
   final Path path;
   private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
   private final AtomicReferenceArray<TileRegion.Slot> slots = new AtomicReferenceArray<>(256);
   private volatile boolean loaded;
   private volatile boolean exists;
   private volatile boolean broken;
   private volatile @Nullable RandomAccessFile file;
   private volatile long end;
   volatile long lastUse;
   long compactAfter = System.nanoTime();

   TileRegion(TileStore store, long regionKey) {
      this.store = store;
      this.level = LodTile.keyLevel(regionKey);
      this.rx = LodTile.keyX(regionKey);
      this.rz = LodTile.keyZ(regionKey);
      this.name = this.level + "." + this.rx + "." + this.rz + ".prtr";
      this.path = store.dir.resolve(this.name);
   }

   static long regionKey(long tileKey) {
      return LodTile.key(LodTile.keyLevel(tileKey), LodTile.keyX(tileKey) >> 4, LodTile.keyZ(tileKey) >> 4);
   }

   static int slotIndex(long tileKey) {
      return (LodTile.keyZ(tileKey) & 15) << 4 | LodTile.keyX(tileKey) & 15;
   }

   void ensureLoaded() {
      if (!this.loaded) {
         synchronized (this) {
            if (!this.loaded) {
               if (this.store.knownFile(this.name)) {
                  this.loadHeader();
               }

               this.loaded = true;
            }
         }
      }
   }

   private void loadHeader() {
      if (!Files.isRegularFile(this.path)) {
         this.exists = false;
      } else {
         this.exists = true;

         try {
            try (RandomAccessFile f = new RandomAccessFile(this.path.toFile(), "r")) {
               long size = f.length();
               this.end = size;
               if (size < 8256L) {
                  this.broken = true;
                  this.store.corrupt("region header truncated", this.path);
                  return;
               }

               byte[] head = new byte[8256];
               f.readFully(head);
               if (TileCodec.getInt(head, 0) == 1448366663
                  && TileCodec.getInt(head, 4) == 8
                  && TileCodec.getInt(head, 8) == this.level
                  && TileCodec.getInt(head, 12) == this.rx
                  && TileCodec.getInt(head, 16) == this.rz
                  && TileCodec.getInt(head, 20) == 16
                  && TileCodec.crc(head, 0, 60) == TileCodec.getInt(head, 60)) {
                  int bad = 0;

                  for (int i = 0; i < 256; i++) {
                     int at = 64 + i * 32;
                     if (!zero(head, at, 32)) {
                        long offset = TileCodec.getLong(head, at);
                        int length = TileCodec.getInt(head, at + 8);
                        int flags = TileCodec.getInt(head, at + 12);
                        int crc = TileCodec.getInt(head, at + 16);
                        if (TileCodec.crc(head, at, 28) == TileCodec.getInt(head, at + 28)
                           && (flags & 1) != 0
                           && (flags & -4) == 0
                           && offset >= 8256L
                           && length >= 24
                           && length <= 33609105
                           && offset <= size - length) {
                           this.slots.set(i, new TileRegion.Slot(offset, length, crc, (flags & 2) != 0));
                        } else {
                           bad++;
                        }
                     }
                  }

                  if (bad > 0) {
                     this.store.corrupt(bad + " damaged slot(s) ignored", this.path);
                  }

                  return;
               }

               this.broken = true;
               this.store.corrupt("region header invalid", this.path);
            }

            return;
         } catch (FileNotFoundException | NoSuchFileException e) {
            this.exists = false;
         } catch (IOException e) {
            this.broken = true;
            this.store.corrupt("region unreadable: " + e, this.path);
         }
      }
   }

   static long tileKey(int level, int rx, int rz, int index) {
      return LodTile.key(level, rx << 4 | index & 15, rz << 4 | index >>> 4);
   }

   private static boolean zero(byte[] data, int from, int length) {
      for (int i = from; i < from + length; i++) {
         if (data[i] != 0) {
            return false;
         }
      }

      return true;
   }

   TileRegion.@Nullable Slot slot(int index) {
      return this.slots.get(index);
   }

   void invalidate(int index, TileRegion.Slot slot) {
      this.slots.compareAndSet(index, slot, null);
   }

   TileRegion.@Nullable Blob read(int index) throws IOException {
      this.lock.readLock().lock();

      try {
         TileRegion.Slot slot = this.slots.get(index);
         if (slot == null) {
            return null;
         }

         RandomAccessFile f = this.file();
         if (f == null) {
            return null;
         }

         byte[] data = new byte[slot.length()];

         try {
            synchronized (f) {
               f.seek(slot.offset());
               f.readFully(data);
            }
         } catch (EOFException e) {
            this.invalidate(index, slot);
            throw e;
         }

         this.lastUse = System.nanoTime();
         return new TileRegion.Blob(slot, data);
      } finally {
         this.lock.readLock().unlock();
      }
   }

   private @Nullable RandomAccessFile file() throws IOException {
      RandomAccessFile known = this.file;
      if (known != null) {
         return known;
      }

      synchronized (this) {
         if (this.file == null) {
            if (this.store.isShut() || !this.exists || !Files.isRegularFile(this.path)) {
               return null;
            }

            this.file = new RandomAccessFile(this.path.toFile(), "rw");
            this.store.fileOpened();
         }

         return this.file;
      }
   }

   boolean isOpen() {
      return this.file != null;
   }

   void append(int index, byte[] blob, int length, int crc, boolean complete) throws IOException {
      if (!this.exists || this.broken) {
         this.recreate();
      }

      RandomAccessFile f = this.file();
      if (f == null) {
         throw new IOException("region file vanished");
      }

      long offset = this.end;
      synchronized (f) {
         f.seek(offset);
         f.write(blob, 0, length);
      }

      this.end = offset + length;
      TileRegion.Slot slot = new TileRegion.Slot(offset, length, crc, complete);
      this.slots.set(index, slot);
      byte[] bytes = slotBytes(slot);
      synchronized (f) {
         f.seek(64L + index * 32L);
         f.write(bytes);
      }

      this.lastUse = System.nanoTime();
   }

   private void recreate() throws IOException {
      this.lock.writeLock().lock();

      try {
         this.closeQuietly();

         for (int i = 0; i < 256; i++) {
            this.slots.set(i, null);
         }

         RandomAccessFile f = new RandomAccessFile(this.path.toFile(), "rw");

         try {
            f.setLength(0L);
            f.write(this.emptyHeader());
         } catch (IOException e) {
            f.close();
            throw e;
         }

         this.file = f;
         this.store.fileOpened();
         this.exists = true;
         this.broken = false;
         this.end = 8256L;
      } finally {
         this.lock.writeLock().unlock();
      }
   }

   private byte[] emptyHeader() {
      byte[] head = new byte[8256];
      TileCodec.putInt(head, 0, 1448366663);
      TileCodec.putInt(head, 4, 8);
      TileCodec.putInt(head, 8, this.level);
      TileCodec.putInt(head, 12, this.rx);
      TileCodec.putInt(head, 16, this.rz);
      TileCodec.putInt(head, 20, 16);
      TileCodec.putInt(head, 60, TileCodec.crc(head, 0, 60));
      return head;
   }

   static byte[] slotBytes(TileRegion.Slot slot) {
      byte[] out = new byte[32];
      TileCodec.putLong(out, 0, slot.offset());
      TileCodec.putInt(out, 8, slot.length());
      TileCodec.putInt(out, 12, 1 | (slot.complete() ? 2 : 0));
      TileCodec.putInt(out, 16, slot.crc());
      TileCodec.putInt(out, 28, TileCodec.crc(out, 0, 28));
      return out;
   }

   long liveBytes() {
      long live = 0L;

      for (int i = 0; i < 256; i++) {
         TileRegion.Slot slot = this.slots.get(i);
         if (slot != null) {
            live += slot.length();
         }
      }

      return live;
   }

   long fileBytes() {
      return this.exists ? this.end : 0L;
   }

   boolean needsCompaction() {
      if (this.exists && !this.broken) {
         long live = this.liveBytes();
         long dead = this.end - 8256L - live;
         return dead > 1048576L && dead > live;
      } else {
         return false;
      }
   }

   void compact() throws IOException {
      RandomAccessFile src = this.file();
      if (src != null) {
         Path temp = this.path.resolveSibling(this.name + ".compact");
         TileRegion.Slot[] before = new TileRegion.Slot[256];
         TileRegion.Slot[] after = new TileRegion.Slot[256];
         long position = 8256L;

         try (RandomAccessFile out = new RandomAccessFile(temp.toFile(), "rw")) {
            out.setLength(0L);
            out.write(this.emptyHeader());

            for (int i = 0; i < 256; i++) {
               TileRegion.Slot slot = this.slots.get(i);
               if (slot != null) {
                  before[i] = slot;
                  byte[] data = new byte[slot.length()];

                  try {
                     synchronized (src) {
                        src.seek(slot.offset());
                        src.readFully(data);
                     }
                  } catch (EOFException e) {
                     continue;
                  }

                  if (!TileCodec.verify(tileKey(this.level, this.rx, this.rz, i), data, slot.crc())) {
                     this.store.corrupt("damaged record dropped during compaction", this.path);
                  } else {
                     out.write(data);
                     after[i] = new TileRegion.Slot(position, slot.length(), slot.crc(), slot.complete());
                     position += slot.length();
                  }
               }
            }

            for (int i = 0; i < 256; i++) {
               if (after[i] != null) {
                  out.seek(64L + i * 32L);
                  out.write(slotBytes(after[i]));
               }
            }

            out.getFD().sync();
         } catch (IOException e) {
            Files.deleteIfExists(temp);
            throw e;
         }

         this.lock.writeLock().lock();

         try {
            this.closeQuietly();

            try {
               Files.move(temp, this.path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
               Files.deleteIfExists(temp);
               throw e;
            }

            for (int i = 0; i < 256; i++) {
               if (!this.slots.compareAndSet(i, before[i], after[i]) && this.slots.get(i) != null) {
                  this.slots.set(i, null);
               }
            }

            this.end = position;
         } finally {
            this.lock.writeLock().unlock();
         }
      }
   }

   boolean tryClose() {
      if (!this.lock.writeLock().tryLock()) {
         return false;
      }

      try {
         this.closeQuietly();
         return true;
      } finally {
         this.lock.writeLock().unlock();
      }
   }

   void close() {
      this.lock.writeLock().lock();

      try {
         this.closeQuietly();
      } finally {
         this.lock.writeLock().unlock();
      }
   }

   private void closeQuietly() {
      synchronized (this) {
         RandomAccessFile f = this.file;
         if (f != null) {
            this.file = null;
            this.store.fileClosed();

            try {
               f.close();
            } catch (IOException var5) {
            }
         }
      }
   }

   record Blob(TileRegion.Slot slot, byte[] data) {
   }

   record Slot(long offset, int length, int crc, boolean complete) {
   }
}
