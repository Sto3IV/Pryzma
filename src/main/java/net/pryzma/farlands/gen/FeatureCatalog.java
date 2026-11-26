package net.pryzma.farlands.gen;

import net.pryzma.farlands.Farlands;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.jspecify.annotations.Nullable;

final class FeatureCatalog {
   static final int GROUND = 0;
   static final int SURFACE = 1;
   static final int FLOATING = 2;
   private static final float RARE_PER_CHUNK = 0.2F;
   private static final int MAX_TEMPLATES = 8;
   private static final int MAX_HEIGHT = 64;
   private static final int MAX_REACH = 16;
   private static final int MAX_INSTANCES = 4096;
   private static final int MIN_COLUMNS = 2048;
   private static final int EXTRA_PROBES = 4;
   private static final int SURVEY_LEVEL = 6;
   private static final int SURVEY_RADIUS = 2;
   static final int DRY = 0;
   static final int WET = 1;
   static final int EITHER = 2;
   static final FeatureCatalog.Kind[] NONE = new FeatureCatalog.Kind[0];
   private final FeatureCatalog.Prober prober;
   private final FeatureCatalog.Surveyor surveyor;
   private final Map<Integer, FeatureCatalog.Kind[]> ready = new ConcurrentHashMap<>();
   private final Map<Integer, Object> locks = new ConcurrentHashMap<>();
   private final Map<Integer, Set<PlacedFeature>> stamped = new ConcurrentHashMap<>();
   private final Map<Integer, Set<PlacedFeature>> observed = new ConcurrentHashMap<>();
   private final Map<Integer, Long> sites = new ConcurrentHashMap<>();
   private final Set<Integer> unsited = ConcurrentHashMap.newKeySet();
   private final Object surveyLock = new Object();
   private final int[][] surveyOrder = surveyOrder();
   private volatile int surveyed;

   FeatureCatalog(FeatureCatalog.Prober prober, FeatureCatalog.Surveyor surveyor) {
      this.prober = prober;
      this.surveyor = surveyor;
   }

   private static int[][] surveyOrder() {
      List<int[]> tiles = new ArrayList<>();

      for (int tz = -3; tz <= 2; tz++) {
         for (int tx = -3; tx <= 2; tx++) {
            tiles.add(new int[]{tx, tz, Math.max(Math.abs(2 * tx + 1), Math.abs(2 * tz + 1))});
         }
      }

      tiles.sort((a, b) -> a[2] != b[2] ? Integer.compare(a[2], b[2]) : (a[1] != b[1] ? Integer.compare(a[1], b[1]) : Integer.compare(a[0], b[0])));
      return tiles.toArray(new int[0][]);
   }

   private @Nullable Long site(int biome) {
      Long known = this.sites.get(biome);
      if (known == null && this.surveyed < this.surveyOrder.length) {
         synchronized (this.surveyLock) {
            int[] biomes = new int[4096];

            while (!this.sites.containsKey(biome) && this.surveyed < this.surveyOrder.length) {
               this.surveyNext(biomes);
            }

            return this.sites.get(biome);
         }
      } else {
         return known;
      }
   }

   int[] surveyedBiomes(int tiles) {
      synchronized (this.surveyLock) {
         int[] biomes = new int[4096];

         while (this.surveyed < Math.min(tiles, this.surveyOrder.length)) {
            this.surveyNext(biomes);
         }
      }

      return this.sites.keySet().stream().mapToInt(Integer::intValue).sorted().toArray();
   }

   boolean ready(int biome) {
      return this.ready.containsKey(biome);
   }

   private void surveyNext(int[] biomes) {
      int size = 64;
      int step = 64;
      int[] tile = this.surveyOrder[this.surveyed];
      Arrays.fill(biomes, -1);

      try {
         this.surveyor.survey(6, tile[0], tile[1], biomes);
      } catch (RuntimeException e) {
         Farlands.LOG.debug("Distant feature survey failed", e);
      }

      Map<Integer, Integer> edge = new HashMap<>();
      Map<Integer, Integer> inner = new HashMap<>();

      for (int cz = 0; cz < 64; cz++) {
         for (int cx = 0; cx < 64; cx++) {
            int found = biomes[cz * 64 + cx];
            if (found >= 0 && !this.sites.containsKey(found)) {
               edge.putIfAbsent(found, cz * 64 + cx);
               if (!inner.containsKey(found) && cx > 0 && cz > 0 && cx < 63 && cz < 63 && surrounded(biomes, 64, cx, cz, found)) {
                  inner.put(found, cz * 64 + cx);
               }
            }
         }
      }

      for (Entry<Integer, Integer> entry : edge.entrySet()) {
         int cell = inner.getOrDefault(entry.getKey(), entry.getValue());
         long x = tile[0] * 64L * 64L + cell % 64 * 64L + 32L;
         long z = tile[1] * 64L * 64L + cell / 64 * 64L + 32L;
         this.sites.put(entry.getKey(), x << 32 | z & 4294967295L);
      }

      this.surveyed++;
   }

