package net.pryzma.farlands.gen;

import net.pryzma.farlands.lod.LodTile;
import net.pryzma.farlands.lod.Span;
import net.pryzma.farlands.lod.TileBuilder;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Arrays;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.jspecify.annotations.Nullable;

final class FeatureStamper {
   private static final int SNOW = Block.getId(Blocks.SNOW.defaultBlockState());
   private static final int MAX_HANG = 12;
   private static final int COVER_LEVEL = 4;
   private static final int COVER_HEIGHT = 2;
   private static volatile byte[] looseCache = new byte[0];
   private final FeatureCatalog catalog;
   private final long seed;

   FeatureStamper(FeatureCatalog catalog, long seed) {
      this.catalog = catalog;
      this.seed = seed;
   }

   IntOpenHashSet learn(LodTile tile) {
      int step = 1 << tile.level;
      IntOpenHashSet seen = new IntOpenHashSet();

      for (int cz = -1; cz <= 64; cz++) {
         for (int cx = -1; cx <= 64; cx++) {
            if (tile.count(cx, cz) != 0) {
               int biome = Span.biome(tile.spans[tile.first(cx, cz)]);
               if (seen.add(biome)) {
                  this.catalog.kinds(biome, tile.originX() + cx * step, tile.originZ() + cz * step);
               }
            }
         }
      }

      return seen;
   }

   LodTile stamp(LodTile tile, boolean @Nullable [] touched, @Nullable Map<PlacedFeature, LongOpenHashSet> realChunks) {
      int step = 1 << tile.level;
      int originX = tile.originX();
      int originZ = tile.originZ();
      int[] ground = new int[4356];
      int[] fluid = new int[4356];
      int[] biome = new int[4356];
      Block[] soil = new Block[4356];
      boolean[] snow = new boolean[4356];
      Int2ObjectOpenHashMap<FeatureCatalog.Kind[]> kinds = new Int2ObjectOpenHashMap();

      for (int cz = -1; cz <= 64; cz++) {
         for (int cx = -1; cx <= 64; cx++) {
            int c = LodTile.cell(cx, cz);
            ground[c] = Integer.MIN_VALUE;
            fluid[c] = Integer.MIN_VALUE;
            int from = tile.first(cx, cz);
            int count = tile.count(cx, cz);
            int i = 0;
            if (count > 0 && Span.state(tile.spans[from]) == SNOW) {
               snow[c] = true;
               i++;
            }

            for (; i < count; i++) {
               long span = tile.spans[from + i];
               BlockState state = Block.stateById(Span.state(span));
               if (!state.getFluidState().isEmpty()) {
                  if (fluid[c] == Integer.MIN_VALUE) {
                     fluid[c] = Span.top(span);
                  }
               } else if (!loose(Span.state(span))) {
                  ground[c] = Span.top(span);
                  soil[c] = state.getBlock();
                  break;
               }
            }

            if (count > 0) {
               biome[c] = Span.biome(tile.spans[from]);
               if (!kinds.containsKey(biome[c])) {
                  kinds.put(biome[c], this.catalog.kinds(biome[c], originX + cx * step, originZ + cz * step));
               }
            }
         }
      }

      TileBuilder builder = null;
      LongArrayList pieces = new LongArrayList();

      for (int cz = -1; cz <= 64; cz++) {
         for (int cx = -1; cx <= 64; cx++) {
            int c = LodTile.cell(cx, cz);
            if ((ground[c] != Integer.MIN_VALUE || fluid[c] != Integer.MIN_VALUE) && (touched == null || !touched[c])) {
               FeatureCatalog.Kind[] options = (FeatureCatalog.Kind[])kinds.get(biome[c]);
               if (options != null && options.length != 0) {
                  int wx = originX + cx * step;
                  int wz = originZ + cz * step;
                  FeatureTemplate best = null;
                  int bestFirst = 0;
                  int bestBase = 0;
                  int bestTop = Integer.MIN_VALUE;
                  int bestDx = 0;
                  int bestDz = 0;
                  boolean bestRebase = false;

                  for (FeatureCatalog.Kind kind : options) {
                     if (realChunks == null || kind.anchor != 2) {
                        int g = kind.spacing;
                        int r = kind.reach;
                        LongOpenHashSet real = realChunks == null ? null : realChunks.get(kind.feature);

                        for (int gz = Math.floorDiv(wz - r, g); gz <= Math.floorDiv(wz + r, g); gz++) {
                           for (int gx = Math.floorDiv(wx - r, g); gx <= Math.floorDiv(wx + r, g); gx++) {
                              long h = mix(this.seed ^ kind.id * -7046029254386353131L ^ gx * -4417276706812531889L ^ gz * 1609587929392839161L);
                              if (!((float)(h >>> 40) * 5.9604645E-8F >= kind.presence)) {
                                 long j = mix(h);
                                 int ax = gx * g + (int)((j & 65535L) * g >>> 16);
                                 int az = gz * g + (int)((j >>> 16 & 65535L) * g >>> 16);
                                 int ox = wx - ax;
                                 int oz = wz - az;
                                 if (Math.abs(ox) <= r && Math.abs(oz) <= r && (real == null || !real.contains(ChunkPos.pack(ax >> 4, az >> 4)))) {
                                    int rotation = (int)(j >>> 32) & 3;
                                    FeatureTemplate template = kind.templates[(int)(j >>> 34 & 134217727L) % kind.templates.length];
                                    int dx = rotateX(4 - rotation & 3, ox, oz);
                                    int dz = rotateZ(4 - rotation & 3, ox, oz);
                                    int first = template.first(dx, dz);
                                    if (first >= 0) {
                                       int ac = LodTile.cell(clamp(Math.floorDiv(ax - originX, step)), clamp(Math.floorDiv(az - originZ, step)));
                                       int base;
                                       if (kind.anchor == 2) {
                                          base = 0;
                                       } else if (kind.anchor == 1) {
                                          if (fluid[ac] == Integer.MIN_VALUE) {
                                             continue;
                                          }

                                          base = fluid[ac];
                                       } else {
                                          if (ground[ac] == Integer.MIN_VALUE || soil[ac] == null || !kind.soils.isEmpty() && !kind.soils.contains(soil[ac])) {
                                             continue;
                                          }

                                          boolean wet = fluid[ac] != Integer.MIN_VALUE && fluid[ac] > ground[ac];
                                          if (kind.water == 1 && !wet || kind.water == 0 && wet) {
                                             continue;
                                          }

                                          base = ground[ac];
                                       }

                                       int top = base + FeatureTemplate.top(template.run(first));
                                       int floor = Math.max(ground[c], fluid[c]);
                                       if ((
                                             kind.anchor == 2
                                                || floor == Integer.MIN_VALUE
                                                || hang(template, first, template.end(dx, dz), base, floor) <= Math.max(12, template.height / 2)
                                          )
                                          && top > bestTop) {
                                          best = template;
                                          bestFirst = first;
                                          bestBase = base;
                                          bestTop = top;
                                          bestDx = dx;
                                          bestDz = dz;
                                          bestRebase = kind.anchor == 0
                                             && ground[c] != Integer.MIN_VALUE
                                             && wet(ground[c], fluid[c]) == wet(ground[ac], fluid[ac]);
                                       }
                                    }
                                 }
                              }
                           }
                        }
                     }
                  }

                  if (best != null) {
                     if (builder == null) {
                        builder = TileBuilder.borrow();
                        builder.copyFrom(tile);
                     }

                     this.merge(
                        builder,
                        tile,
                        cx,
                        cz,
                        best,
                        bestFirst,
                        best.end(bestDx, bestDz),
                        bestBase,
                        biome[c],
                        snow[c],
                        pieces,
                        ground[c],
                        fluid[c],
                        bestRebase,
                        step > 1
                     );
                  }
               }
            }
         }
      }

      return builder == null ? tile : builder.build(tile.level, tile.tx, tile.tz, tile.realMask, tile.complete);
   }

