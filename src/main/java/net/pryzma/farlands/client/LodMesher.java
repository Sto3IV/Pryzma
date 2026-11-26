package net.pryzma.farlands.client;

import net.pryzma.farlands.lod.LodTile;
import net.pryzma.farlands.lod.Span;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.Registry;
import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

public final class LodMesher {
   public static final int QUAD_BYTES = 16;
   public static final int SHADER_QUAD_BYTES = 64;
   public static final int WATER_QUAD_BYTES = 16;
   public static final int VANILLA_WATER_QUAD_BYTES = 112;
   public static final int WATER_PATCH_INTS = 5;
   private static final float[] WATER_SHADE = new float[]{0.5F, 1.0F, 0.8F, 0.8F, 0.6F, 0.6F};
   private static final int LAVA_MATERIAL = 6;
   private static final int DOWN = 0;
   private static final int UP = 1;
   private static final int NORTH = 2;
   private static final int SOUTH = 3;
   private static final int WEST = 4;
   private static final int EAST = 5;
   private static final int[] DX = new int[]{0, 0, 0, 0, -1, 1};
   private static final int[] DZ = new int[]{0, 0, -1, 1, 0, 0};
   private static final int FLAG_CUTOUT = 1;
   private static final int FLAG_FLUID = 2;
   private static final int FLAG_PLANT = 4;
   private static final int SEGMENT_SHIFT = 4;
   private static final int FACE_CROSS_A = 6;
   private static final int FACE_CROSS_B = 7;
   private static final int MIRRORED = 8;
   private static final int CELLS = 4096;
   public static final int BUCKETS = 7;
   public static final int[] BUCKET_FACES = new int[]{2, 4, 1, 6, 5, 3, 0};
   private static final int[] BUCKET_OF = new int[]{6, 2, 0, 5, 1, 4, 3, 3};
   public static final int SUBS = 16;
   public static final int PARTS = 32;
   public static final int ALL_PARTS = -1;
   public static final int GROUPS = 224;
   public static final int COMPACT_BYTES = 8;
   private static final int COMPACT_Y_RANGE = 8192;
   private static final int COMPACT_MATERIALS = 32768;
   private final BlockLooks looks;
   private final SpriteTable sprites;
   private final LodMesher.Quads solid = new LodMesher.Quads();
   private final LodMesher.Quads fluid = new LodMesher.Quads();
   private ByteBuffer water = MemoryUtil.memAlloc(16384);
   private int[] waterPatch = new int[1024];
   private int waterCount;
   private boolean waterLayer;
   private boolean vanillaWater;
   private int patchCells = 64;
   private final float[] tiledRect = new float[4];
   private final float[] waterRect = new float[4];
   private final int[] lights = new int[4];
   private final long[] solidTops = new long[4096];
   private final long[] fluidTops = new long[4096];
   private final boolean[] used = new boolean[4096];
   private final int[] xs = new int[4];
   private final int[] ys = new int[4];
   private final int[] zs = new int[4];
   private LodMesher.Format format = LodMesher.Format.FARLANDS;
   private int slot;
   private int scale;
   private Registry<Biome> biomes;
   private static final int TINT_GRIDS = 6;
   private final long[] tintKeys = new long[6];
   private final int[] tintGen = new int[6];
   private final int[][] tintGrid = new int[6][4356];
   private final int[][] tintStamp = new int[6][4356];
   private int tintCounter;
   private int tintNext;
   private @Nullable LodTile tintTile;
   private final @Nullable AtomicBoolean compactFlag;
   private final @Nullable AtomicBoolean vanillaWaterFlag;
   private int floor = 2047;
   private int lowest16 = Integer.MAX_VALUE;
   public static final int OCCLUDER_INTS = 5;
   private long[] wallKeys = new long[0];
   private long[] wallExtra = new long[0];
   private int[] wallOrder = new int[0];
   private int[] wallSorted = new int[0];
   private boolean[] wallRemoved = new boolean[0];
   private int[] bucketKeys = new int[0];
   private final Long2IntOpenHashMap compactMaterialIds = newMaterialIds();
   private final LongArrayList compactOrder = new LongArrayList();
   private int[] compactIndex = new int[0];
   private final int[] waterCounts = new int[4096];
   private final int[] waterStarts = new int[4096];
   private final int[] waterFirst = new int[4096];
   public static final int GROUPS_PER_SUB = 16;
   public static final int GROUP_BASE = 80;
   public static final int OCCLUDER_TOTAL = 592;
   private int[] band = new int[16];
   private int[] column = new int[16];
   private int[] merged = new int[16];
   private int compactMaterials;
   private int compactBase;

   public LodMesher(BlockLooks looks, SpriteTable sprites) {
      this(looks, sprites, null, null);
   }

   public LodMesher(BlockLooks looks, SpriteTable sprites, @Nullable AtomicBoolean compactFlag) {
      this(looks, sprites, compactFlag, null);
   }

   public LodMesher(BlockLooks looks, SpriteTable sprites, @Nullable AtomicBoolean compactFlag, @Nullable AtomicBoolean vanillaWaterFlag) {
      this.looks = looks;
      this.sprites = sprites;
      this.compactFlag = compactFlag;
      this.vanillaWaterFlag = vanillaWaterFlag;
   }

   public boolean vanillaWater() {
      return this.vanillaWater;
   }

   public LodMesher.Mesh build(LodTile tile, int slot, Registry<Biome> biomes, LodMesher.Format format, boolean waterLayer) {
      return this.build(tile, slot, biomes, format, waterLayer, this.compactFlag != null && this.compactFlag.get(), 2047);
   }

   public LodMesher.Mesh build(LodTile tile, int slot, Registry<Biome> biomes, LodMesher.Format format, boolean waterLayer, int floor) {
      return this.build(tile, slot, biomes, format, waterLayer, this.compactFlag != null && this.compactFlag.get(), floor);
   }

   public int lowest() {
      return this.lowest16 == Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.floorDiv(this.lowest16, 16);
   }

   public LodMesher.Mesh build(LodTile tile, int slot, Registry<Biome> biomes, LodMesher.Format format, boolean waterLayer, boolean compact) {
      return this.build(tile, slot, biomes, format, waterLayer, compact, 2047);
   }

