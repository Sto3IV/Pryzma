package net.pryzma.farlands.gen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import org.jspecify.annotations.Nullable;

public final class PrefillBuffers {
   private static final ThreadLocal<PrefillBuffers> ACTIVE = new ThreadLocal<>();
   private static final int KEEP = 4;
   private final int minimum;
   private final List<PrefillBuffers.Buffer> free = new ArrayList<>();
   private final List<PrefillBuffers.Buffer> used = new ArrayList<>();

   PrefillBuffers(int minimum) {
      this.minimum = minimum;
   }

   public static @Nullable DensityBuffer take(int size) {
      PrefillBuffers buffers = ACTIVE.get();
      return buffers == null ? null : buffers.acquire(size);
   }

   void open() {
      ACTIVE.set(this);
   }

   void close() {
      ACTIVE.remove();
      this.free.addAll(this.used);
      this.used.clear();

      while (this.free.size() > 4) {
         this.free.removeFirst();
      }
   }

   private DensityBuffer acquire(int size) {
      PrefillBuffers.Buffer best = null;
      int index = -1;

      for (int i = 0; i < this.free.size(); i++) {
         PrefillBuffers.Buffer buffer = this.free.get(i);
         if (buffer.capacity() >= size && (best == null || buffer.capacity() < best.capacity())) {
            best = buffer;
            index = i;
         }
      }

      if (best == null) {
         best = new PrefillBuffers.Buffer(Math.max(size, this.minimum));
      } else {
         this.free.remove(index);
      }

      this.used.add(best);
      return best.prepare(size);
   }

   private static final class Buffer extends DensityBuffer {
      Buffer(int capacity) {
         super(capacity);
      }

      PrefillBuffers.Buffer prepare(int length) {
         this.size = length;
         this.fill(0.0F);
         return this;
      }
   }
}
