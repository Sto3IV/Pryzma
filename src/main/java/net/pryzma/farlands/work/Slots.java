package net.pryzma.farlands.work;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

public final class Slots<T> {
   private final ConcurrentLinkedQueue<T> idle = new ConcurrentLinkedQueue<>();
   private final Supplier<T> factory;

   public Slots(Supplier<T> factory) {
      this.factory = factory;
   }

   public T take() {
      T value = this.idle.poll();
      return value != null ? value : this.factory.get();
   }

   public void give(T value) {
      this.idle.add(value);
   }

   public void clear() {
      this.idle.clear();
   }
}