   public LodMesher.Mesh build(LodTile tile, int slot, Registry<Biome> biomes, LodMesher.Format format, boolean waterLayer, boolean compact, int floor) {
      this.floor = floor;
      this.lowest16 = Integer.MAX_VALUE;
      this.solid.reset();
      this.fluid.reset();
      this.water.clear();
      this.waterCount = 0;
      this.waterLayer = waterLayer && format == LodMesher.Format.FARLANDS;
      this.vanillaWater = this.waterLayer && this.vanillaWaterFlag != null && this.vanillaWaterFlag.get();
      this.format = format;
      this.slot = slot;
      this.biomes = biomes;
      this.scale = 1 << tile.level;
      this.patchCells = this.scale <= 16 ? 16 / this.scale : 4;
      int skirt = Math.max(2, this.scale * 2);
      Arrays.fill(this.solidTops, Long.MIN_VALUE);
      Arrays.fill(this.fluidTops, Long.MIN_VALUE);

      for (int z = 0; z < 64; z++) {
         for (int x = 0; x < 64; x++) {
            this.column(tile, x, z, skirt);
         }
      }

      this.mergeTops(this.solidTops, this.solid, 64);
      if (!this.shader()) {
         this.mergeWalls();
      }

      int below = this.fluid.count;
      this.mergeTops(this.fluidTops, this.fluid, this.waterLayer ? this.patchCells : 64);
      int total = this.solid.count + this.fluid.count;
      ByteBuffer out = MemoryUtil.memAlloc(Math.max(total, 1) * (this.shader() ? 64 : 16));
      int[] buckets = new int[7];
      int[] groups = new int[224];
      int[] bounds = new int[64];
      if (this.shader()) {
         this.solid.buffer.flip();
         out.put(this.solid.buffer);
      } else {
         this.bucketSolid(out, buckets, groups, bounds, this.cuts(tile));
      }

      this.fluid.buffer.flip();
      out.put(this.fluid.buffer);
      out.flip();
      int patchCount = this.waterCount == 0 ? 0 : this.patchTotal();
      int[] patches = new int[patchCount * 5];
      ByteBuffer waterOut = this.waterCount == 0 ? null : this.sortWater(patches);
      if (compact && !this.shader() && this.fluid.count == 0 && total > 0) {
         ByteBuffer packed = this.compact(out, total);
         if (packed != null) {
            int materials = this.compactMaterials;
            int yBase = this.compactBase;
            MemoryUtil.memFree(out);
            return new LodMesher.Mesh(
               packed, total, 0, 0, format, waterOut, this.waterCount, patches, buckets, groups, bounds, materials, yBase, this.occluders(tile)
            );
         }
      }

      return new LodMesher.Mesh(
         out,
         total,
         this.fluid.count,
         this.fluid.count - below,
         format,
         waterOut,
         this.waterCount,
         patches,
         buckets,
         groups,
         bounds,
         0,
         0,
         this.shader() ? new int[0] : this.occluders(tile)
      );
   }

   private static Long2IntOpenHashMap newMaterialIds() {
      Long2IntOpenHashMap map = new Long2IntOpenHashMap();
      map.defaultReturnValue(-1);
      return map;
   }

   private int[] occluders(LodTile tile) {
      int[] out = new int[592];

      for (int sub = 0; sub < 16; sub++) {
         int x0 = (sub & 3) * 16;
         int z0 = (sub >> 2) * 16;
         this.subBand(tile, x0, z0, 16, false, out, sub * 5);
         this.subBand(tile, x0, z0, 16, true, out, sub * 5 + 2);

         for (int group = 0; group < 16; group++) {
            this.subBand(tile, x0 + (group & 3) * 4, z0 + (group >> 2) * 4, 4, true, out, 80 + (sub * 16 + group) * 2);
         }

         long real = 3L << z0 / 8 * 8 + x0 / 8 | 3L << (z0 / 8 + 1) * 8 + x0 / 8;
         out[sub * 5 + 4] = (tile.realMask & real) == real ? 1 : 0;
      }

      return out;
   }

   private void subBand(LodTile tile, int x0, int z0, int width, boolean leaves, int[] out, int at) {
      int bands = -1;

      for (int z = z0; z < z0 + width && bands != 0; z++) {
         for (int x = x0; x < x0 + width && bands != 0; x++) {
            int runs = this.solidRuns(tile, x, z, leaves);
            if (bands < 0) {
               if (this.band.length < runs * 2) {
                  this.band = new int[runs * 2];
               }

               System.arraycopy(this.column, 0, this.band, 0, runs * 2);
               bands = runs;
            } else {
               bands = this.intersect(bands, runs);
            }
         }
      }

      int lo = 1;
      int hi = 0;

      for (int i = 0; i < bands; i++) {
         if (this.band[i * 2 + 1] > hi || lo > hi) {
            lo = this.band[i * 2];
            hi = this.band[i * 2 + 1];
         }
      }

      out[at] = lo;
      out[at + 1] = hi;
   }

   private int solidRuns(LodTile tile, int x, int z, boolean leaves) {
      int from = tile.first(x, z);
      int count = tile.count(x, z);
      int runs = 0;

      for (int i = 0; i < count; i++) {
         long span = tile.spans[from + i];
         int top = Span.top(span);
         int bottom = Span.bottom(span);
         BlockLooks.Look look = this.occupant(span);
         if (!look.plant() && !look.fluid() && !look.translucent() && (!look.cutout() || leaves) && (look.height() >= 16 || top - bottom != 1)) {
            if (runs > 0 && this.column[runs * 2 - 2] == top) {
               this.column[runs * 2 - 2] = bottom;
            } else {
               if (runs * 2 + 2 > this.column.length) {
                  this.column = Arrays.copyOf(this.column, this.column.length * 2);
               }

               this.column[runs * 2] = bottom;
               this.column[runs * 2 + 1] = top;
               runs++;
            }
         }
      }

      return runs;
   }

   private int intersect(int bands, int runs) {
      if (this.merged.length < (bands + runs) * 2) {
         this.merged = new int[(bands + runs) * 2];
      }

      int n = 0;

      for (int i = 0; i < bands; i++) {
         int lo = this.band[i * 2];
         int hi = this.band[i * 2 + 1];

         for (int j = 0; j < runs; j++) {
            int a = Math.max(lo, this.column[j * 2]);
            int b = Math.min(hi, this.column[j * 2 + 1]);
            if (a < b) {
               this.merged[n * 2] = a;
               this.merged[n * 2 + 1] = b;
               n++;
            }
         }
      }

      if (this.band.length < n * 2) {
         this.band = new int[n * 2];
      }

      System.arraycopy(this.merged, 0, this.band, 0, n * 2);
      return n;
   }

