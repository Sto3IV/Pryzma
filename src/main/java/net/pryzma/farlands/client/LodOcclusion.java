package net.pryzma.farlands.client;

import net.pryzma.farlands.lod.LodTile;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jspecify.annotations.Nullable;

final class LodOcclusion {
   static final int BINS = 4096;
   private static final double BIN = 0.0015339807878856412;
   private static final double NEAREST = 12.0;
   private final AtomicBoolean busy = new AtomicBoolean();
   private volatile LodOcclusion.@Nullable Result result;
   private volatile boolean closed;
   private @Nullable Thread thread;
   private LodOcclusion.@Nullable Job pending;
   private final Object lock = new Object();

   LodOcclusion.@Nullable Result result() {
      return this.result;
   }

   boolean busy() {
      return this.busy.get();
   }

   void submit(LodOcclusion.Job job) {
      if (!this.closed && this.busy.compareAndSet(false, true)) {
         synchronized (this.lock) {
            this.pending = job;
            if (this.thread == null) {
               Thread worker = new Thread(this::loop, "Farlands occlusion");
               worker.setDaemon(true);
               worker.setPriority(4);
               this.thread = worker;
               worker.start();
            }

            this.lock.notifyAll();
         }
      }
   }

   void clear() {
      this.result = null;
   }

   void close() {
      this.closed = true;
      this.result = null;
      synchronized (this.lock) {
         this.lock.notifyAll();
      }
   }

   private void loop() {
      LodOcclusion.Horizon horizon = new LodOcclusion.Horizon();
      LodOcclusion.Panorama panorama = new LodOcclusion.Panorama();

      while (!this.closed) {
         LodOcclusion.Job job;
         synchronized (this.lock) {
            while (this.pending == null && !this.closed) {
               try {
                  this.lock.wait();
               } catch (InterruptedException e) {
                  return;
               }
            }

            job = this.pending;
            this.pending = null;
         }

         if (job != null) {
            try {
               long start = System.nanoTime();
               Long2LongOpenHashMap visible = new Long2LongOpenHashMap(job.count * 2);
               visible.defaultReturnValue(-1L);
               int hidden = job.canopy ? panorama.run(job, visible) : horizon.run(job, visible);
               if (!this.closed) {
                  this.result = new LodOcclusion.Result(job, visible, hidden, System.nanoTime() - start);
               }
            } catch (RuntimeException e) {
               this.result = null;
            } finally {
               this.busy.set(false);
            }
         }
      }
   }

   static boolean azimuth(double cx, double cz, double minX, double maxX, double minZ, double maxZ, double[] corner, double[] angles) {
      double lowest = Double.POSITIVE_INFINITY;
      double highest = Double.NEGATIVE_INFINITY;

      for (int c = 0; c < 4; c++) {
         double px = (c & 1) == 0 ? minX : maxX;
         double pz = (c & 2) == 0 ? minZ : maxZ;
         double angle = Math.atan2(pz - cz, px - cx);
         if (angle < 0.0) {
            angle += Math.PI * 2;
         }

         angles[c] = angle;
         lowest = Math.min(lowest, angle);
         highest = Math.max(highest, angle);
      }

      if (highest - lowest > Math.PI) {
         lowest = Double.POSITIVE_INFINITY;
         highest = Double.NEGATIVE_INFINITY;

         for (int c = 0; c < 4; c++) {
            double angle = angles[c] < Math.PI ? angles[c] + (Math.PI * 2) : angles[c];
            lowest = Math.min(lowest, angle);
            highest = Math.max(highest, angle);
         }

         if (highest - lowest >= Math.PI) {
            return false;
         }
      }

      corner[0] = lowest + (Math.PI * 2);
      corner[1] = highest + (Math.PI * 2);
      return true;
   }

   static double distance(double x, double z, double minX, double maxX, double minZ, double maxZ) {
      double dx = Math.max(0.0, Math.max(minX - x, x - maxX));
      double dz = Math.max(0.0, Math.max(minZ - z, z - maxZ));
      return Math.sqrt(dx * dx + dz * dz);
   }

   static double farthest(double x, double z, double minX, double maxX, double minZ, double maxZ) {
      double dx = Math.max(Math.abs(minX - x), Math.abs(maxX - x));
      double dz = Math.max(Math.abs(minZ - z), Math.abs(maxZ - z));
      return Math.sqrt(dx * dx + dz * dz);
   }

