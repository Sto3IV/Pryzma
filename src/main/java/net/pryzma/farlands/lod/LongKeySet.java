package net.pryzma.farlands.lod;

import it.unimi.dsi.fastutil.HashCommon;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class LongKeySet extends AbstractSet<Long> {
   private final Set<Long> set = ConcurrentHashMap.newKeySet();

   public boolean add(long key) {
      return this.set.add(HashCommon.mix(key));
   }

   public boolean remove(long key) {
      return this.set.remove(HashCommon.mix(key));
   }

   public boolean contains(long key) {
      return this.set.contains(HashCommon.mix(key));
   }

   public boolean add(Long key) {
      return this.add(key.longValue());
   }

   @Override
   public boolean remove(Object key) {
      return key instanceof Long value && this.remove(value.longValue());
   }

   @Override
   public boolean contains(Object key) {
      return key instanceof Long value && this.contains(value.longValue());
   }

   @Override
   public int size() {
      return this.set.size();
   }

   @Override
   public boolean isEmpty() {
      return this.set.isEmpty();
   }

   @Override
   public void clear() {
      this.set.clear();
   }

   @Override
   public Iterator<Long> iterator() {
      final Iterator<Long> inner = this.set.iterator();
      return new Iterator<Long>() {
         @Override
         public boolean hasNext() {
            return inner.hasNext();
         }

         public Long next() {
            return HashCommon.invMix(inner.next());
         }

         @Override
         public void remove() {
            inner.remove();
         }
      };
   }
}
