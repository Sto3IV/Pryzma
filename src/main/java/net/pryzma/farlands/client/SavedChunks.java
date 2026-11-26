package net.pryzma.farlands.client;

import net.pryzma.farlands.gen.BiomeIds;
import net.pryzma.farlands.lod.ChunkColumns;
import net.pryzma.farlands.lod.ChunkSupplier;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.chunk.storage.SerializableChunkData.SectionData;
import org.jspecify.annotations.Nullable;

public final class SavedChunks implements ChunkSupplier {
   private static final int BATCH = 64;
   private final ServerLevel level;
   private final BiomeIds biomes;
   private final int sky;

   public SavedChunks(ServerLevel level, BiomeIds biomes, boolean skyLight) {
      this.level = level;
      this.biomes = biomes;
      this.sky = skyLight ? 15 : 0;
   }

   @Override
   public @Nullable ChunkColumns load(int chunkX, int chunkZ) {
      try {
         return this.parse(
            (Optional<CompoundTag>)this.level.getChunkSource().chunkMap.read(new ChunkPos(chunkX, chunkZ)).get(5L, TimeUnit.SECONDS), chunkX, chunkZ
         );
      } catch (Exception e) {
         return null;
      }
   }

   @Override
   public Supplier<@Nullable ChunkColumns[]> request(long[] chunks) {
      CompletableFuture<Optional<CompoundTag>>[] reads = new CompletableFuture[chunks.length];

      for (int i = 0; i < chunks.length; i++) {
         try {
            reads[i] = this.level.getChunkSource().chunkMap.read(new ChunkPos(ChunkPos.getX(chunks[i]), ChunkPos.getZ(chunks[i])));
         } catch (RuntimeException e) {
            reads[i] = null;
         }
      }

      return () -> {
         ChunkColumns[] out = new ChunkColumns[chunks.length];

         for (int ix = 0; ix < chunks.length; ix++) {
            if (reads[ix] != null) {
               try {
                  out[ix] = this.parse(reads[ix].get(5L, TimeUnit.SECONDS), ChunkPos.getX(chunks[ix]), ChunkPos.getZ(chunks[ix]));
               } catch (Exception e) {
                  out[ix] = null;
               }
            }
         }

         return out;
      };
   }

   @Override
   public @Nullable ChunkColumns[] loadAll(long[] chunks) {
      ChunkColumns[] out = new ChunkColumns[chunks.length];
      CompletableFuture<Optional<CompoundTag>>[] reads = new CompletableFuture[Math.min(64, chunks.length)];

      for (int from = 0; from < chunks.length; from += 64) {
         int to = Math.min(chunks.length, from + 64);

         for (int i = from; i < to; i++) {
            try {
               reads[i - from] = this.level.getChunkSource().chunkMap.read(new ChunkPos(ChunkPos.getX(chunks[i]), ChunkPos.getZ(chunks[i])));
            } catch (RuntimeException e) {
               reads[i - from] = null;
            }
         }

         for (int i = from; i < to; i++) {
            CompletableFuture<Optional<CompoundTag>> read = reads[i - from];
            reads[i - from] = null;
            if (read != null) {
               try {
                  out[i] = this.parse(read.get(5L, TimeUnit.SECONDS), ChunkPos.getX(chunks[i]), ChunkPos.getZ(chunks[i]));
               } catch (Exception e) {
                  out[i] = null;
               }
            }
         }
      }

      return out;
   }

   private @Nullable ChunkColumns parse(Optional<CompoundTag> tag, int chunkX, int chunkZ) {
      try {
         if (tag.isEmpty()) {
            return null;
         }

         SerializableChunkData data = SerializableChunkData.parse(this.level, this.level.palettedContainerFactory(), tag.get());
         if (data != null && data.chunkStatus().isOrAfter(ChunkStatus.FULL)) {
            LevelChunkSection[] sections = new LevelChunkSection[this.level.getSectionsCount()];

            for (SectionData section : data.sectionData()) {
               int index = this.level.getSectionIndexFromSectionY(section.y());
               if (section.chunkSection() != null && index >= 0 && index < sections.length) {
                  sections[index] = section.chunkSection();
               }
            }

            return ChunkIngest.columns(sections, this.level.getMinSectionY(), this.level.getMinY(), chunkX, chunkZ, this.biomes, this.sky);
         } else {
            return null;
         }
      } catch (RuntimeException e) {
         return null;
      }
   }
}