   private static final class Horizon {
      private static final double EPS = 1.0E-6;
      private final double[] low = new double[4096];
      private final double[] high = new double[4096];
      private final double[] leftReach = new double[4096];
      private final double[] leftLow = new double[4096];
      private final double[] leftHigh = new double[4096];
      private final double[] rightStart = new double[4096];
      private final double[] rightLow = new double[4096];
      private final double[] rightHigh = new double[4096];
      private double[] occDist = new double[1024];
      private double[] occFrom = new double[1024];
      private double[] occTo = new double[1024];
      private double[] occLow = new double[1024];
      private double[] occHigh = new double[1024];
      private long[] occOrder = new long[1024];
      private long[] targetOrder = new long[1024];
      private int[] targetFrom = new int[1024];
      private int[] targetTo = new int[1024];
      private double[] targetLow = new double[1024];
      private double[] targetHigh = new double[1024];
      private int[] targetMesh = new int[1024];
      private int[] targetSub = new int[1024];
      private int occluders;
      private int targets;
      private final double[] corner = new double[2];
      private final double[] angles = new double[4];

      int run(LodOcclusion.Job job, Long2LongOpenHashMap visible) {
         Arrays.fill(this.low, Double.POSITIVE_INFINITY);
         Arrays.fill(this.high, Double.NEGATIVE_INFINITY);
         Arrays.fill(this.leftReach, 0.0);
         Arrays.fill(this.rightStart, 1.0);
         this.occluders = 0;
         this.targets = 0;
         int[] masks = new int[job.count];

         for (int m = 0; m < job.count; m++) {
            masks[m] = -1;
            long key = job.keys[m];
            int level = LodTile.keyLevel(key);
            int size = 64 << level;
            int cell = 1 << level;
            double x0 = (double)LodTile.keyX(key) * size;
            double z0 = (double)LodTile.keyZ(key) * size;
            int[] bounds = job.bounds[m];
            int[] occ = job.occluders[m];

            for (int sub = 0; sub < 16; sub++) {
               int sx = (sub & 3) * 16;
               int sz = (sub >> 2) * 16;
               int quadrant = (sx >= 32 ? 1 : 0) + (sz >= 32 ? 2 : 0);
               if (occ != null && occ.length >= 80 && (job.quadMasks[m] >> quadrant & 1) != 0) {
                  this.occluder(job, occ, sub, x0 + sx * cell, z0 + sz * cell, 16.0 * cell);
               }
            }

            if (bounds != null && bounds.length >= 64) {
               for (int part = 0; part < 32; part++) {
                  this.target(job, bounds, part, m, x0, z0, cell);
               }
            }
         }

         int hidden = 0;
         if (this.occluders > 0 && this.targets > 0) {
            Arrays.sort(this.occOrder, 0, this.occluders);
            Arrays.sort(this.targetOrder, 0, this.targets);
            int next = 0;

            for (int t = 0; t < this.targets; t++) {
               int index = (int)(this.targetOrder[t] & 16777215L);
               double near = (this.targetOrder[t] >>> 24) / 64.0;

               while (next < this.occluders) {
                  int o = (int)(this.occOrder[next] & 16777215L);
                  if (this.occDist[o] > near) {
                     break;
                  }

                  this.insert(o);
                  next++;
               }

               if (next > 0 && this.hidden(index)) {
                  masks[this.targetMesh[index]] = masks[this.targetMesh[index]] & ~(1 << this.targetSub[index]);
                  hidden++;
               }
            }
         }

         for (int m = 0; m < job.count; m++) {
            visible.put(job.keys[m], (long)job.versions[m] << 32 | masks[m] & 4294967295L);
         }

         return hidden;
      }

