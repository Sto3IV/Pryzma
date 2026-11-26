package net.pryzma.farlands.net;

import net.pryzma.farlands.LoaderHooks;
import net.pryzma.farlands.FarlandsServerConfig;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

final class PlayerUpload {
   static final int MAX_QUEUED = 4096;
   static final ScheduledThreadPoolExecutor EXECUTOR = new ScheduledThreadPoolExecutor(1, runnable -> {
      Thread thread = new Thread(runnable, "Farlands Biomes");
      thread.setDaemon(true);
      thread.setPriority(1);
      return thread;
   });
   private static final long IDLE_NANOS = TimeUnit.MINUTES.toNanos(2L);
   private static final Map<UUID, PlayerUpload.Budget> BUDGETS = new ConcurrentHashMap<>();

   private PlayerUpload() {
   }

   static PlayerUpload.Budget budget(ServerPlayer player) {
      if (BUDGETS.size() > 256) {
         long now = System.nanoTime();
         BUDGETS.values().removeIf(budget -> {
            synchronized (budget) {
               return budget.queued == 0 && now - budget.lastUse > IDLE_NANOS;
            }
         });
      }

      return BUDGETS.computeIfAbsent(player.getUUID(), id -> new PlayerUpload.Budget());
   }

   static long reserveRequest(PlayerUpload.Budget budget) {
      synchronized (budget) {
         if (budget.queued >= 4096) {
            return -1L;
         }

         long now = System.nanoTime();
         long start = Math.max(now, budget.nextRequest);
         budget.nextRequest = start + 1000000000L / Math.max(1, FarlandsServerConfig.tilesPerSecond());
         budget.queued++;
         budget.lastUse = now;
         return start - now;
      }
   }

   static void finishRequest(PlayerUpload.Budget budget) {
      synchronized (budget) {
         budget.queued = Math.max(0, budget.queued - 1);
      }
   }

   static void send(MinecraftServer server, ServerPlayer player, CustomPacketPayload payload, int bytes) {
      PlayerUpload.Budget budget = budget(player);
      long delay;
      synchronized (budget) {
         long now = System.nanoTime();
         long start = Math.max(now, budget.nextByte);
         budget.nextByte = start + bytes * 1000000000L / (Math.max(16, FarlandsServerConfig.kilobytesPerSecond()) * 1024L);
         budget.lastUse = now;
         delay = start - now;
      }

      Runnable task = () -> {
         if (!player.hasDisconnected()) {
            LoaderHooks.sender.accept(player, payload);
         }
      };
      if (delay <= 0L) {
         server.execute(task);
      } else {
         try {
            EXECUTOR.schedule(() -> server.execute(task), delay, TimeUnit.NANOSECONDS);
         } catch (RejectedExecutionException e) {
            server.execute(task);
         }
      }
   }

   static void clear() {
      BUDGETS.clear();
   }

   static {
      EXECUTOR.setRemoveOnCancelPolicy(true);
   }

   static final class Budget {
      private long nextRequest;
      private long nextByte;
      private long lastUse = System.nanoTime();
      private int queued;
   }
}