   private static boolean surrounded(int[] biomes, int size, int cx, int cz, int biome) {
      for (int dz = -1; dz <= 1; dz++) {
         for (int dx = -1; dx <= 1; dx++) {
            if (biomes[(cz + dz) * size + cx + dx] != biome) {
               return false;
            }
         }
      }

      return true;
   }

   FeatureCatalog.Kind[] kinds(final int biome, int siteX, int siteZ) {
      FeatureCatalog.Kind[] known = this.ready.get(biome);
      if (known != null) {
         return known;
      }

      synchronized (this.locks.computeIfAbsent(biome, b -> new Object())) {
         FeatureCatalog.Kind[] again = this.ready.get(biome);
         if (again != null) {
            return again;
         }

         final FeatureCatalog.BiomeStats stats = new FeatureCatalog.BiomeStats();
         final Set<PlacedFeature> seenFeatures = new HashSet<>();

         try {
            Long site = this.site(biome);
            int probeX = siteX;
            int probeZ = siteZ;
            if (site != null) {
               probeX = (int)(site >> 32);
               probeZ = (int)site.longValue();
            } else if (this.unsited.add(biome)) {
               Farlands.LOG.info("Distant feature catalog: biome {} not found near the world origin, sampled where it was first seen", biome);
            }

            FeatureCatalog.Sink sink = new FeatureCatalog.Sink() {
               @Override
               public void columns(int seen, int dry, int wet) {
                  if (seen == biome) {
                     stats.columns += dry + wet;
                     stats.dry += dry;
                     stats.wet += wet;
                  }
               }

               @Override
               public void instance(PlacedFeature feature, int kind, int seen, int anchor, Block soil, boolean submerged, FeatureTemplate template) {
                  seenFeatures.add(feature);
                  if (seen == biome) {
                     FeatureCatalog.KindStats k = stats.kinds.computeIfAbsent(kind, i -> new FeatureCatalog.KindStats(feature));
                     if (k.count < 4096) {
                        k.count++;
                        k.anchors[anchor]++;
                        if (submerged) {
                           k.submerged++;
                        }

                        if (anchor == 0) {
                           k.soils.add(soil);
                        }

                        if (k.templates.size() < 8) {
                           k.templates.add(template);
                        } else {
                           int slot = Math.floorMod(Integer.rotateLeft(k.count * -1640531527, 13), k.count);
                           if (slot < 8) {
                              k.templates.set(slot, template);
                           }
                        }
                     }
                  }
               }
            };
            this.prober.probe(probeX, probeZ, sink);

            for (int extra = 0; extra < 4 && stats.columns < 2048; extra++) {
               this.prober.probe(probeX + (extra < 2 ? (extra == 0 ? 64 : -64) : 0), probeZ + (extra >= 2 ? (extra == 2 ? 64 : -64) : 0), sink);
            }
         } catch (RuntimeException e) {
            Farlands.LOG.debug("Distant feature probe failed", e);
         }

         this.observed.put(biome, Set.copyOf(seenFeatures));
         FeatureCatalog.Kind[] built = this.finish(biome, stats);
         this.ready.put(biome, built);
         return built;
      }
   }

   boolean realOnly(int biome, PlacedFeature feature, int siteX, int siteZ) {
      this.kinds(biome, siteX, siteZ);
      Set<PlacedFeature> features = this.stamped.get(biome);
      return features == null || !features.contains(feature);
   }

   boolean observedRare(int biome, PlacedFeature feature, int siteX, int siteZ, Set<PlacedFeature> near) {
      if (!this.realOnly(biome, feature, siteX, siteZ)) {
         return false;
      }

      if (near.contains(feature)) {
         return true;
      }

      Set<PlacedFeature> features = this.observed.get(biome);
      return features != null && features.contains(feature);
   }

