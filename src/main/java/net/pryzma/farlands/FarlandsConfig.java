package net.pryzma.farlands;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.BooleanValue;
import net.neoforged.neoforge.common.ModConfigSpec.Builder;
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue;
import net.neoforged.neoforge.common.ModConfigSpec.EnumValue;
import net.neoforged.neoforge.common.ModConfigSpec.IntValue;

public final class FarlandsConfig {
   public static final ModConfigSpec SPEC;
   public static final BooleanValue ENABLED;
   public static final IntValue LOD_DISTANCE;
   public static final EnumValue<FarlandsSettings.Quality> QUALITY;
   public static final IntValue THREADS;
   public static final BooleanValue GENERATE;
   public static final BooleanValue VEGETATION;
   public static final BooleanValue TEXTURES;
   public static final BooleanValue FOG;
   public static final BooleanValue SCALE_MOD_FOG;
   public static final ConfigValue<String> SERVER_SEED;

   private FarlandsConfig() {
   }

   public static void apply() {
      FarlandsSettings.enabled = (Boolean)ENABLED.get();
      FarlandsSettings.requestedLodChunks = (Integer)LOD_DISTANCE.get();
      FarlandsSettings.lodChunks = Math.min(FarlandsSettings.requestedLodChunks, FarlandsSettings.serverLodLimit);
      FarlandsSettings.quality = (FarlandsSettings.Quality)QUALITY.get();
      FarlandsSettings.threads = (Integer)THREADS.get();
      FarlandsSettings.generate = (Boolean)GENERATE.get();
      FarlandsSettings.vegetation = (Boolean)VEGETATION.get();
      FarlandsSettings.textures = (Boolean)TEXTURES.get();
      FarlandsSettings.fog = (Boolean)FOG.get();
      FarlandsSettings.scaleModFog = (Boolean)SCALE_MOD_FOG.get();
      FarlandsSettings.serverSeed = (String)SERVER_SEED.get();
      FarlandsSettings.changed();
   }

   static {
      Builder b = new Builder();
      ENABLED = b.define("enabled", true);
      LOD_DISTANCE = b.defineInRange("lodDistance", 128, 16, 512);
      QUALITY = b.defineEnum("quality", FarlandsSettings.Quality.HIGH);
      THREADS = b.defineInRange("generationThreads", FarlandsSettings.defaultThreads(), 1, FarlandsSettings.maxThreads());
      GENERATE = b.define("generateTerrain", true);
      VEGETATION = b.define("vegetation", true);
      TEXTURES = b.define("textures", true);
      FOG = b.define("distantFog", true);
      SCALE_MOD_FOG = b.define("scaleModFog", false);
      SERVER_SEED = b.define("serverSeed", "");
      SPEC = b.build();
   }
}