      private void occluder(LodOcclusion.Job job, int[] occ, int sub, double minX, double minZ, double width) {
         double maxX = minX + width;
         double maxZ = minZ + width;
         double near = distance(job.x, job.z, minX, maxX, minZ, maxZ);
         if (!(near < 12.0)) {
            boolean leaves = near > job.leafRadius;
            if (leaves || occ[sub * 5 + 4] != 0) {
               int at = sub * 5 + (leaves ? 2 : 0);
               double lo = occ[at] + job.margin;
               double hi = occ[at + 1] - job.margin;
               if (occ[at] < occ[at + 1] && !(lo >= hi)) {
                  double far = farthest(job.x, job.z, minX, maxX, minZ, maxZ);
                  double top = (hi - job.y) / (hi >= job.y ? far : near);
                  double bottom = (lo - job.y) / (lo >= job.y ? near : far);
                  if (!(bottom >= top) && this.azimuth(job.x, job.z, minX, maxX, minZ, maxZ)) {
                     this.growOccluders();
                     int i = this.occluders++;
                     this.occDist[i] = far;
                     this.occFrom[i] = this.corner[0] / 0.0015339807878856412;
                     this.occTo[i] = this.corner[1] / 0.0015339807878856412;
                     this.occLow[i] = bottom;
                     this.occHigh[i] = top;
                     this.occOrder[i] = (long)Math.ceil(far * 64.0) << 24 | i;
                  }
               }
            }
         }
      }

      private void target(LodOcclusion.Job job, int[] bounds, int sub, int mesh, double x0, double z0, int cell) {
         int xz = bounds[sub * 2];
         int y = bounds[sub * 2 + 1];
         if (xz != 0 || y != 0) {
            double minX = x0 + (xz & 0xFF) * cell - 1.0;
            double maxX = x0 + (xz >>> 8 & 0xFF) * cell + 1.0;
            double minZ = z0 + (xz >>> 16 & 0xFF) * cell - 1.0;
            double maxZ = z0 + (xz >>> 24 & 0xFF) * cell + 1.0;
            double minY = Math.floorDiv(y & 65535, 16) - 2048 - 1;
            double maxY = Math.floorDiv((y >>> 16) + 15, 16) - 2048 + 1;
            double near = distance(job.x, job.z, minX, maxX, minZ, maxZ);
            if (!(near < 12.0) && this.azimuth(job.x, job.z, minX, maxX, minZ, maxZ)) {
               double far = farthest(job.x, job.z, minX, maxX, minZ, maxZ);
               double top = (maxY - job.y) / (maxY >= job.y ? near : far);
               double bottom = (minY - job.y) / (minY >= job.y ? far : near);
               int from = (int)Math.floor(this.corner[0] / 0.0015339807878856412);
               int to = (int)Math.floor(this.corner[1] / 0.0015339807878856412);
               if (to - from < 2048) {
                  this.growTargets();
                  int i = this.targets++;
                  this.targetFrom[i] = from;
                  this.targetTo[i] = to;
                  this.targetLow[i] = bottom;
                  this.targetHigh[i] = top;
                  this.targetMesh[i] = mesh;
                  this.targetSub[i] = sub;
                  this.targetOrder[i] = (long)Math.floor(near * 64.0) << 24 | i;
               }
            }
         }
      }

      private void insert(int o) {
         double a = this.occLow[o];
         double b = this.occHigh[o];
         double s = this.occFrom[o];
         double e = this.occTo[o];
         int first = (int)Math.floor(s);
         int last = (int)Math.floor(e);
         if (first != last) {
            double head = s - first;
            double tail = e - last;
            if (head <= 1.0E-6) {
               this.full(first & 4095, a, b);
            } else {
               this.right(first & 4095, head, a, b);
            }

            for (int bin = first + 1; bin < last; bin++) {
               this.full(bin & 4095, a, b);
            }

            if (tail >= 0.999999) {
               this.full(last & 4095, a, b);
            } else if (tail > 1.0E-6) {
               this.left(last & 4095, tail, a, b);
            }
         }
      }

      private void full(int k, double a, double b) {
         double lo = this.low[k];
         double hi = this.high[k];
         if (lo > hi) {
            this.low[k] = a;
            this.high[k] = b;
         } else if (a <= hi && b >= lo) {
            this.low[k] = Math.min(lo, a);
            this.high[k] = Math.max(hi, b);
         } else if (b > hi) {
            this.low[k] = a;
            this.high[k] = b;
         }
      }

      private void left(int k, double reach, double a, double b) {
         if (reach > this.leftReach[k]) {
            this.leftReach[k] = reach;
            this.leftLow[k] = a;
            this.leftHigh[k] = b;
         }

         this.join(k);
      }

