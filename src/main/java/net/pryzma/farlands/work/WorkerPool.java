package net.pryzma.farlands.work;

import net.pryzma.farlands.Farlands;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.jspecify.annotations.Nullable;

public final class WorkerPool {
   private static final ThreadLocal<Boolean> WORKER = ThreadLocal.withInitial(() -> Boolean.FALSE);
   private static final long STUCK_NANOS = 30000000000L;
   private static final int MAX_REPLACEMENTS = 64;
   private static final int RUNNING = 0;
   private static final int FINISHED = 1;
   private static final int ABANDONED = 2;
   private final PriorityBlockingQueue<WorkerPool.Task> queue = new PriorityBlockingQueue<>();
   private final List<WorkerPool.Worker> workers = new ArrayList<>();
   private final AtomicLong order = new AtomicLong();
   private final AtomicInteger busy = new AtomicInteger();
   private final AtomicInteger replacements = new AtomicInteger();
   private final AtomicLong lastFailureLog = new AtomicLong();
   private final AtomicInteger hiddenFailures = new AtomicInteger();
   private final Map<Thread, WorkerPool.Run> active = new ConcurrentHashMap<>();
   private final String name;
   private volatile int wanted;
   private volatile boolean running = true;

   public static boolean onWorker() {
      return WORKER.get();
   }

   public WorkerPool(String name, int count) {
      this.name = name;
      this.resize(count);
   }

   public synchronized void resize(int count) {
      this.wanted = Math.max(1, count);

      while (this.workers.size() < this.wanted) {
         this.start(this.workers.size());
      }
   }

   private void start(int index) {
      WorkerPool.Worker worker = new WorkerPool.Worker(index);
      Thread thread = new Thread(() -> this.loop(worker), this.name + " " + (index + 1));
      thread.setDaemon(true);
      thread.setPriority(2);
      if (index < this.workers.size()) {
         this.workers.set(index, worker);
      } else {
         this.workers.add(worker);
      }

      thread.start();
   }

   private void loop(WorkerPool.Worker self) {
      WORKER.set(Boolean.TRUE);

      while (this.running && !self.retired) {
         if (self.index >= this.wanted) {
            synchronized (this) {
               if (self.index >= this.wanted && self.index == this.workers.size() - 1 && this.workers.get(self.index) == self) {
                  this.workers.remove(self.index);
                  return;
               }
            }

            sleep(100L);
         } else {
            WorkerPool.Task task;
            try {
               task = this.queue.poll(250L, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
               if (this.running) {
                  continue;
               }

               return;
            }

            if (task != null) {
               if (self.retired) {
                  this.queue.add(task);
                  return;
               }

               this.busy.incrementAndGet();
               WorkerPool.Run run = new WorkerPool.Run(self, task, System.nanoTime());
               this.active.put(Thread.currentThread(), run);

               try {
                  task.job.run();
               } catch (OutOfMemoryError e) {
                  this.failed(e);
                  sleep(1000L);
               } catch (Throwable e) {
                  this.failed(e);
               } finally {
                  this.active.remove(Thread.currentThread(), run);
                  if (run.state.compareAndSet(0, 1)) {
                     this.busy.decrementAndGet();
                  }
               }
            }
         }
      }
   }

   private void failed(Throwable error) {
      long now = System.currentTimeMillis();
      long last = this.lastFailureLog.get();
      if (now - last >= 5000L && this.lastFailureLog.compareAndSet(last, now)) {
         int hidden = this.hiddenFailures.getAndSet(0);
         if (hidden > 0) {
            Farlands.LOG.warn("Background task failed ({} similar failures hidden)", hidden, error);
         } else {
            Farlands.LOG.warn("Background task failed", error);
         }
      } else {
         this.hiddenFailures.incrementAndGet();
      }
   }

   private static void sleep(long millis) {
      try {
         Thread.sleep(millis);
      } catch (InterruptedException ignored) {
         Thread.currentThread().interrupt();
      }
   }

   public void submit(double priority, Runnable job) {
      this.submit(priority, job, null);
   }

   public void submit(double priority, Runnable job, @Nullable Runnable cancel) {
      this.queue.add(new WorkerPool.Task(priority, this.order.incrementAndGet(), job, cancel));
   }

   public int queued() {
      return this.queue.size();
   }

   public int busy() {
      return this.busy.get();
   }

   public void reportStuck() {
      long now = System.nanoTime();

      for (Entry<Thread, WorkerPool.Run> entry : this.active.entrySet()) {
         WorkerPool.Run run = entry.getValue();
         if (now - run.start >= 30000000000L) {
            if (this.replacements.get() >= 64) {
               if (!run.reported) {
                  run.reported = true;
                  Throwable trace = new Throwable("Stack of " + entry.getKey().getName());
                  trace.setStackTrace(entry.getKey().getStackTrace());
                  Farlands.LOG.warn("Background task has been running for {} s", (now - run.start) / 1000000000L, trace);
               }
            } else if (run.state.compareAndSet(0, 2)) {
               this.busy.decrementAndGet();
               this.active.remove(entry.getKey(), run);
               Throwable trace = new Throwable("Stack of " + entry.getKey().getName());
               trace.setStackTrace(entry.getKey().getStackTrace());
               Farlands.LOG.warn("Background task has been running for {} s, a new worker takes over", (now - run.start) / 1000000000L, trace);
               if (run.task.cancel != null) {
                  try {
                     run.task.cancel.run();
                  } catch (Throwable e) {
                     this.failed(e);
                  }
               }

               this.replace(run.worker);
            }
         }
      }
   }

   private synchronized void replace(WorkerPool.Worker stuck) {
      stuck.retired = true;
      if (this.running && stuck.index < this.workers.size() && this.workers.get(stuck.index) == stuck) {
         this.replacements.incrementAndGet();
         this.start(stuck.index);
      }
   }

   public void clear() {
      List<WorkerPool.Task> dropped = new ArrayList<>();
      this.queue.drainTo(dropped);

      for (WorkerPool.Task task : dropped) {
         if (task.cancel != null) {
            try {
               task.cancel.run();
            } catch (Throwable e) {
               this.failed(e);
            }
         }
      }
   }

   public void shutdown() {
      this.running = false;
      this.clear();
   }

   private static final class Run {
      final WorkerPool.Worker worker;
      final WorkerPool.Task task;
      final long start;
      final AtomicInteger state = new AtomicInteger(0);
      volatile boolean reported;

      Run(WorkerPool.Worker worker, WorkerPool.Task task, long start) {
         this.worker = worker;
         this.task = task;
         this.start = start;
      }
   }

   public record Task(double priority, long order, Runnable job, @Nullable Runnable cancel) implements Comparable<WorkerPool.Task> {
      public int compareTo(WorkerPool.Task other) {
         int c = Double.compare(this.priority, other.priority);
         return c != 0 ? c : Long.compare(this.order, other.order);
      }
   }

   private static final class Worker {
      final int index;
      volatile boolean retired;

      Worker(int index) {
         this.index = index;
      }
   }
}