   Set<PlacedFeature> observedIn(IntSet biomes) {
      Set<PlacedFeature> union = new HashSet<>();
      IntIterator it = biomes.iterator();

      while (it.hasNext()) {
         Set<PlacedFeature> features = this.observed.get(it.nextInt());
         if (features != null) {
            union.addAll(features);
         }
      }

      return union;
   }

   private FeatureCatalog.Kind[] finish(int biome, FeatureCatalog.BiomeStats biomeStats) {
      if (biomeStats.columns == 0) {
         this.stamped.put(biome, Set.of());
         return NONE;
      }

      List<FeatureCatalog.Kind> kinds = new ArrayList<>();
      Set<PlacedFeature> stampedHere = new HashSet<>();

      for (Entry<Integer, FeatureCatalog.KindStats> entry : biomeStats.kinds.entrySet()) {
         FeatureCatalog.KindStats k = entry.getValue();
         if (!k.templates.isEmpty()) {
            int anchor = 0;

            for (int a = 1; a < 3; a++) {
               if (k.anchors[a] > k.anchors[anchor]) {
                  anchor = a;
               }
            }

            int water = k.submerged * 5 >= k.count * 4 ? 1 : (k.submerged * 5 <= k.count ? 0 : 2);
            int eligible = anchor != 1 && (anchor != 0 || water != 1) ? (anchor == 0 && water == 0 ? biomeStats.dry : biomeStats.columns) : biomeStats.wet;
            float perChunk = k.count * 256.0F / Math.max(64, eligible);
            if (!(perChunk < 0.2F)) {
               FeatureTemplate[] templates = k.templates.toArray(new FeatureTemplate[0]);
               int painters = 0;

               for (FeatureTemplate template : templates) {
                  if (template.paints) {
                     painters++;
                  }
               }

               if (anchor != 0 || painters * 2 <= templates.length) {
                  int[] heights = new int[templates.length];
                  int reach = 0;

                  for (int i = 0; i < templates.length; i++) {
                     heights[i] = templates[i].height;
                     reach = Math.max(reach, templates[i].reach);
                  }

                  Arrays.sort(heights);
                  if (heights[heights.length / 2] <= 64 && reach <= 16) {
                     kinds.add(new FeatureCatalog.Kind(entry.getKey(), k.feature, anchor, Set.copyOf(k.soils), water, templates, perChunk));
                     if (anchor != 2) {
                        stampedHere.add(k.feature);
                     }
                  }
               }
            }
         }
      }

      this.stamped.put(biome, Set.copyOf(stampedHere));
      kinds.sort((a, b) -> a.reach != b.reach ? Integer.compare(b.reach, a.reach) : Integer.compare(a.id, b.id));
      return kinds.toArray(NONE);
   }

   private static final class BiomeStats {
      int columns;
      int dry;
      int wet;
      final Map<Integer, FeatureCatalog.KindStats> kinds = new HashMap<>();
   }

   static final class Kind {
      final int id;
      final PlacedFeature feature;
      final int anchor;
      final Set<Block> soils;
      final int water;
      final FeatureTemplate[] templates;
      final int spacing;
      final float presence;
      final int reach;

      Kind(int id, PlacedFeature feature, int anchor, Set<Block> soils, int water, FeatureTemplate[] templates, float perChunk) {
         this.id = id;
         this.feature = feature;
         this.anchor = anchor;
         this.soils = soils;
         this.water = water;
         this.templates = templates;
         int reach = 0;

         for (FeatureTemplate template : templates) {
            reach = Math.max(reach, template.reach);
         }

         this.reach = reach;
         this.spacing = Math.max(1, Math.min(64, (int)Math.floor(16.0F / (float)Math.sqrt(perChunk))));
         this.presence = Math.min(1.0F, perChunk * this.spacing * this.spacing / 256.0F);
      }
   }

   private static final class KindStats {
      final PlacedFeature feature;
      int count;
      final int[] anchors = new int[3];
      int submerged;
      final Set<Block> soils = new HashSet<>();
      final List<FeatureTemplate> templates = new ArrayList<>();

      KindStats(PlacedFeature feature) {
         this.feature = feature;
      }
   }

   interface Prober {
      void probe(int var1, int var2, FeatureCatalog.Sink var3);
   }

   interface Sink {
      void columns(int var1, int var2, int var3);

      void instance(PlacedFeature var1, int var2, int var3, int var4, Block var5, boolean var6, FeatureTemplate var7);
   }

   interface Surveyor {
      void survey(int var1, int var2, int var3, int[] var4);
   }
}