   private void bucketSolid(ByteBuffer out, int[] counts, int[] groups, int[] bounds, int[] cuts) {
      ByteBuffer source = this.solid.buffer;
      int quads = this.solid.count;
      int[] minX = new int[32];
      int[] maxX = new int[32];
      int[] minZ = new int[32];
      int[] maxZ = new int[32];
      int[] minY = new int[32];
      int[] maxY = new int[32];
      Arrays.fill(minX, Integer.MAX_VALUE);
      Arrays.fill(minZ, Integer.MAX_VALUE);
      Arrays.fill(minY, Integer.MAX_VALUE);
      Arrays.fill(maxX, Integer.MIN_VALUE);
      Arrays.fill(maxZ, Integer.MIN_VALUE);
      Arrays.fill(maxY, Integer.MIN_VALUE);
      if (this.bucketKeys.length < quads) {
         this.bucketKeys = new int[Math.max(quads, this.bucketKeys.length * 2)];
      }

      int[] keys = this.bucketKeys;

      for (int q = 0; q < quads; q++) {
         int head = source.getInt(q * 16);
         int y = source.getInt(q * 16 + 8);
         int face = head >>> 24 & 7;
         int x = head & 63;
         int z = head >>> 6 & 63;
         int sub = (z >> 4) * 4 + (x >> 4);
         int low = y & 65535;
         int high = face < 2 ? low : y >>> 16;
         int part = Math.max(low, high) <= cuts[sub] ? sub : sub + 16;
         minX[part] = Math.min(minX[part], x);
         maxX[part] = Math.max(maxX[part], x + (head >>> 12 & 63) + 1);
         minZ[part] = Math.min(minZ[part], z);
         maxZ[part] = Math.max(maxZ[part], z + (head >>> 18 & 63) + 1);
         minY[part] = Math.min(minY[part], Math.min(low, high));
         maxY[part] = Math.max(maxY[part], Math.max(low, high));
         int group = BUCKET_OF[face] * 32 + part;
         keys[q] = group;
         groups[group]++;
         counts[BUCKET_OF[face]]++;
      }

      for (int part = 0; part < 32; part++) {
         if (minX[part] != Integer.MAX_VALUE) {
            bounds[part * 2] = minX[part] | maxX[part] << 8 | minZ[part] << 16 | maxZ[part] << 24;
            bounds[part * 2 + 1] = minY[part] | maxY[part] << 16;
         }
      }

      int[] next = new int[224];

      for (int i = 1; i < 224; i++) {
         next[i] = next[i - 1] + groups[i - 1];
      }

      long from = MemoryUtil.memAddress0(source);
      long to = MemoryUtil.memAddress(out);

      for (int q = 0; q < quads; q++) {
         MemoryUtil.memCopy(from + q * 16L, to + (long)(next[keys[q]]++) * 16L, 16L);
      }

      out.position(out.position() + quads * 16);
   }

   private int[] cuts(LodTile tile) {
      int[] out = new int[16];
      int[] bottoms = new int[256];

      for (int sub = 0; sub < 16; sub++) {
         int x0 = (sub & 3) * 16;
         int z0 = (sub >> 2) * 16;
         int n = 0;

         for (int z = z0; z < z0 + 16; z++) {
            for (int x = x0; x < x0 + 16; x++) {
               int bottom = this.canopyBottom(tile, x, z);
               if (bottom != Integer.MIN_VALUE) {
                  bottoms[n++] = bottom;
               }
            }
         }

         if (n < 64) {
            out[sub] = Integer.MIN_VALUE;
         } else {
            Arrays.sort(bottoms, 0, n);
            out[sub] = (bottoms[n / 5] + 2048) * 16;
         }
      }

      return out;
   }

   private int canopyBottom(LodTile tile, int x, int z) {
      int from = tile.first(x, z);
      int count = tile.count(x, z);
      int canopy = Integer.MIN_VALUE;

      for (int i = 0; i < count; i++) {
         long span = tile.spans[from + i];
         BlockLooks.Look look = this.occupant(span);
         if (look.cutout() && !look.plant() && !look.fluid() && look.height() >= 16) {
            if (canopy != Integer.MIN_VALUE && Span.top(span) < canopy - 2) {
               return canopy;
            }

            canopy = Span.bottom(span);
         } else if (!look.plant() || look.fluid()) {
            return canopy;
         }
      }

      return canopy;
   }

   private void mergeWalls() {
      ByteBuffer buffer = this.solid.buffer;
      int quads = this.solid.count;
      int limit = Math.max(1, Math.min(16, 64 / this.scale));
      if (limit >= 2 && quads >= 2) {
         if (this.wallKeys.length < quads) {
            int size = Math.max(quads, this.wallKeys.length * 2);
            this.wallKeys = new long[size];
            this.wallExtra = new long[size];
            this.wallOrder = new int[size];
            this.wallSorted = new int[size];
            this.wallRemoved = new boolean[size];
         }

         long[] keys = this.wallKeys;
         long[] extra = this.wallExtra;
         int[] order = this.wallOrder;
         int walls = 0;

         for (int q = 0; q < quads; q++) {
            int head = buffer.getInt(q * 16);
            int face = head >>> 24 & 7;
            if (face >= 2 && face <= 5 && (head >>> 12 & 4095) == 0) {
               boolean alongX = face == 2 || face == 3;
               int plane = alongX ? head >>> 6 & 63 : head & 63;
               int along = alongX ? head & 63 : head >>> 6 & 63;
               keys[q] = (long)face << 60 | (long)plane << 54 | (long)(head >>> 28) << 50 | (buffer.getInt(q * 16 + 12) & 4294967295L) << 6 | along;
               extra[q] = (long)buffer.getInt(q * 16 + 4) << 32 | buffer.getInt(q * 16 + 8) & 4294967295L;
               order[walls++] = q;
            }
         }

         if (walls >= 2) {
            int[] sorted = order;
            System.arraycopy(order, 0, this.wallSorted, 0, walls);
            IntArrays.mergeSort(sorted, 0, walls, (a, b) -> {
               long ka = keys[a] & -64L;
               long kb = keys[b] & -64L;
               if (ka != kb) {
                  return Long.compare(ka, kb);
               } else {
                  return extra[a] != extra[b] ? Long.compare(extra[a], extra[b]) : Long.compare(keys[a] & 63L, keys[b] & 63L);
               }
            }, this.wallSorted);
            boolean[] removed = this.wallRemoved;
            Arrays.fill(removed, 0, quads, false);
            int i = 0;

            while (i < walls) {
               int first = sorted[i];
               long group = keys[first] & -64L;
               int length = 1;
               int last = (int)(keys[first] & 63L);

               while (i + length < walls && length < limit) {
                  int next = sorted[i + length];
                  if ((keys[next] & -64L) != group || extra[next] != extra[first] || (int)(keys[next] & 63L) != last + 1) {
                     break;
                  }

                  last++;
                  length++;
               }

               if (length > 1) {
                  int head = buffer.getInt(first * 16);
                  int face = head >>> 24 & 7;
                  int start = (int)(keys[first] & 63L);
                  int merged = face != 2 && face != 3
                     ? head & -4033 & -16515073 | start << 6 | length - 1 << 18
                     : head & -64 & -258049 | start | length - 1 << 12;
                  buffer.putInt(first * 16, merged);

                  for (int k = 1; k < length; k++) {
                     removed[sorted[i + k]] = true;
                  }
               }

               i += length;
            }

            int write = 0;

            for (int q = 0; q < quads; q++) {
               if (!removed[q]) {
                  if (write != q) {
                     long from = MemoryUtil.memAddress0(buffer) + q * 16L;
                     MemoryUtil.memCopy(from, MemoryUtil.memAddress0(buffer) + write * 16L, 16L);
                  }

                  write++;
               }
            }

            this.solid.count = write;
            buffer.position(write * 16);
         }
      }
   }