   private static boolean wet(int ground, int fluid) {
      return fluid != Integer.MIN_VALUE && fluid > ground;
   }

   private static boolean cover(long run) {
      return FeatureTemplate.bottom(run) <= 4 && FeatureTemplate.top(run) - FeatureTemplate.bottom(run) <= 2 && loose(FeatureTemplate.state(run));
   }

   static boolean loose(int stateId) {
      byte[] cache = looseCache;
      if (stateId < cache.length && cache[stateId] != 0) {
         return cache[stateId] == 2;
      }

      BlockState state = Block.stateById(stateId);
      boolean loose = state.getFluidState().isEmpty()
         && state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty()
         && !Block.isShapeFullBlock(state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
      remember(stateId, loose);
      return loose;
   }

   private static void remember(int stateId, boolean loose) {
      byte[] cache = looseCache;
      if (stateId >= cache.length) {
         synchronized (FeatureStamper.class) {
            cache = looseCache;
            if (stateId >= cache.length) {
               cache = Arrays.copyOf(cache, Math.max(stateId + 1, cache.length * 2));
               looseCache = cache;
            }
         }
      }

      cache[stateId] = (byte)(loose ? 2 : 1);
   }

   private static int hang(FeatureTemplate template, int first, int end, int base, int floor) {
      int lowest = Integer.MAX_VALUE;

      for (int i = first; i < end; i++) {
         if (!cover(template.run(i))) {
            int bottom = base + FeatureTemplate.bottom(template.run(i));
            if (bottom >= floor) {
               lowest = Math.min(lowest, bottom);
            }
         }
      }

      return lowest == Integer.MAX_VALUE ? 0 : lowest - floor;
   }

   private void merge(
      TileBuilder builder,
      LodTile tile,
      int cx,
      int cz,
      FeatureTemplate template,
      int first,
      int end,
      int base,
      int biome,
      boolean snow,
      LongArrayList pieces,
      int ground,
      int fluid,
      boolean rebase,
      boolean fill
   ) {
      int capacity = end - first + 1;
      int[] tops = new int[capacity];
      int[] bottoms = new int[capacity];
      int[] states = new int[capacity];
      int runs = 0;
      int coverState = -1;
      int coverHeight = 0;

      for (int i = first; i < end; i++) {
         long run = template.run(i);
         if (cover(run)) {
            if (rebase && coverState < 0) {
               coverState = FeatureTemplate.state(run);
               coverHeight = FeatureTemplate.top(run) - FeatureTemplate.bottom(run);
            }
         } else {
            int top = base + FeatureTemplate.top(run);
            if (ground == Integer.MIN_VALUE || top > ground) {
               int bottom = base + FeatureTemplate.bottom(run);
               if (ground != Integer.MIN_VALUE && bottom < ground && loose(FeatureTemplate.state(run))) {
                  bottom = ground;
               }

               tops[runs] = top;
               bottoms[runs] = bottom;
               states[runs] = FeatureTemplate.state(run);
               runs++;
            }
         }
      }

      int coverTop = coverState < 0 ? Integer.MIN_VALUE : ground + coverHeight;
      if (fill && runs > 0) {
         for (int i = 0; i + 1 < runs; i++) {
            if (!loose(states[i])) {
               bottoms[i] = Math.min(bottoms[i], tops[i + 1]);
            }
         }

         int floor = coverState >= 0 ? coverTop : Math.max(ground, fluid);
         if (floor != Integer.MIN_VALUE && !loose(states[runs - 1])) {
            bottoms[runs - 1] = Math.min(bottoms[runs - 1], floor);
         }
      }

      if (coverState >= 0 && (runs == 0 || bottoms[runs - 1] >= coverTop)) {
         tops[runs] = coverTop;
         bottoms[runs] = ground;
         states[runs] = coverState;
         runs++;
      }

      if (runs != 0) {
         pieces.clear();
         int from = tile.first(cx, cz);
         int count = tile.count(cx, cz);

         for (int i = 0; i < runs; i++) {
            pieces.add(Span.pack(tops[i], bottoms[i], states[i], biome, skyAt(tile, from, count, tops[i] - 1), Block.stateById(states[i]).getLightEmission()));
         }

         for (int s = snow ? 1 : 0; s < count; s++) {
            long span = tile.spans[from + s];
            int top = Span.top(span);
            int bottom = Span.bottom(span);

            for (int i = 0; i < runs && top > bottom; i++) {
               if (bottoms[i] < top && tops[i] > bottom) {
                  if (tops[i] < top) {
                     pieces.add(Span.withBottom(Span.withTop(span, top), tops[i]));
                  }

                  top = Math.min(top, bottoms[i]);
               }
            }

            if (top > bottom) {
               pieces.add(Span.withTop(span, top));
            }
         }

         long[] order = new long[pieces.size()];

         for (int i = 0; i < order.length; i++) {
            order[i] = (long)(Span.top(pieces.getLong(i)) + 4096) << 32 | i;
         }

         Arrays.sort(order);
         builder.begin();
         if (order.length > 0 && snow) {
            long highest = pieces.getLong((int)order[order.length - 1]);
            BlockState state = Block.stateById(Span.state(highest));
            if (state.getFluidState().isEmpty() && NoiseTerrain.holdsSnow(state)) {
               builder.add(Span.pack(Span.top(highest) + 1, Span.top(highest), SNOW, biome, 15, 0));
            }
         }

         for (int i = order.length - 1; i >= 0; i--) {
            long span = pieces.getLong((int)order[i]);
            if (Span.top(span) > Span.bottom(span)) {
               builder.add(span);
            }
         }

         builder.end(cx, cz);
      }
   }

   private static int skyAt(LodTile tile, int from, int count, int y) {
      for (int i = 0; i < count; i++) {
         long span = tile.spans[from + i];
         if (Span.top(span) <= y) {
            return 15;
         }

         if (Span.bottom(span) <= y) {
            return Block.stateById(Span.state(span)).getFluidState().isEmpty() ? 15 : Math.max(0, Span.sky(span) - (Span.top(span) - 1 - y));
         }
      }

      return 15;
   }

   private static int clamp(int cell) {
      return Math.max(-1, Math.min(64, cell));
   }

   private static int rotateX(int rotation, int x, int z) {
      return switch (rotation) {
         case 1 -> -z;
         case 2 -> -x;
         case 3 -> z;
         default -> x;
      };
   }

   private static int rotateZ(int rotation, int x, int z) {
      return switch (rotation) {
         case 1 -> x;
         case 2 -> -z;
         case 3 -> -x;
         default -> z;
      };
   }

   private static long mix(long z) {
      z = (z ^ z >>> 30) * -4658895280553007687L;
      z = (z ^ z >>> 27) * -7723592293110705685L;
      return z ^ z >>> 31;
   }
}
