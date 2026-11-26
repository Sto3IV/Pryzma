package net.pryzma.farlands;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.BooleanValue;
import net.neoforged.neoforge.common.ModConfigSpec.Builder;
import net.neoforged.neoforge.common.ModConfigSpec.IntValue;

public final class FarlandsServerConfig {
   public static final ModConfigSpec SPEC;
   public static final BooleanValue SHARE;
   public static final IntValue MAX_LOD_DISTANCE;
   public static final IntValue TILES_PER_SECOND;
   public static final IntValue KILOBYTES_PER_SECOND;

   private FarlandsServerConfig() {
   }

   public static boolean share() {
      try {
         return (Boolean)SHARE.get();
      } catch (IllegalStateException e) {
         return (Boolean)SHARE.getDefault();
      }
   }

   public static int maxLodDistance() {
      return value(MAX_LOD_DISTANCE);
   }

   public static int tilesPerSecond() {
      return value(TILES_PER_SECOND);
   }

   public static int kilobytesPerSecond() {
      return value(KILOBYTES_PER_SECOND);
   }

   private static int value(IntValue value) {
      try {
         return (Integer)value.get();
      } catch (IllegalStateException e) {
         return (Integer)value.getDefault();
      }
   }

   static {
      Builder b = new Builder();
      SHARE = b.define("shareWorldGeneration", true);
      MAX_LOD_DISTANCE = b.defineInRange("maxLodDistance", 128, 16, 512);
      TILES_PER_SECOND = b.defineInRange("biomeTilesPerSecond", 50, 1, 1000);
      KILOBYTES_PER_SECOND = b.defineInRange("uploadKilobytesPerSecond", 512, 16, 65536);
      SPEC = b.build();
   }
}