   private @Nullable ByteBuffer compact(ByteBuffer source, int quads) {
      int low = Integer.MAX_VALUE;
      int high = Integer.MIN_VALUE;

      for (int q = 0; q < quads; q++) {
         int head = source.getInt(q * 16);
         int y = source.getInt(q * 16 + 8);
         int face = head >>> 24 & 7;
         int a = y & 65535;
         int b = face < 2 ? a : y >>> 16;
         low = Math.min(low, Math.min(a, b));
         high = Math.max(high, Math.max(a, b));
         if (face == 2 || face == 3 ? (head >>> 18 & 63) != 0 : face >= 2 && (head >>> 12 & 63) != 0) {
            return null;
         }
      }

      if (high - low >= 8192) {
         return null;
      }

      Long2IntOpenHashMap materials = this.compactMaterialIds;
      materials.clear();
      if (this.compactIndex.length < quads) {
         this.compactIndex = new int[Math.max(quads, this.compactIndex.length * 2)];
      }

      int[] index = this.compactIndex;
      LongArrayList order = this.compactOrder;
      order.clear();

      for (int q = 0; q < quads; q++) {
         int head = source.getInt(q * 16);
         int look = source.getInt(q * 16 + 4);
         int rest = source.getInt(q * 16 + 12);
         long key = (long)look << 32 | (rest & 16383 | rest >>> 26 << 14 | head >>> 28 << 20) & 4294967295L;
         int id = materials.get(key);
         if (id < 0) {
            id = order.size();
            if (id >= 32768) {
               return null;
            }

            materials.put(key, id);
            order.add(key);
         }

         index[q] = id;
      }

      ByteBuffer out = MemoryUtil.memAlloc((order.size() + quads) * 8);

      for (int i = 0; i < order.size(); i++) {
         long key = order.getLong(i);
         out.putInt((int)(key >>> 32));
         out.putInt((int)key);
      }

      for (int q = 0; q < quads; q++) {
         int head = source.getInt(q * 16);
         int y = source.getInt(q * 16 + 8);
         int face = head >>> 24 & 7;
         int id = index[q];
         if (face < 2) {
            out.putInt(head & 268435455 | (id & 15) << 28);
            out.putInt((y & 65535) - low | (y >>> 16 & 0xFF) << 13 | (id >>> 4 & 2047) << 21);
         } else if (face != 2 && face != 3) {
            out.putInt(head & 268177407 | (id & 63) << 12 | (id >>> 6 & 15) << 28);
            out.putInt((y & 65535) - low | (y >>> 16) - low << 13 | (id >>> 10 & 63) << 26);
         } else {
            out.putInt(head & 251920383 | (id & 63) << 18 | (id >>> 6 & 15) << 28);
            out.putInt((y & 65535) - low | (y >>> 16) - low << 13 | (id >>> 10 & 63) << 26);
         }
      }

      out.flip();
      this.compactMaterials = order.size();
      this.compactBase = low;
      return out;
   }

   private int patchTotal() {
      boolean[] seen = new boolean[4096];
      int total = 0;

      for (int q = 0; q < this.waterCount; q++) {
         if (!seen[this.waterPatch[q]]) {
            seen[this.waterPatch[q]] = true;
            total++;
         }
      }

      return total;
   }

   private ByteBuffer sortWater(int[] patches) {
      int cells = 4096;
      int[] counts = this.waterCounts;
      int[] starts = this.waterStarts;
      int[] firstQuad = this.waterFirst;
      Arrays.fill(counts, 0);

      for (int q = 0; q < this.waterCount; q++) {
         counts[this.waterPatch[q]]++;
      }

      int next = 0;
      int patch = 0;

      for (int id = 0; id < 4096; id++) {
         if (counts[id] != 0) {
            starts[id] = next;
            firstQuad[id] = patch;
            patches[patch * 5] = id % 64 * this.patchCells;
            patches[patch * 5 + 1] = id / 64 * this.patchCells;
            patches[patch * 5 + 2] = next;
            patches[patch * 5 + 3] = counts[id];
            next += counts[id];
            patch++;
         }
      }

      int quadBytes = this.waterQuadBytes();
      ByteBuffer out = MemoryUtil.memAlloc(this.waterCount * quadBytes);
      long source = MemoryUtil.memAddress0(this.water);
      long target = MemoryUtil.memAddress0(out);

      for (int q = 0; q < this.waterCount; q++) {
         int id = this.waterPatch[q];
         int place = (int)(starts[id]++);
         MemoryUtil.memCopy(source + (long)q * quadBytes, target + (long)place * quadBytes, quadBytes);
         if (patches[firstQuad[id] * 5 + 2] == place) {
            patches[firstQuad[id] * 5 + 4] = this.vanillaWater
               ? Math.round(this.water.getFloat(q * quadBytes + 4))
               : Math.round(((this.water.getInt(q * quadBytes + 8) & 65535) - 32768) / 16.0F);
         }
      }

      out.position(0).limit(this.waterCount * quadBytes);
      return out;
   }

   private int waterQuadBytes() {
      return this.vanillaWater ? 112 : 16;
   }

   private void waterFace(int cellX, int cellZ, int face, int sprite, int tint) {
      this.waterFace(cellX, cellZ, face, sprite, tint, false);
   }

