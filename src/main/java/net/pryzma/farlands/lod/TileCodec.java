package net.pryzma.farlands.lod;

import java.util.Arrays;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import org.jspecify.annotations.Nullable;

final class TileCodec {
   static final int BLOB_MAGIC = 1448362572;
   static final int BLOB_HEAD = 24;
   static final int FLAG_COMPLETE = 2;
   static final int MAX_SPANS = 4194304;
   static final int MAX_RAW = 33576228;
   static final int MAX_PACKED = 33609081;
   static final int MAX_BLOB = 33609105;
   private static final long MAX_RATIO = 1032L;
   private final Deflater deflater = new Deflater(1);
   private final CRC32 crc = new CRC32();
   private byte[] raw = new byte[65536];
   private byte[] blob = new byte[65536];
   private int rawLength;
   private int blobLength;
   private int blobCrc;

   byte[] blob() {
      return this.blob;
   }

   int blobLength() {
      return this.blobLength;
   }

   int blobCrc() {
      return this.blobCrc;
   }

   @Nullable String encode(LodTile tile) {
      int[] offsets = tile.offsets;
      long[] spans = tile.spans;
      if (offsets.length == 4357 && offsets[0] == 0 && offsets[4356] == spans.length) {
         if (spans.length > 4194304) {
            return "tile too large (" + spans.length + " spans)";
         }

         int need = 21796 + spans.length * 8;
         if (this.raw.length < need) {
            this.raw = new byte[Math.min(33576228, Math.max(need, this.raw.length * 2))];
         }

         byte[] out = this.raw;
         int p = putLong(out, 0, tile.realMask);
         p = putInt(out, p, 4356);

         for (int c = 0; c < 4356; c++) {
            int count = offsets[c + 1] - offsets[c];
            if (count < 0) {
               return "inconsistent tile layout";
            }

            while ((count & -128) != 0) {
               out[p++] = (byte)(count & 127 | 128);
               count >>>= 7;
            }

            out[p++] = (byte)count;
         }

         p = putInt(out, p, spans.length);
         int n = spans.length;

         for (int b = 0; b < 8; b++) {
            int shift = 56 - 8 * b;

            for (int i = 0; i < n; i++) {
               out[p++] = (byte)(spans[i] >>> shift);
            }
         }

         this.rawLength = p;
         int bound = 24 + p + (p >> 10) + 64;
         if (this.blob.length < bound) {
            this.blob = new byte[bound];
         }

         this.deflater.reset();
         this.deflater.setInput(out, 0, p);
         this.deflater.finish();

         int at;
         for (at = 24; !this.deflater.finished(); at += this.deflater.deflate(this.blob, at, this.blob.length - at)) {
            if (at == this.blob.length) {
               if (this.blob.length >= 33609105) {
                  return "tile does not compress";
               }

               this.blob = Arrays.copyOf(this.blob, Math.min(33609105, this.blob.length * 2));
            }
         }

         int packed = at - 24;
         if (packed > 33609081) {
            return "tile does not compress";
         }

         putInt(this.blob, 0, 1448362572);
         putLong(this.blob, 4, tile.key());
         putInt(this.blob, 12, tile.complete ? 2 : 0);
         putInt(this.blob, 16, this.rawLength);
         putInt(this.blob, 20, packed);
         this.crc.reset();
         this.crc.update(this.blob, 0, at);
         this.blobCrc = (int)this.crc.getValue();
         this.blobLength = at;
         return null;
      } else {
         return "inconsistent tile layout";
      }
   }

   void end() {
      this.deflater.end();
   }

   static int crc(byte[] data, int from, int length) {
      CRC32 crc = new CRC32();
      crc.update(data, from, length);
      return (int)crc.getValue();
   }

   static boolean verify(long key, byte[] blob, int expectedCrc) {
      return blob.length >= 24 && crc(blob, 0, blob.length) == expectedCrc && getInt(blob, 0) == 1448362572 && getLong(blob, 4) == key;
   }