      private void right(int k, double start, double a, double b) {
         if (start < this.rightStart[k]) {
            this.rightStart[k] = start;
            this.rightLow[k] = a;
            this.rightHigh[k] = b;
         }

         this.join(k);
      }

      private void join(int k) {
         if (!(this.leftReach[k] <= 1.0E-6) && !(this.rightStart[k] >= 0.999999) && !(this.leftReach[k] < this.rightStart[k] - 1.0E-6)) {
            double a = Math.max(this.leftLow[k], this.rightLow[k]);
            double b = Math.min(this.leftHigh[k], this.rightHigh[k]);
            if (a < b) {
               this.full(k, a, b);
            }
         }
      }

      private boolean hidden(int t) {
         double bottom = this.targetLow[t];
         double top = this.targetHigh[t];

         for (int bin = this.targetFrom[t]; bin <= this.targetTo[t]; bin++) {
            int k = bin & 4095;
            if (!(this.low[k] <= bottom) || !(top <= this.high[k])) {
               return false;
            }
         }

         return true;
      }

      private boolean azimuth(double cx, double cz, double minX, double maxX, double minZ, double maxZ) {
         double lowest = Double.POSITIVE_INFINITY;
         double highest = Double.NEGATIVE_INFINITY;

         for (int c = 0; c < 4; c++) {
            double px = (c & 1) == 0 ? minX : maxX;
            double pz = (c & 2) == 0 ? minZ : maxZ;
            double angle = Math.atan2(pz - cz, px - cx);
            if (angle < 0.0) {
               angle += Math.PI * 2;
            }

            this.angles[c] = angle;
            lowest = Math.min(lowest, angle);
            highest = Math.max(highest, angle);
         }

         if (highest - lowest > Math.PI) {
            lowest = Double.POSITIVE_INFINITY;
            highest = Double.NEGATIVE_INFINITY;

            for (int c = 0; c < 4; c++) {
               double angle = this.angles[c] < Math.PI ? this.angles[c] + (Math.PI * 2) : this.angles[c];
               lowest = Math.min(lowest, angle);
               highest = Math.max(highest, angle);
            }

            if (highest - lowest >= Math.PI) {
               return false;
            }
         }

         this.corner[0] = lowest + (Math.PI * 2);
         this.corner[1] = highest + (Math.PI * 2);
         return true;
      }

      private static double distance(double x, double z, double minX, double maxX, double minZ, double maxZ) {
         double dx = Math.max(0.0, Math.max(minX - x, x - maxX));
         double dz = Math.max(0.0, Math.max(minZ - z, z - maxZ));
         return Math.sqrt(dx * dx + dz * dz);
      }

      private static double farthest(double x, double z, double minX, double maxX, double minZ, double maxZ) {
         double dx = Math.max(Math.abs(minX - x), Math.abs(maxX - x));
         double dz = Math.max(Math.abs(minZ - z), Math.abs(maxZ - z));
         return Math.sqrt(dx * dx + dz * dz);
      }

      private void growOccluders() {
         if (this.occluders >= this.occDist.length) {
            int size = this.occDist.length * 2;
            this.occDist = Arrays.copyOf(this.occDist, size);
            this.occFrom = Arrays.copyOf(this.occFrom, size);
            this.occTo = Arrays.copyOf(this.occTo, size);
            this.occLow = Arrays.copyOf(this.occLow, size);
            this.occHigh = Arrays.copyOf(this.occHigh, size);
            this.occOrder = Arrays.copyOf(this.occOrder, size);
         }
      }

      private void growTargets() {
         if (this.targets >= this.targetFrom.length) {
            int size = this.targetFrom.length * 2;
            this.targetFrom = Arrays.copyOf(this.targetFrom, size);
            this.targetTo = Arrays.copyOf(this.targetTo, size);
            this.targetLow = Arrays.copyOf(this.targetLow, size);
            this.targetHigh = Arrays.copyOf(this.targetHigh, size);
            this.targetMesh = Arrays.copyOf(this.targetMesh, size);
            this.targetSub = Arrays.copyOf(this.targetSub, size);
            this.targetOrder = Arrays.copyOf(this.targetOrder, size);
         }
      }
   }

