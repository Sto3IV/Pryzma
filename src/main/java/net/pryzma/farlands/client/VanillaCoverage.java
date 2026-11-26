package net.pryzma.farlands.client;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

final class VanillaCoverage {
   private static final long STABLE_MILLIS = 150L;
   private static final int MAX_SECTIONS = 64;
   private final Long2LongOpenHashMap applied = new Long2LongOpenHashMap();
   private final Long2LongOpenHashMap waiting = new Long2LongOpenHashMap();
   private final Long2LongOpenHashMap waitingSince = new Long2LongOpenHashMap();
   private final LongArrayList order = new LongArrayList();
   private final MutableBlockPos pos = new MutableBlockPos();
   private boolean orderDirty = true;
   private int cursor;
   private int version;
   private int minSection;

   long bits(long chunk) {
      return this.applied.get(chunk);
   }

   int version() {
      return this.version;
   }

   int minSection() {
      return this.minSection;
   }

   void loaded() {
      this.orderDirty = true;
   }

   void unloaded(long chunk) {
      this.orderDirty = true;
      this.waiting.remove(chunk);
      this.waitingSince.remove(chunk);
      if (this.applied.remove(chunk) != 0L) {
         this.version++;
      }
   }

   void clear() {
      if (!this.applied.isEmpty()) {
         this.version++;
      }

      this.applied.clear();
      this.waiting.clear();
      this.waitingSince.clear();
      this.orderDirty = true;
   }

   void update(Minecraft mc, ClientLevel level, LongSet loaded) {
      this.minSection = level.getMinSectionY();
      if (this.orderDirty) {
         this.orderDirty = false;
         this.order.clear();
         this.order.addAll(loaded);
         this.cursor = 0;
      }

      int size = this.order.size();
      if (size != 0) {
         long now = Util.getMillis();
         int budget = Math.min(size, Math.max(64, size / 3));

         for (int i = 0; i < budget; i++) {
            if (this.cursor >= size) {
               this.cursor = 0;
            }

            long chunk = this.order.getLong(this.cursor++);
            this.refresh(mc, level, loaded, chunk, now);
         }
      }
   }

   private void refresh(Minecraft mc, ClientLevel level, LongSet loaded, long chunk, long now) {
      long built = this.scan(mc, level, loaded, chunk);
      long current = this.applied.get(chunk);
      long next = current & built;
      long gained = built & ~current;
      if (gained == 0L) {
         this.waiting.remove(chunk);
         this.waitingSince.remove(chunk);
      } else if (this.waiting.get(chunk) == gained && this.waitingSince.containsKey(chunk)) {
         if (now - this.waitingSince.get(chunk) >= 150L) {
            next |= gained;
            this.waiting.remove(chunk);
            this.waitingSince.remove(chunk);
         }
      } else {
         this.waiting.put(chunk, gained);
         this.waitingSince.put(chunk, now);
      }

      if (next != current) {
         if (next == 0L) {
            this.applied.remove(chunk);
         } else {
            this.applied.put(chunk, next);
         }

         this.version++;
      }
   }

   private long scan(Minecraft mc, ClientLevel level, LongSet loaded, long chunk) {
      int cx = ChunkPos.getX(chunk);
      int cz = ChunkPos.getZ(chunk);
      LevelChunk column = level.getChunkSource().getChunkNow(cx, cz);
      if (column == null) {
         return 0L;
      }

      boolean ready = true;

      for (int dz = -1; dz <= 1 && ready; dz++) {
         for (int dx = -1; dx <= 1; dx++) {
            if ((dx != 0 || dz != 0) && !loaded.contains(ChunkPos.pack(cx + dx, cz + dz))) {
               ready = false;
               break;
            }
         }
      }

      LevelChunkSection[] sections = column.getSections();
      int count = Math.min(64, sections.length);
      long bits = 0L;

      for (int i = 0; i < count; i++) {
         if (sections[i] != null && !sections[i].hasOnlyAir()) {
            this.pos.set(cx * 16 + 8, (this.minSection + i) * 16 + 8, cz * 16 + 8);
            if (mc.levelRenderer.isSectionCompiledAndVisible(this.pos, 0L)) {
               bits |= 1L << i;
            }
         } else if (ready) {
            bits |= 1L << i;
         }
      }

      return bits;
   }
}