   private void waterFace(int cellX, int cellZ, int face, int sprite, int tint, boolean twoSided) {
      if (this.vanillaWater) {
         this.vanillaWaterFace(cellX, cellZ, face, sprite, tint);
         if (twoSided) {
            swap(this.xs, 0, 2);
            swap(this.xs, 1, 3);
            this.vanillaWaterFace(cellX, cellZ, face, sprite, tint);
            swap(this.xs, 0, 2);
            swap(this.xs, 1, 3);
         }
      } else {
         int needed = (this.waterCount + 1) * 16;
         if (this.water.capacity() < needed) {
            ByteBuffer bigger = MemoryUtil.memAlloc(this.water.capacity() * 2);
            this.water.flip();
            bigger.put(this.water);
            MemoryUtil.memFree(this.water);
            this.water = bigger;
         }

         if (this.waterPatch.length <= this.waterCount) {
            this.waterPatch = Arrays.copyOf(this.waterPatch, this.waterPatch.length * 2);
         }

         float shade = WATER_SHADE[face];
         int red = Math.round((tint >> 16 & 0xFF) * shade);
         int green = Math.round((tint >> 8 & 0xFF) * shade);
         int blue = Math.round((tint & 0xFF) * shade);
         boolean flat = face == 1 || face == 0;
         boolean tiled = flat && this.scale <= 16 && this.sprites.tiled(sprite, this.tiledRect);
         int minX = Integer.MAX_VALUE;
         int maxX = Integer.MIN_VALUE;
         int minZ = Integer.MAX_VALUE;
         int maxZ = Integer.MIN_VALUE;
         int low = Integer.MAX_VALUE;
         int high = Integer.MIN_VALUE;
         int lightLow = 0;
         int lightHigh = 0;

         for (int v = 0; v < 4; v++) {
            minX = Math.min(minX, this.xs[v]);
            maxX = Math.max(maxX, this.xs[v]);
            minZ = Math.min(minZ, this.zs[v]);
            maxZ = Math.max(maxZ, this.zs[v]);
            if (this.ys[v] < low) {
               low = this.ys[v];
               lightLow = this.lights[v];
            }

            if (this.ys[v] > high) {
               high = this.ys[v];
               lightHigh = this.lights[v];
            }
         }

         int level = Integer.numberOfTrailingZeros(this.scale);
         ByteBuffer b = this.water;
         b.putInt(
            minX / this.scale | minZ / this.scale << 7 | maxX / this.scale << 14 | maxZ / this.scale << 21 | face << 28 | (twoSided ? Integer.MIN_VALUE : 0)
         );
         b.putInt(red << 16 | green << 8 | blue | level << 24 | (tiled ? 134217728 : 0));
         b.putInt(low + 32768 & 65535 | (high + 32768 & 65535) << 16);
         b.putInt(sprite & 16383 | (lightLow & 0xFF) << 14 | (lightHigh & 0xFF) << 22);
         this.waterPatch[this.waterCount++] = cellZ / this.patchCells * 64 + cellX / this.patchCells;
      }
   }

   private void vanillaWaterFace(int cellX, int cellZ, int face, int sprite, int tint) {
      int needed = (this.waterCount + 1) * 112;
      if (this.water.capacity() < needed) {
         ByteBuffer bigger = MemoryUtil.memAlloc(Math.max(this.water.capacity() * 2, needed));
         this.water.flip();
         bigger.put(this.water);
         MemoryUtil.memFree(this.water);
         this.water = bigger;
      }

      if (this.waterPatch.length <= this.waterCount) {
         this.waterPatch = Arrays.copyOf(this.waterPatch, this.waterPatch.length * 2);
      }

      this.sprites.rect(sprite, this.waterRect);
      float shade = WATER_SHADE[face];
      int red = Math.round((tint >> 16 & 0xFF) * shade);
      int green = Math.round((tint >> 8 & 0xFF) * shade);
      int blue = Math.round((tint & 0xFF) * shade);
      boolean flat = face == 1 || face == 0;
      boolean tiled = flat && this.scale <= 16 && this.sprites.tiled(sprite, this.tiledRect);
      int patchX = cellX / this.patchCells * 16;
      int patchZ = cellZ / this.patchCells * 16;
      ByteBuffer b = this.water;

      for (int v = 0; v < 4; v++) {
         boolean middle = v == 1 || v == 2;
         b.putFloat(this.xs[v]);
         b.putFloat(this.ys[v] / 16.0F);
         b.putFloat(this.zs[v]);
         b.put((byte)red);
         b.put((byte)green);
         b.put((byte)blue);
         b.put((byte)-1);
         if (tiled) {
            b.putFloat(this.tiledRect[0] + (this.tiledRect[2] - this.tiledRect[0]) * (this.xs[v] - patchX) / 16.0F);
            b.putFloat(this.tiledRect[1] + (this.tiledRect[3] - this.tiledRect[1]) * (this.zs[v] - patchZ) / 16.0F);
         } else {
            b.putFloat(v < 2 ? this.waterRect[0] : this.waterRect[2]);
            b.putFloat(flat == middle ? this.waterRect[3] : this.waterRect[1]);
         }

         b.putShort((short)((this.lights[v] & 15) * 16));
         b.putShort((short)((this.lights[v] >> 4 & 15) * 16));
      }

      this.waterPatch[this.waterCount++] = cellZ / this.patchCells * 64 + cellX / this.patchCells;
   }

   private static void swap(int[] values, int a, int b) {
      int t = values[a];
      values[a] = values[b];
      values[b] = t;
   }

   private int tintSlot(long key) {
      for (int i = 0; i < 6; i++) {
         if (this.tintKeys[i] == key) {
            return i;
         }
      }

      int slot = this.tintNext;
      this.tintNext = (slot + 1) % 6;
      this.tintKeys[slot] = key;
      this.tintGen[slot] = ++this.tintCounter;
      return slot;
   }

   private int blended(LodTile tile, int x, int z, int state, int tintIndex, boolean fluid, int biome, int wx, int wz) {
      if (tintIndex < 0) {
         return 16777215;
      }

      int radius = this.scale == 1 ? 2 : (this.scale == 2 ? 1 : 0);
      if (radius == 0) {
         return this.looks.tint(state, tintIndex, fluid, biome, this.biomes, wx, wz);
      }

      if (this.tintTile != tile) {
         Arrays.fill(this.tintKeys, Long.MIN_VALUE);
         this.tintTile = tile;
      }

      int slot = this.tintSlot((long)state << 32 | (long)(tintIndex & 65535) << 1 | (fluid ? 1L : 0L));
      int[] grid = this.tintGrid[slot];
      int[] stamp = this.tintStamp[slot];
      int gen = this.tintGen[slot];
      int r = 0;
      int g = 0;
      int b = 0;
      int n = 0;

      for (int dz = -radius; dz <= radius; dz++) {
         for (int dx = -radius; dx <= radius; dx++) {
            int ux = x + dx;
            int uz = z + dz;
            int nx = Math.max(-1, Math.min(64, ux));
            int nz = Math.max(-1, Math.min(64, uz));
            boolean filled = tile.count(nx, nz) > 0;
            int c;
            if (filled && nx == ux && nz == uz) {
               int cell = LodTile.cell(nx, nz);
               if (stamp[cell] == gen) {
                  c = grid[cell];
               } else {
                  c = this.looks
                     .tint(state, tintIndex, fluid, Span.biome(tile.spans[tile.first(nx, nz)]), this.biomes, wx + dx * this.scale, wz + dz * this.scale);
                  grid[cell] = c;
                  stamp[cell] = gen;
               }
            } else {
               int other = filled ? Span.biome(tile.spans[tile.first(nx, nz)]) : biome;
               c = this.looks.tint(state, tintIndex, fluid, other, this.biomes, wx + dx * this.scale, wz + dz * this.scale);
            }

            r += c >> 16 & 0xFF;
            g += c >> 8 & 0xFF;
            b += c & 0xFF;
            n++;
         }
      }

      return r / n << 16 | g / n << 8 | b / n;
   }

