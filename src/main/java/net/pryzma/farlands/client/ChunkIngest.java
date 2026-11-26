package net.pryzma.farlands.client;

import net.pryzma.farlands.gen.BiomeIds;
import net.pryzma.farlands.lod.BlockShapes;
import net.pryzma.farlands.lod.ChunkColumns;
import net.pryzma.farlands.lod.LodWorld;
import net.pryzma.farlands.lod.Span;
import net.pryzma.farlands.work.WorkerPool;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.jspecify.annotations.Nullable;

public final class ChunkIngest {
   private static final int MAX_SPANS = 14;
   private static final int SOLID_DEPTH = 6;
   private static final int MAX_DEPTH = 96;
   private static final int OPEN_GAP = 8;
   private static final int MAX_TOTAL = 40;
   private static final ThreadLocal<ChunkColumns.Builder> BUILDERS = ThreadLocal.withInitial(ChunkColumns.Builder::new);

   private ChunkIngest() {
   }

   public static void submit(LevelChunk chunk, LodWorld world, WorkerPool pool, BiomeIds biomes, boolean skyLight) {
      LevelChunkSection[] sections = chunk.getSections();
      int top = -1;

      for (int i = sections.length - 1; i >= 0; i--) {
         if (!sections[i].hasOnlyAir()) {
            top = i;
            break;
         }
      }

      if (top >= 0) {
         int highest = top;
         int lowest = 0;
         PalettedContainer<BlockState>[] states = new PalettedContainer[highest - 0 + 1];

         for (int i = 0; i <= highest; i++) {
            states[i - 0] = sections[i].hasOnlyAir() ? null : sections[i].getStates().copy();
         }

         int[] biomeIds = biomeIds(sections, 0, highest, biomes);
         int minY = chunk.getMinY();
         int sectionBase = chunk.getMinSectionY();
         int chunkX = chunk.getPos().x();
         int chunkZ = chunk.getPos().z();
         int sky = skyLight ? 15 : 0;
         pool.submit(-500.0, () -> world.ingest(build(states, 0, highest, sectionBase, minY, chunkX, chunkZ, biomeIds, sky)));
      }
   }

   public static @Nullable ChunkColumns columns(LevelChunkSection[] sections, int sectionBase, int minY, int chunkX, int chunkZ, BiomeIds biomes, int sky) {
      int top = -1;

      for (int i = sections.length - 1; i >= 0; i--) {
         if (sections[i] != null && !sections[i].hasOnlyAir()) {
            top = i;
            break;
         }
      }

      if (top < 0) {
         return null;
      }

      int lowest = 0;
      PalettedContainer<BlockState>[] states = new PalettedContainer[top - 0 + 1];

      for (int i = 0; i <= top; i++) {
         states[i - 0] = sections[i] != null && !sections[i].hasOnlyAir() ? sections[i].getStates() : null;
      }

      int[] biomeIds = biomeIds(sections, 0, top, biomes);
      return build(states, 0, top, sectionBase, minY, chunkX, chunkZ, biomeIds, sky);
   }

   private static int[] biomeIds(LevelChunkSection[] sections, int lowest, int highest, BiomeIds biomes) {
      int[] ids = new int[(highest - lowest + 1) * 64];

      for (int s = lowest; s <= highest; s++) {
         LevelChunkSection section = sections[s];
         if (section != null) {
            for (int q = 0; q < 64; q++) {
               ids[(s - lowest) * 64 + q] = biomes.id(section.getNoiseBiome(q & 3, q >> 4, q >> 2 & 3));
            }
         }
      }

      return ids;
   }

