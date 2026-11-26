package net.pryzma.farlands.gen;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.IntSupplier;
import net.minecraft.world.level.levelgen.Heightmap.Types;

public final class BaseHeights {
   private static final int STRIPES = 64;
   private static final int STRIPE_LIMIT = 16384;
   private static final Map<Object, Long2IntOpenHashMap[]> CACHES = Collections.synchronizedMap(new WeakHashMap<>());

   private BaseHeights() {
   }

   public static int get(Object owner, Types type, int x, int z, IntSupplier compute) {
      Long2IntOpenHashMap[] stripes = CACHES.computeIfAbsent(owner, o -> create());
      long key = x & 67108863L | (z & 67108863L) << 26 | (long)type.ordinal() << 52;
      Long2IntOpenHashMap stripe = stripes[(int)(HashCommon.mix(key) & 63L)];
      synchronized (stripe) {
         int known = stripe.get(key);
         if (known != Integer.MIN_VALUE) {
            return known;
         }
      }

      int height = compute.getAsInt();
      synchronized (stripe) {
         if (stripe.size() >= 16384) {
            stripe.clear();
         }

         stripe.put(key, height);
         return height;
      }
   }

   private static Long2IntOpenHashMap[] create() {
      Long2IntOpenHashMap[] stripes = new Long2IntOpenHashMap[64];

      for (int i = 0; i < 64; i++) {
         stripes[i] = new Long2IntOpenHashMap();
         stripes[i].defaultReturnValue(Integer.MIN_VALUE);
      }

      return stripes;
   }
}