   private boolean occupied(LodTile tile, int x, int z, int y) {
      int nx = Math.max(-1, Math.min(64, x));
      int nz = Math.max(-1, Math.min(64, z));
      int from = tile.first(nx, nz);
      int count = tile.count(nx, nz);

      for (int i = 0; i < count; i++) {
         long span = tile.spans[from + i];
         if (Span.top(span) <= y) {
            return false;
         }

         if (Span.bottom(span) <= y) {
            BlockLooks.Look look = this.occupant(span);
            return !look.plant() && !this.clear(look);
         }
      }

      return false;
   }

   private BlockLooks.Look occupant(long span) {
      BlockLooks.Look look = this.looks.look(Span.state(span));
      return look.plant() && look.water() >= 0 ? this.looks.look(look.water()) : look;
   }

   private int corner(LodTile tile, int x, int z, int dx, int dz, int y) {
      boolean a = this.occupied(tile, x + dx, z, y);
      boolean b = this.occupied(tile, x, z + dz, y);
      boolean c = a && b || this.occupied(tile, x + dx, z + dz, y);
      return (a ? 1 : 0) + (b ? 1 : 0) + (c ? 1 : 0);
   }

   private boolean shadedTop(LodTile tile, int x, int z, int top, int top16, int sprite, int tint, int light, BlockLooks.Look look) {
      int occlusion = this.corner(tile, x, z, -1, -1, top)
         | this.corner(tile, x, z, -1, 1, top) << 2
         | this.corner(tile, x, z, 1, 1, top) << 4
         | this.corner(tile, x, z, 1, -1, top) << 6;
      if (occlusion == 0) {
         return false;
      } else {
         LodMesher.Quads target = this.clear(look) ? this.fluid : this.solid;
         int y = top16 + 32768;
         if (y >= 0 && y <= 65535) {
            int flags = (look.cutout() ? 1 : 0) | (look.fluid() ? 2 : 0);
            this.quad(target, x, z, 1, 1, 1, light, light, y | occlusion << 16, tint, sprite, flags);
            return true;
         } else {
            return false;
         }
      }
   }

   private boolean clear(BlockLooks.Look look) {
      return look.fluid() ? !this.shader() || look.material() != 6 : look.translucent() && !this.shader();
   }

   private boolean shader() {
      return this.format == LodMesher.Format.SHADER;
   }

   private void column(LodTile tile, int x, int z, int skirt) {
      int wx = tile.originX() + x * this.scale + this.scale / 2;
      int wz = tile.originZ() + z * this.scale + this.scale / 2;
      int from = tile.first(x, z);
      int count = tile.count(x, z);
      boolean solidTopSeen = false;
      boolean fluidTopSeen = false;

      for (int i = 0; i < count; i++) {
         long span = tile.spans[from + i];
         int top = Span.top(span);
         int bottom = Span.bottom(span);
         int light = Span.sky(span) << 4 | Span.block(span);
         int biome = Span.biome(span);
         int state = Span.state(span);
         BlockLooks.Look look = this.looks.look(state);
         if (look.plant()) {
            if (this.scale <= 2 && !this.shader()) {
               int tint = this.blended(tile, x, z, state, look.topTint(), false, biome, wx, wz);
               if (look.segments() > 0) {
                  this.flat(x, z, bottom * 16 + look.height(), look.top(), tint, light, look.segments());
               } else {
                  this.cross(x, z, bottom * 16, top * 16, look.top(), tint, light);
               }
            }

            if (look.water() < 0) {
               continue;
            }

            state = look.water();
            look = this.looks.look(state);
         }

         boolean surface = look.fluid() && (i == 0 || Span.bottom(tile.spans[from + i - 1]) != top);
         int top16 = surface ? top * 16 - 2 : (look.height() < 16 && top - bottom == 1 ? bottom * 16 + look.height() : top * 16);
         boolean isFluid = look.fluid();
         boolean coveredAbove = false;
         if (i > 0) {
            long above = tile.spans[from + i - 1];
            BlockLooks.Look aboveLook = this.occupant(above);
            coveredAbove = Span.bottom(above) == top && !aboveLook.plant() && (isFluid ? aboveLook.fluid() || !this.clear(aboveLook) : !this.clear(aboveLook));
         }

         if (!coveredAbove) {
            int tint = this.blended(tile, x, z, state, look.topTint(), isFluid, biome, wx, wz);
            int cell = z * 64 + x;
            if (this.clear(look) && !fluidTopSeen) {
               this.fluidTops[cell] = this.key(top16, look.top(), light, look, tint);
               fluidTopSeen = true;
            } else if (!this.clear(look) && !solidTopSeen) {
               solidTopSeen = true;
               if (this.scale > 2 || this.shader() || !this.shadedTop(tile, x, z, top, top16, look.top(), tint, light, look)) {
                  this.solidTops[cell] = this.key(top16, look.top(), light, look, tint);
               }
            } else {
               this.face(1, x, z, top16, top16, look.top(), tint, light, look, Integer.MIN_VALUE);
            }
         }

         if (i < count - 1 ? Span.top(tile.spans[from + i + 1]) < bottom : bottom > this.floor && !this.clear(look)) {
            int tint = this.blended(tile, x, z, state, look.bottomTint(), isFluid, biome, wx, wz);
            this.face(0, x, z, bottom * 16, bottom * 16, look.bottom(), tint, light, look, Integer.MIN_VALUE);
         }

         boolean greenSide = this.scale >= 4 && !coveredAbove && !isFluid && look.topTint() >= 0 && (look.sideTint() < 0 || look.layered());
         int sideSprite = greenSide ? look.top() : look.side();
         int sideTint = greenSide
            ? this.blended(tile, x, z, state, look.topTint(), false, biome, wx, wz)
            : this.blended(tile, x, z, state, look.sideTint(), isFluid, biome, wx, wz);

         for (int face = 2; face <= 5; face++) {
            int nx = x + DX[face];
            int nz = z + DZ[face];
            boolean edge = nx < 0 || nz < 0 || nx >= 64 || nz >= 64;
            this.side(
               tile, face, x, z, nx, nz, bottom * 16, top16, this.clear(look), edge && !this.clear(look) ? skirt * 16 : 0, sideSprite, sideTint, light, look
            );
         }
      }
   }

