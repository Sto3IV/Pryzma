package net.pryzma.farlands.gen;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.Holder.Reference;
import net.minecraft.world.level.biome.Biome;

public final class BiomeIds {
   private final Registry<Biome> target;
   private final Map<Holder<Biome>, Integer> cache = new ConcurrentHashMap<>();

   public BiomeIds(Registry<Biome> target) {
      this.target = target;
   }

   public int id(Holder<Biome> holder) {
      Integer known = this.cache.get(holder);
      if (known != null) {
         return known;
      }

      int id = holder.unwrapKey().<Reference>flatMap(this.target::get).map(reference -> this.target.getId((Biome)reference.value())).orElse(0);
      this.cache.put(holder, Math.max(0, id));
      return Math.max(0, id);
   }
}
