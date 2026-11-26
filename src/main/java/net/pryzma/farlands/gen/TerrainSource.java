package net.pryzma.farlands.gen;

import net.pryzma.farlands.lod.LodTile;
import org.jspecify.annotations.Nullable;

public interface TerrainSource {
   @Nullable LodTile generate(int var1, int var2, int var3);

   default @Nullable LodTile generate(int level, int tx, int tz, boolean preview) {
      return this.generate(level, tx, tz);
   }

   default void trim() {
   }

   default void close() {
   }
}