   private void side(
      LodTile tile,
      int face,
      int x,
      int z,
      int nx,
      int nz,
      int low16,
      int top16,
      boolean fluid,
      int skirt16,
      int sprite,
      int tint,
      int light,
      BlockLooks.Look look
   ) {
      int from = tile.first(nx, nz);
      int count = tile.count(nx, nz);
      int cur = top16;
      int water = Integer.MIN_VALUE;

      for (int i = 0; i < count && cur > low16; i++) {
         long span = tile.spans[from + i];
         BlockLooks.Look other = this.occupant(span);
         if (!other.plant()) {
            boolean neighbourFluid = this.clear(other);
            if (fluid || !neighbourFluid) {
               boolean surface = other.fluid() && (i == 0 || Span.bottom(tile.spans[from + i - 1]) != Span.top(span));
               int realTop = surface ? Span.top(span) * 16 - 2 : this.spanTop16(span);
               int nTop = realTop - skirt16;
               int nBottom = Span.bottom(span) * 16;
               if (nBottom < cur && (!fluid || neighbourFluid || nTop > low16)) {
                  if (nTop < cur) {
                     int lo = Math.max(nTop, low16);
                     int hi = skirt16 > 0 && realTop >= cur ? cur - (this.shader() ? 16 : 1) : cur;
                     if (lo < hi) {
                        this.wall(face, x, z, lo, hi, sprite, tint, light, look, water);
                     }
                  }

                  cur = Math.min(cur, nBottom);
               }
            } else if (other.fluid() && water == Integer.MIN_VALUE) {
               water = Span.top(span) * 16;
            }
         }
      }

      if (cur > low16) {
         boolean floating = low16 > this.floor * 16;
         if (count == 0) {
            this.face(face, x, z, floating ? low16 : Math.max(low16, top16 - 1024), cur, sprite, tint, light, look, Integer.MIN_VALUE);
         } else if (!fluid) {
            this.wall(face, x, z, low16, cur, sprite, tint, light, look, water);
         }
      }
   }

   private void wall(int face, int x, int z, int lo16, int hi16, int sprite, int tint, int light, BlockLooks.Look look, int water16) {
      if (water16 > lo16 && water16 < hi16) {
         this.face(face, x, z, water16, hi16, sprite, tint, light, look, Integer.MIN_VALUE);
         this.face(face, x, z, lo16, water16, sprite, tint, light, look, water16);
      } else {
         this.face(face, x, z, lo16, hi16, sprite, tint, light, look, water16);
      }
   }

   private static int submerged(int light, int y16, int water16) {
      if (y16 >= water16) {
         return light;
      }

      int sky = light >> 4 & 15;
      int depth = (water16 - y16 + 8) / 16;
      return Math.min(sky, Math.max(0, 15 - depth)) << 4 | light & 15;
   }

   private int spanTop16(long span) {
      int top = Span.top(span);
      int bottom = Span.bottom(span);
      BlockLooks.Look look = this.looks.look(Span.state(span));
      return look.height() < 16 && top - bottom == 1 ? bottom * 16 + look.height() : top * 16;
   }

   private long key(int top16, int sprite, int light, BlockLooks.Look look, int tint) {
      if (this.shader()) {
         return (long)(top16 & 65535) << 48
            | (long)(light & 0xFF) << 40
            | (long)(look.material() & 15) << 32
            | (long)(look.fluid() ? 1 : 0) << 31
            | this.albedo(sprite, tint) & 16777215L;
      }

      int flags = (look.cutout() ? 1 : 0) | (look.fluid() ? 2 : 0);
      return (long)(top16 & 65535) << 48 | (long)(sprite & 16383) << 34 | (long)(light & 0xFF) << 26 | (long)(flags & 3) << 24 | tint & 16777215;
   }

   private int albedo(int sprite, int tint) {
      float[] data = this.sprites.data();
      int base = sprite * 8;
      float marker = data[base + 7];
      if (marker > 3.5F) {
         int layer = (int)(marker - 4.0F);
         float cover = (marker - 4.0F - layer) * 2.0F;
         int over = layer * 8;
         int r = Math.round(Math.min(1.0F, data[base + 4]) * (1.0F - cover) * 255.0F + Math.min(1.0F, data[over + 4]) * cover * (tint >> 16 & 0xFF));
         int g = Math.round(Math.min(1.0F, data[base + 5]) * (1.0F - cover) * 255.0F + Math.min(1.0F, data[over + 5]) * cover * (tint >> 8 & 0xFF));
         int b = Math.round(Math.min(1.0F, data[base + 6]) * (1.0F - cover) * 255.0F + Math.min(1.0F, data[over + 6]) * cover * (tint & 0xFF));
         return Math.min(255, r) << 16 | Math.min(255, g) << 8 | Math.min(255, b);
      } else {
         float shade = marker > 1.5F ? 0.62F : 1.0F;
         int r = Math.round(Math.min(1.0F, data[base + 4]) * shade * (tint >> 16 & 0xFF));
         int g = Math.round(Math.min(1.0F, data[base + 5]) * shade * (tint >> 8 & 0xFF));
         int b = Math.round(Math.min(1.0F, data[base + 6]) * shade * (tint & 0xFF));
         return r << 16 | g << 8 | b;
      }
   }

   private void mergeTops(long[] keys, LodMesher.Quads target, int limit) {
      Arrays.fill(this.used, false);
      int size = 64;

      for (int z = 0; z < 64; z++) {
         for (int x = 0; x < 64; x++) {
            int i = z * 64 + x;
            long key = keys[i];
            if (!this.used[i] && key != Long.MIN_VALUE) {
               int w = 1;

               while (x + w < 64 && (x + w) % limit != 0 && !this.used[i + w] && keys[i + w] == key) {
                  w++;
               }

               int h;
               label70:
               for (h = 1; z + h < 64 && (z + h) % limit != 0; h++) {
                  int row = (z + h) * 64 + x;

                  for (int k = 0; k < w; k++) {
                     if (this.used[row + k] || keys[row + k] != key) {
                        break label70;
                     }
                  }
               }

               for (int dz = 0; dz < h; dz++) {
                  for (int dx = 0; dx < w; dx++) {
                     this.used[(z + dz) * 64 + x + dx] = true;
                  }
               }

               this.rect(target, x, z, w, h, key);
            }
         }
      }
   }

   private void rect(LodMesher.Quads target, int x, int z, int w, int h, long key) {
      int top16 = (int)(key >>> 48 & 65535L);
      int x0 = x * this.scale;
      int z0 = z * this.scale;
      int x1 = (x + w) * this.scale;
      int z1 = (z + h) * this.scale;
      if (this.waterLayer && target == this.fluid) {
         int sprite = (int)(key >>> 34 & 16383L);
         int light = (int)(key >>> 26 & 255L);
         int flags = (int)(key >>> 24 & 3L);
         int tint = (int)(key & 16777215L);
         int y16 = (short)top16 - ((flags & 2) == 0 ? 1 : 0);
         fill(this.ys, y16, y16, y16, y16);
         fill(this.lights, light, light, light, light);
         fill(this.xs, x0, x0, x1, x1);
         fill(this.zs, z0, z1, z1, z0);
         this.waterFace(x, z, 1, sprite, tint, (flags & 2) != 0);
      } else {
         target.ensure();
         if (this.shader()) {
            int light = (int)(key >>> 40 & 255L);
            int material = (int)(key >>> 32 & 15L);
            int color = (int)(key & 16777215L);
            this.shaderVertex(target, x0, top16, z0, 1, color, light, material);
            this.shaderVertex(target, x0, top16, z1, 1, color, light, material);
            this.shaderVertex(target, x1, top16, z1, 1, color, light, material);
            this.shaderVertex(target, x1, top16, z0, 1, color, light, material);
            target.count++;
         } else {
            int sprite = (int)(key >>> 34 & 16383L);
            int light = (int)(key >>> 26 & 255L);
            int flags = (int)(key >>> 24 & 3L);
            int tint = (int)(key & 16777215L);
            int y = top16 + 32768 & 65535;
            this.quad(target, x, z, w, h, 1, light, light, y, tint, sprite, flags);
         }
      }
   }