   private static ChunkColumns build(
      PalettedContainer<BlockState>[] states, int lowest, int highest, int sectionBase, int minY, int chunkX, int chunkZ, int[] biomeIds, int sky
   ) {
      ChunkColumns.Builder builder = BUILDERS.get().reset();
      int topY = (sectionBase + highest) * 16 + 15;
      int bottomY = (sectionBase + lowest) * 16;

      for (int z = 0; z < 16; z++) {
         for (int x = 0; x < 16; x++) {
            builder.beginColumn();
            int biome = 0;
            int spans = 0;
            int solid = 0;
            int started = Integer.MIN_VALUE;
            int lastId = -1;
            int lastY = Integer.MIN_VALUE;
            int covered = 0;
            int spanSky = sky;
            boolean skipping = false;
            boolean draining = false;
            boolean drained = false;
            int skipId = -1;
            int drainId = -1;
            int drainTop = 0;
            int skipTop = 0;
            int skipBottom = 0;
            int gap = 0;
            int total = 0;

            for (int y = topY; y >= bottomY; y--) {
               PalettedContainer<BlockState> container = states[y - bottomY >> 4];
               if (container == null) {
                  int sectionBottom = (y - bottomY >> 4 << 4) + bottomY;
                  if (started != Integer.MIN_VALUE) {
                     gap += y - sectionBottom + 1;
                     solid = 0;
                  }

                  y = sectionBottom;
               } else {
                  BlockState state = (BlockState)container.get(x, y & 15, z);
                  int id = BlockShapes.stored(state);
                  if (id == -1) {
                     if (started != Integer.MIN_VALUE) {
                        gap++;
                        solid = 0;
                     }
                  } else {
                     if (draining) {
                        if (gap < 8) {
                           gap = 0;
                           BlockState fill = Block.stateById(id);
                           if (fill.getBlock() instanceof LiquidBlock) {
                              draining = false;
                              drained = true;
                              skipping = true;
                              skipId = id;
                              skipTop = y + 1;
                              skipBottom = y;
                           } else if (fill.canOcclude()) {
                              if (solid++ == 0) {
                                 drainId = id;
                                 drainTop = y + 1;
                              }

                              if (solid >= 6) {
                                 draining = false;
                                 drained = true;
                                 skipping = true;
                                 skipId = drainId;
                                 skipTop = drainTop;
                                 skipBottom = y;
                              }
                           } else {
                              solid = 0;
                           }
                           continue;
                        }

                        draining = false;
                        started = Integer.MIN_VALUE;
                        spans = 0;
                        lastId = -1;
                     } else if (skipping) {
                        if (gap < 8) {
                           gap = 0;
                           if (drained && Block.stateById(skipId).getBlock() instanceof LiquidBlock && Block.stateById(id).canOcclude()) {
                              builder.add(Span.pack(skipTop, y + 1, skipId, biome, spanSky, 0));
                              total++;
                              spanSky = Math.max(4, spanSky - (skipTop - y - 1));
                              skipId = id;
                              skipTop = y + 1;
                              drained = false;
                           }

                           skipBottom = y;
                           continue;
                        }

                        if (skipBottom < skipTop) {
                           builder.add(Span.pack(skipTop, skipBottom, skipId, biome, spanSky, 0));
                           total++;
                        }

                        skipping = false;
                        started = Integer.MIN_VALUE;
                        spans = 0;
                        lastId = -1;
                     } else if (gap >= 8) {
                        started = Integer.MIN_VALUE;
                        spans = 0;
                     }

                     gap = 0;
                     if (started == Integer.MIN_VALUE) {
                        started = y;
                        biome = biomeIds[(y - bottomY >> 4) * 64 + (((y & 15) >> 2) * 4 + (z >> 2)) * 4 + (x >> 2)];
                     }

                     if (id != lastId || y + 1 != lastY) {
                        spanSky = Math.max(4, sky - covered);
                     }

                     if (!state.getFluidState().isEmpty() || state.is(BlockTags.ICE)) {
                        covered++;
                     }

                     BlockState stored = Block.stateById(id);
                     if (id != lastId || y + 1 != lastY) {
                        total++;
                     }

                     builder.add(Span.pack(y + 1, y, id, biome, spanSky, stored.getLightEmission()));
                     if (id != lastId) {
                        spans++;
                     }

                     lastId = id;
                     lastY = y;
                     if (stored.canOcclude()) {
                        solid++;
                     } else {
                        solid = 0;
                     }

                     if (solid >= 6 || spans >= 14 || started - y >= 96 || total >= 40) {
                        if (!stored.canOcclude() && !(stored.getBlock() instanceof LiquidBlock)) {
                           draining = true;
                           solid = 0;
                        } else {
                           skipping = true;
                           drained = false;
                           skipId = lastId;
                           skipTop = y;
                           skipBottom = y;
                        }
                     }
                  }
               }
            }

            if (skipping) {
               int restBottom = gap >= 8 ? skipBottom : minY;
               if (restBottom < skipTop) {
                  builder.add(Span.pack(skipTop, restBottom, skipId, biome, spanSky, 0));
               }
            } else if (draining && solid > 0 && drainTop > minY) {
               builder.add(Span.pack(drainTop, minY, drainId, biome, spanSky, 0));
            }

            builder.endColumn();
         }
      }

      return builder.build(chunkX, chunkZ);
   }
}
