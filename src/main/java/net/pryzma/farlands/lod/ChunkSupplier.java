package net.pryzma.farlands.lod;

import java.util.function.Supplier;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

public interface ChunkSupplier {
   @Nullable ChunkColumns load(int var1, int var2);

   default Supplier<@Nullable ChunkColumns[]> request(long[] chunks) {
      return () -> this.loadAll(chunks);
   }

   default @Nullable ChunkColumns[] loadAll(long[] chunks) {
      ChunkColumns[] out = new ChunkColumns[chunks.length];

      for (int i = 0; i < chunks.length; i++) {
         out[i] = this.load(ChunkPos.getX(chunks[i]), ChunkPos.getZ(chunks[i]));
      }

      return out;
   }
}
