package net.pryzma.farlands;

public final class FarlandsSettings {
   public static volatile boolean enabled = true;
   public static volatile int lodChunks = 128;
   public static volatile int requestedLodChunks = 128;
   public static volatile int serverLodLimit = Integer.MAX_VALUE;
   public static volatile FarlandsSettings.Quality quality = FarlandsSettings.Quality.HIGH;
   public static volatile int threads = defaultThreads();
   public static volatile boolean generate = true;
   public static volatile boolean vegetation = true;
   public static volatile boolean textures = true;
   public static volatile boolean fog = true;
   public static volatile int fogMode = 2;
   public static volatile boolean scaleModFog;
   public static volatile String serverSeed = "";
   private static volatile int version;

   private FarlandsSettings() {
   }

   public static int defaultThreads() {
      return syncThreadsFor(net.pryzma.PryzmaConfig.prChunkUpdates);
   }

   public static int syncThreadsFor(int chunkUpdates) {
      int cores = Runtime.getRuntime().availableProcessors();
      int chunkThreads = net.pryzma.perf.PrChunkWorkers.threadsFor(chunkUpdates);
      int max = maxThreads();
      int available = Math.max(1, cores - chunkThreads - 2);
      int target = switch (Math.clamp(chunkUpdates, 1, 5)) {
         case 1 -> 1;
         case 2 -> Math.min(2, available);
         case 3 -> Math.min(Math.max(2, cores / 4), available);
         case 4 -> Math.min(Math.max(3, cores / 3), available);
         case 5 -> Math.min(Math.max(2, cores / 4), Math.max(1, available));
         default -> Math.min(6, available);
      };
      return Math.clamp(target, 1, max);
   }

   public static int maxThreads() {
      return Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
   }

   public static void limitLod(int limit) {
      serverLodLimit = limit;
      int effective = Math.min(requestedLodChunks, limit);
      if (effective != lodChunks) {
         lodChunks = effective;
         changed();
      }
   }

   public static void changed() {
      version++;
   }

   public static int version() {
      return version;
   }

   public enum Quality {
      LOW(6.0F),
      MEDIUM(4.0F),
      HIGH(3.0F),
      ULTRA(2.0F);

      public final float pixelsPerCell;

      Quality(final float pixelsPerCell) {
         this.pixelsPerCell = pixelsPerCell;
      }

      public int cellsPerLevel(int screenHeight, double fovDegrees) {
         double focal = Math.max(1, screenHeight) / (2.0 * Math.tan(Math.toRadians(Math.max(30.0, Math.min(110.0, fovDegrees))) / 2.0));
         return (int)Math.max(48L, Math.min(768L, Math.round(focal / this.pixelsPerCell)));
      }
   }
}