   static LodTile decode(long key, byte[] blob, int expectedCrc, Inflater inflater) throws TileCodec.Corrupt {
      if (blob.length < 24) {
         throw new TileCodec.Corrupt("short record");
      }

      if (crc(blob, 0, blob.length) != expectedCrc) {
         throw new TileCodec.Corrupt("checksum mismatch");
      }

      if (getInt(blob, 0) != 1448362572) {
         throw new TileCodec.Corrupt("bad record magic");
      }

      if (getLong(blob, 4) != key) {
         throw new TileCodec.Corrupt("record belongs to another tile");
      }

      int flags = getInt(blob, 12);
      int rawLength = getInt(blob, 16);
      int packed = getInt(blob, 20);
      if ((flags & -3) != 0) {
         throw new TileCodec.Corrupt("unknown record flags");
      }

      if (packed != blob.length - 24) {
         throw new TileCodec.Corrupt("record length mismatch");
      }

      if (rawLength >= 16 && rawLength <= 33576228 && rawLength <= packed * 1032L + 1024L) {
         byte[] raw = new byte[rawLength];
         inflater.reset();
         inflater.setInput(blob, 24, packed);

         try {
            int p = 0;
            int stalls = 0;

            while (p < rawLength) {
               int n = inflater.inflate(raw, p, rawLength - p);
               if (n == 0) {
                  if (inflater.finished() || inflater.needsInput() || inflater.needsDictionary() || ++stalls > 2) {
                     throw new TileCodec.Corrupt("truncated tile data");
                  }
               } else {
                  stalls = 0;
               }

               p += n;
            }

            if (!inflater.finished() && (inflater.inflate(new byte[1]) != 0 || !inflater.finished())) {
               throw new TileCodec.Corrupt("trailing tile data");
            }

            if (inflater.getRemaining() != 0) {
               throw new TileCodec.Corrupt("trailing record bytes");
            }
         } catch (DataFormatException e) {
            throw new TileCodec.Corrupt("bad compressed data");
         }

         long realMask = getLong(raw, 0);
         if (getInt(raw, 8) != 4356) {
            throw new TileCodec.Corrupt("cell count mismatch");
         }

         int q = 12;
         int[] offsets = new int[4357];
         int total = 0;
         int c = 0;

         label113:
         while (c < 4356) {
            int value = 0;

            for (int shift = 0; q < rawLength && shift <= 28; shift += 7) {
               int b = raw[q++] & 255;
               value |= (b & 127) << shift;
               if ((b & 128) == 0) {
                  if (value >= 0 && value <= 4194304 - total) {
                     offsets[c] = total;
                     total += value;
                     c++;
                     continue label113;
                  }

                  throw new TileCodec.Corrupt("bad cell table");
               }
            }

            throw new TileCodec.Corrupt("bad cell table");
         }

         offsets[4356] = total;
         if (q + 4 <= rawLength && getInt(raw, q) == total) {
            q += 4;
            if ((long)rawLength - q != 8L * total) {
               throw new TileCodec.Corrupt("span data length mismatch");
            }

            long[] spans = new long[total];

            for (int b = 0; b < 8; b++) {
               int shift = 56 - 8 * b;

               for (int i = 0; i < total; i++) {
                  spans[i] |= (raw[q++] & 255L) << shift;
               }
            }

            try {
               return new LodTile(LodTile.keyLevel(key), LodTile.keyX(key), LodTile.keyZ(key), offsets, spans, realMask, (flags & 2) != 0);
            } catch (RuntimeException e) {
               throw new TileCodec.Corrupt("invalid tile content");
            }
         } else {
            throw new TileCodec.Corrupt("span count mismatch");
         }
      } else {
         throw new TileCodec.Corrupt("implausible tile size " + rawLength);
      }
   }

   static int putInt(byte[] out, int at, int value) {
      out[at] = (byte)(value >>> 24);
      out[at + 1] = (byte)(value >>> 16);
      out[at + 2] = (byte)(value >>> 8);
      out[at + 3] = (byte)value;
      return at + 4;
   }

   static int putLong(byte[] out, int at, long value) {
      putInt(out, at, (int)(value >>> 32));
      putInt(out, at + 4, (int)value);
      return at + 8;
   }

   static int getInt(byte[] in, int at) {
      return (in[at] & 0xFF) << 24 | (in[at + 1] & 0xFF) << 16 | (in[at + 2] & 0xFF) << 8 | in[at + 3] & 0xFF;
   }

   static long getLong(byte[] in, int at) {
      return (long)getInt(in, at) << 32 | getInt(in, at + 4) & 4294967295L;
   }

   static final class Corrupt extends Exception {
      Corrupt(String message) {
         super(message, null, false, false);
      }
   }
}
