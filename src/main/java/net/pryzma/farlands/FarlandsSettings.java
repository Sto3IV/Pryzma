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
   public static volatile boolean scaleModFog;
   public static volatile String serverSeed = "";
   private static volatile int version;

   private FarlandsSettings() {
   }

   public static int defaultThreads() {
      int cores = Runtime.getRuntime().availableProcessors();
      return Math.max(1, Math.min(6, cores / 3));
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
