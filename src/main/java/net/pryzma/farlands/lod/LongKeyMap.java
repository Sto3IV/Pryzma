package net.pryzma.farlands.lod;

import it.unimi.dsi.fastutil.HashCommon;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongFunction;
import org.jspecify.annotations.Nullable;

public final class LongKeyMap<V> extends AbstractMap<Long, V> {
   private final ConcurrentHashMap<Long, V> map = new ConcurrentHashMap<>();

   public @Nullable V get(long key) {
      return this.map.get(HashCommon.mix(key));
   }

   public @Nullable V set(long key, V value) {
      return this.map.put(HashCommon.mix(key), value);
   }

   public @Nullable V setIfAbsent(long key, V value) {
      return this.map.putIfAbsent(HashCommon.mix(key), value);
   }

   public @Nullable V remove(long key) {
      return this.map.remove(HashCommon.mix(key));
   }

   public boolean containsKey(long key) {
      return this.map.containsKey(HashCommon.mix(key));
   }

   public V computeIfMissing(long key, LongFunction<? extends V> factory) {
      return this.map.computeIfAbsent(HashCommon.mix(key), mixed -> factory.apply(key));
   }

   @Override
   public @Nullable V get(Object key) {
      return key instanceof Long value ? this.get(value.longValue()) : null;
   }

   public @Nullable V put(Long key, V value) {
      return this.set(key, value);
   }

   @Override
   public @Nullable V remove(Object key) {
      return key instanceof Long value ? this.remove(value.longValue()) : null;
   }

   @Override
   public boolean containsKey(Object key) {
      return key instanceof Long value && this.containsKey(value.longValue());
   }

   @Override
   public int size() {
      return this.map.size();
   }

   @Override
   public boolean isEmpty() {
      return this.map.isEmpty();
   }

   @Override
   public void clear() {
      this.map.clear();
   }

   @Override
   public Collection<V> values() {
      return this.map.values();
   }

   @Override
   public Set<Long> keySet() {
      return new AbstractSet<Long>() {
         @Override
         public Iterator<Long> iterator() {
            final Iterator<Long> inner = LongKeyMap.this.map.keySet().iterator();
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

         @Override
         public int size() {
            return LongKeyMap.this.map.size();
         }
      };
   }

   @Override
   public Set<Entry<Long, V>> entrySet() {
      return new AbstractSet<Entry<Long, V>>() {
         @Override
         public Iterator<Entry<Long, V>> iterator() {
            final Iterator<Entry<Long, V>> inner = LongKeyMap.this.map.entrySet().iterator();
            return new Iterator<Entry<Long, V>>() {
               @Override
               public boolean hasNext() {
                  return inner.hasNext();
               }

               public Entry<Long, V> next() {
                  Entry<Long, V> entry = inner.next();
                  return new SimpleImmutableEntry<>(HashCommon.invMix(entry.getKey()), entry.getValue());
               }

               @Override
               public void remove() {
                  inner.remove();
               }
            };
         }

         @Override
         public int size() {
            return LongKeyMap.this.map.size();
         }
      };
   }
}