   static final class Job {
      double x;
      double y;
      double z;
      double tolerance;
      double margin;
      double leafRadius;
      boolean canopy;
      long hash;
      int count;
      long[] keys = new long[256];
      int[] versions = new int[256];
      int[] quadMasks = new int[256];
      int[][] bounds = new int[256][];
      int[][] occluders = new int[256][];

      void add(long key, int version, int quadMask, int[] bounds, int[] occluders) {
         if (this.count == this.keys.length) {
            int size = this.count * 2;
            this.keys = Arrays.copyOf(this.keys, size);
            this.versions = Arrays.copyOf(this.versions, size);
            this.quadMasks = Arrays.copyOf(this.quadMasks, size);
            this.bounds = Arrays.copyOf(this.bounds, size);
            this.occluders = Arrays.copyOf(this.occluders, size);
         }

         this.keys[this.count] = key;
         this.versions[this.count] = version;
         this.quadMasks[this.count] = quadMask;
         this.bounds[this.count] = bounds;
         this.occluders[this.count] = occluders;
         this.count++;
      }
   }

   static final class Panorama {
      static final int COLUMNS = 8192;
      static final int ROWS = 4096;
      private static final int WORDS = 128;
      private static final double STEP = 7.669903939428206E-4;
      private final long[] bits = new long[524288];
      private int lowRow = 4096;
      private int highRow = -1;
      private double[] occDist = new double[4096];
      private int[] occFrom = new int[4096];
      private int[] occTo = new int[4096];
      private int[] occLow = new int[4096];
      private int[] occHigh = new int[4096];
      private long[] occOrder = new long[4096];
      private long[] targetOrder = new long[4096];
      private int[] targetFrom = new int[4096];
      private int[] targetTo = new int[4096];
      private int[] targetLow = new int[4096];
      private int[] targetHigh = new int[4096];
      private int[] targetMesh = new int[4096];
      private int[] targetPart = new int[4096];
      private int occluders;
      private int targets;
      private final double[] corner = new double[2];
      private final double[] angles = new double[4];

      int run(LodOcclusion.Job job, Long2LongOpenHashMap visible) {
         if (this.highRow >= this.lowRow) {
            Arrays.fill(this.bits, this.lowRow * 128, (this.highRow + 1) * 128, 0L);
         }

         this.lowRow = 4096;
         this.highRow = -1;
         this.occluders = 0;
         this.targets = 0;
         int[] masks = new int[job.count];

         for (int m = 0; m < job.count; m++) {
            masks[m] = -1;
            long key = job.keys[m];
            int level = LodTile.keyLevel(key);
            int size = 64 << level;
            int cell = 1 << level;
            double x0 = (double)LodTile.keyX(key) * size;
            double z0 = (double)LodTile.keyZ(key) * size;
            int[] bounds = job.bounds[m];
            int[] occ = job.occluders[m];
            if (occ != null && occ.length >= 80) {
               for (int sub = 0; sub < 16; sub++) {
                  int sx = (sub & 3) * 16;
                  int sz = (sub >> 2) * 16;
                  int quadrant = (sx >= 32 ? 1 : 0) + (sz >= 32 ? 2 : 0);
                  if ((job.quadMasks[m] >> quadrant & 1) != 0) {
                     double minX = x0 + sx * cell;
                     double minZ = z0 + sz * cell;
                     double near = LodOcclusion.distance(job.x, job.z, minX, minX + 16.0 * cell, minZ, minZ + 16.0 * cell);
                     if (!(near < 12.0)) {
                        boolean leaves = near > job.leafRadius;
                        if (leaves || occ[sub * 5 + 4] != 0) {
                           int at = sub * 5 + (leaves ? 2 : 0);
                           this.occluder(job, occ[at], occ[at + 1], minX, minZ, 16.0 * cell);
                        }

                        if (leaves && occ.length >= 592) {
                           for (int group = 0; group < 16; group++) {
                              int at = 80 + (sub * 16 + group) * 2;
                              this.occluder(job, occ[at], occ[at + 1], minX + (group & 3) * 4.0 * cell, minZ + (group >> 2) * 4.0 * cell, 4.0 * cell);
                           }
                        }
                     }
                  }
               }
            }

            if (bounds != null && bounds.length >= 64) {
               for (int part = 0; part < 32; part++) {
                  this.target(job, bounds, part, m, x0, z0, cell);
               }
            }
         }

         int hidden = 0;
         if (this.occluders > 0 && this.targets > 0) {
            Arrays.sort(this.occOrder, 0, this.occluders);
            Arrays.sort(this.targetOrder, 0, this.targets);
            int next = 0;

            for (int t = 0; t < this.targets; t++) {
               int index = (int)(this.targetOrder[t] & 16777215L);
               double near = (this.targetOrder[t] >>> 24) / 64.0;

               while (next < this.occluders) {
                  int o = (int)(this.occOrder[next] & 16777215L);
                  if (this.occDist[o] > near) {
                     break;
                  }

                  this.insert(o);
                  next++;
               }

               if (next > 0 && this.covered(index)) {
                  masks[this.targetMesh[index]] = masks[this.targetMesh[index]] & ~(1 << this.targetPart[index]);
                  hidden++;
               }
            }
         }

         for (int m = 0; m < job.count; m++) {
            visible.put(job.keys[m], (long)job.versions[m] << 32 | masks[m] & 4294967295L);
         }

         return hidden;
      }