   private void face(int face, int x, int z, int lo16, int hi16, int sprite, int tint, int light, BlockLooks.Look look, int water16) {
      LodMesher.Quads target = this.clear(look) ? this.fluid : this.solid;
      int x0 = x * this.scale;
      int z0 = z * this.scale;
      int x1 = x0 + this.scale;
      int z1 = z0 + this.scale;
      if (lo16 + 32768 >= 0 && hi16 + 32768 <= 65535) {
         if (lo16 < this.lowest16) {
            this.lowest16 = lo16;
         }

         target.ensure();
         int[] xs = this.xs;
         int[] ys = this.ys;
         int[] zs = this.zs;
         switch (face) {
            case 0:
               fill(xs, x1, x1, x0, x0);
               fill(ys, lo16, lo16, lo16, lo16);
               fill(zs, z0, z1, z1, z0);
               break;
            case 1:
               fill(xs, x0, x0, x1, x1);
               fill(ys, hi16, hi16, hi16, hi16);
               fill(zs, z0, z1, z1, z0);
               break;
            case 2:
               fill(xs, x0, x0, x1, x1);
               fill(ys, lo16, hi16, hi16, lo16);
               fill(zs, z0, z0, z0, z0);
               break;
            case 3:
               fill(xs, x1, x1, x0, x0);
               fill(ys, lo16, hi16, hi16, lo16);
               fill(zs, z1, z1, z1, z1);
               break;
            case 4:
               fill(xs, x0, x0, x0, x0);
               fill(ys, lo16, hi16, hi16, lo16);
               fill(zs, z1, z1, z0, z0);
               break;
            default:
               fill(xs, x1, x1, x1, x1);
               fill(ys, lo16, hi16, hi16, lo16);
               fill(zs, z0, z0, z1, z1);
         }

         if (this.waterLayer && target == this.fluid) {
            for (int v = 0; v < 4; v++) {
               this.lights[v] = submerged(light, ys[v], water16);
            }

            this.waterFace(x, z, face, sprite, tint);
         } else if (this.shader()) {
            int color = this.albedo(sprite, tint);

            for (int v = 0; v < 4; v++) {
               this.shaderVertex(target, xs[v], ys[v], zs[v], face, color, submerged(light, ys[v], water16), look.material());
            }

            target.count++;
         } else {
            int flags = (look.cutout() ? 1 : 0) | (look.fluid() ? 2 : 0);
            int low = ys[0] + 32768 & 65535;
            int high = ys[1] + 32768 & 65535;
            int y = face != 1 && face != 0 ? low | high << 16 : low;
            this.quad(target, x, z, 1, 1, face, submerged(light, ys[0], water16), submerged(light, ys[1], water16), y, tint, sprite, flags);
         }
      }
   }

   private void flat(int x, int z, int y16, int sprite, int tint, int light, int segments) {
      int y = y16 + 32768;
      if (y >= 0 && y <= 65535) {
         this.quad(this.solid, x, z, 1, 1, 1, light, light, y, tint, sprite, 4 | segments << 4);
      }
   }

   private void cross(int x, int z, int lo16, int hi16, int sprite, int tint, int light) {
      int y0 = lo16 + 32768;
      int y1 = hi16 + 32768;
      if (y0 >= 0 && y1 <= 65535) {
         int y = y0 & 65535 | (y1 & 65535) << 16;
         this.quad(this.solid, x, z, 1, 1, 6, light, light, y, tint, sprite, 4);
         this.quad(this.solid, x, z, 1, 1, 7, light, light, y, tint, sprite, 4);
      }
   }

   private static void fill(int[] target, int a, int b, int c, int d) {
      target[0] = a;
      target[1] = b;
      target[2] = c;
      target[3] = d;
   }

   private void quad(LodMesher.Quads target, int x, int z, int w, int h, int face, int lowLight, int light, int y, int tint, int sprite, int flags) {
      target.ensure();
      ByteBuffer b = target.buffer;
      b.putInt(x | z << 6 | w - 1 << 12 | h - 1 << 18 | face << 24 | (lowLight >> 4 & 15) << 28);
      b.putInt(tint & 16777215 | (light & 0xFF) << 24);
      b.putInt(y);
      b.putInt(sprite & 16383 | this.slot << 14 | (flags & 7 | (flags >> 4 & 7) << 3) << 26);
      target.count++;
   }

   private void shaderVertex(LodMesher.Quads target, int x, int y16, int z, int face, int color, int light, int material) {
      ByteBuffer b = target.buffer;
      int y = Math.floorDiv(y16 + 8, 16) + 2048;
      b.putShort((short)x);
      b.putShort((short)y);
      b.putShort((short)z);
      b.putShort((short)((light & 240) >> 4 | (light & 15) << 4));
      b.put((byte)(color >> 16));
      b.put((byte)(color >> 8));
      b.put((byte)color);
      b.put((byte)-1);
      b.put((byte)material);
      b.put((byte)face);
      b.put((byte)0);
      b.put((byte)0);
   }

   public void free() {
      MemoryUtil.memFree(this.solid.buffer);
      MemoryUtil.memFree(this.fluid.buffer);
      MemoryUtil.memFree(this.water);
   }

   public enum Format {
      FARLANDS,
      SHADER;
   }

   public record Mesh(
      ByteBuffer data,
      int quads,
      int fluidQuads,
      int surfaceQuads,
      LodMesher.Format format,
      ByteBuffer water,
      int waterQuads,
      int[] waterPatches,
      int[] solidBuckets,
      int[] solidGroups,
      int[] subBounds,
      int materials,
      int yBase,
      int[] occluders
   ) {
      public boolean compact() {
         return this.materials > 0;
      }

      public long bytes() {
         return this.compact() ? (this.materials + this.quads) * 8L : this.quads * 16L;
      }

      public void free() {
         MemoryUtil.memFree(this.data);
         if (this.water != null) {
            MemoryUtil.memFree(this.water);
         }
      }
   }

   private static final class Quads {
      ByteBuffer buffer = MemoryUtil.memAlloc(131072);
      int count;

      void reset() {
         this.buffer.clear();
         this.count = 0;
      }

      void ensure() {
         if (this.buffer.remaining() < 64) {
            ByteBuffer bigger = MemoryUtil.memAlloc(this.buffer.capacity() * 2);
            this.buffer.flip();
            bigger.put(this.buffer);
            MemoryUtil.memFree(this.buffer);
            this.buffer = bigger;
         }
      }
   }
}