      private static int row(double slope) {
         return (int)Math.floor((Math.atan(slope) + (Math.PI / 2)) / 7.669903939428206E-4);
      }

      private void occluder(LodOcclusion.Job job, int low, int high, double minX, double minZ, double width) {
         double lo = low + job.margin;
         double hi = high - job.margin;
         if (low < high && !(lo >= hi)) {
            double maxX = minX + width;
            double maxZ = minZ + width;
            double near = LodOcclusion.distance(job.x, job.z, minX, maxX, minZ, maxZ);
            if (!(near < 12.0)) {
               double far = LodOcclusion.farthest(job.x, job.z, minX, maxX, minZ, maxZ);
               double top = (hi - job.y) / (hi >= job.y ? far : near);
               double bottom = (lo - job.y) / (lo >= job.y ? near : far);
               if (!(bottom >= top) && LodOcclusion.azimuth(job.x, job.z, minX, maxX, minZ, maxZ, this.corner, this.angles)) {
                  int rowLow = Math.max(0, row(bottom) + 1);
                  int rowHigh = Math.min(4095, row(top) - 1);
                  int from = (int)Math.ceil(this.corner[0] / 7.669903939428206E-4);
                  int to = (int)Math.floor(this.corner[1] / 7.669903939428206E-4) - 1;
                  if (rowLow <= rowHigh && from <= to) {
                     this.growOccluders();
                     int i = this.occluders++;
                     this.occDist[i] = far;
                     this.occFrom[i] = from;
                     this.occTo[i] = to;
                     this.occLow[i] = rowLow;
                     this.occHigh[i] = rowHigh;
                     this.occOrder[i] = (long)Math.ceil(far * 64.0) << 24 | i;
                  }
               }
            }
         }
      }

      private void target(LodOcclusion.Job job, int[] bounds, int part, int mesh, double x0, double z0, int cell) {
         int xz = bounds[part * 2];
         int y = bounds[part * 2 + 1];
         if (xz != 0 || y != 0) {
            double minX = x0 + (xz & 0xFF) * cell - 1.0;
            double maxX = x0 + (xz >>> 8 & 0xFF) * cell + 1.0;
            double minZ = z0 + (xz >>> 16 & 0xFF) * cell - 1.0;
            double maxZ = z0 + (xz >>> 24 & 0xFF) * cell + 1.0;
            double minY = Math.floorDiv(y & 65535, 16) - 2048 - 1;
            double maxY = Math.floorDiv((y >>> 16) + 15, 16) - 2048 + 1;
            double near = LodOcclusion.distance(job.x, job.z, minX, maxX, minZ, maxZ);
            if (!(near < 12.0) && LodOcclusion.azimuth(job.x, job.z, minX, maxX, minZ, maxZ, this.corner, this.angles)) {
               double far = LodOcclusion.farthest(job.x, job.z, minX, maxX, minZ, maxZ);
               double top = (maxY - job.y) / (maxY >= job.y ? near : far);
               double bottom = (minY - job.y) / (minY >= job.y ? far : near);
               int from = (int)Math.floor(this.corner[0] / 7.669903939428206E-4);
               int to = (int)Math.floor(this.corner[1] / 7.669903939428206E-4);
               if (to - from < 4096) {
                  int rowLow = row(bottom);
                  int rowHigh = row(top);
                  if (rowLow >= 0 && rowHigh < 4096) {
                     this.growTargets();
                     int i = this.targets++;
                     this.targetFrom[i] = from;
                     this.targetTo[i] = to;
                     this.targetLow[i] = rowLow;
                     this.targetHigh[i] = rowHigh;
                     this.targetMesh[i] = mesh;
                     this.targetPart[i] = part;
                     this.targetOrder[i] = (long)Math.floor(near * 64.0) << 24 | i;
                  }
               }
            }
         }
      }

      private void insert(int o) {
         int from = this.occFrom[o];
         int to = this.occTo[o];
         int low = this.occLow[o];
         int high = this.occHigh[o];
         this.lowRow = Math.min(this.lowRow, low);
         this.highRow = Math.max(this.highRow, high);

         for (int r = low; r <= high; r++) {
            int base = r * 128;
            int c = from;

            while (c <= to) {
               int bin = c & 8191;
               int bit = bin & 63;
               int span = Math.min(64 - bit, to - c + 1);
               this.bits[base + (bin >>> 6)] = this.bits[base + (bin >>> 6)] | (span == 64 ? -1L : (1L << span) - 1L << bit);
               c += span;
            }
         }
      }

      private boolean covered(int t) {
         int from = this.targetFrom[t];
         int to = this.targetTo[t];
         int low = this.targetLow[t];
         int high = this.targetHigh[t];
         if (low >= this.lowRow && high <= this.highRow) {
            for (int r = low; r <= high; r++) {
               int base = r * 128;
               int c = from;

               while (c <= to) {
                  int bin = c & 8191;
                  int bit = bin & 63;
                  int span = Math.min(64 - bit, to - c + 1);
                  long mask = span == 64 ? -1L : (1L << span) - 1L << bit;
                  if ((this.bits[base + (bin >>> 6)] & mask) != mask) {
                     return false;
                  }

                  c += span;
               }
            }

            return true;
         } else {
            return false;
         }
      }

      private void growOccluders() {
         if (this.occluders >= this.occDist.length) {
            int size = this.occDist.length * 2;
            this.occDist = Arrays.copyOf(this.occDist, size);
            this.occFrom = Arrays.copyOf(this.occFrom, size);
            this.occTo = Arrays.copyOf(this.occTo, size);
            this.occLow = Arrays.copyOf(this.occLow, size);
            this.occHigh = Arrays.copyOf(this.occHigh, size);
            this.occOrder = Arrays.copyOf(this.occOrder, size);
         }
      }

      private void growTargets() {
         if (this.targets >= this.targetFrom.length) {
            int size = this.targetFrom.length * 2;
            this.targetFrom = Arrays.copyOf(this.targetFrom, size);
            this.targetTo = Arrays.copyOf(this.targetTo, size);
            this.targetLow = Arrays.copyOf(this.targetLow, size);
            this.targetHigh = Arrays.copyOf(this.targetHigh, size);
            this.targetMesh = Arrays.copyOf(this.targetMesh, size);
            this.targetPart = Arrays.copyOf(this.targetPart, size);
            this.targetOrder = Arrays.copyOf(this.targetOrder, size);
         }
      }
   }

   static final class Result {
      final double x;
      final double y;
      final double z;
      final double tolerance;
      final long hash;
      final Long2LongOpenHashMap visible;
      final int hiddenSubs;
      final long nanos;

      Result(LodOcclusion.Job job, Long2LongOpenHashMap visible, int hiddenSubs, long nanos) {
         this.x = job.x;
         this.y = job.y;
         this.z = job.z;
         this.tolerance = job.tolerance;
         this.hash = job.hash;
         this.visible = visible;
         this.hiddenSubs = hiddenSubs;
         this.nanos = nanos;
      }

      boolean covers(double x, double y, double z, long hash) {
         return hash == this.hash && Math.abs(x - this.x) <= this.tolerance && Math.abs(z - this.z) <= this.tolerance && Math.abs(y - this.y) <= this.tolerance;
      }
   }
}
