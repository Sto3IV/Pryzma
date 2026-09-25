package net.pryzma.color;

import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.pryzma.PryzmaConfig;
import net.pryzma.mixin.BiomeAccessor;

/**
 * Biome facts the colour engines need, bound to the current client level.
 *
 * <p>Grid colormaps address biomes by OptiFine's numeric id: the vanilla biomes in the fixed
 * order below, then every other registered biome in registry order. The order is part of the
 * pack format and must not change.
 */
public final class PrBiomes {
    private static final String[] OPTIFINE_ORDER = {
            "the_void", "plains", "sunflower_plains", "snowy_plains", "ice_spikes", "desert", "swamp", "mangrove_swamp",
            "forest", "flower_forest", "birch_forest", "dark_forest", "old_growth_birch_forest", "old_growth_pine_taiga",
            "old_growth_spruce_taiga", "taiga", "snowy_taiga", "savanna", "savanna_plateau", "windswept_hills",
            "windswept_gravelly_hills", "windswept_forest", "windswept_savanna", "jungle", "sparse_jungle",
            "bamboo_jungle", "badlands", "eroded_badlands", "wooded_badlands", "meadow", "cherry_grove", "grove",
            "snowy_slopes", "frozen_peaks", "jagged_peaks", "stony_peaks", "river", "frozen_river", "beach",
            "snowy_beach", "stony_shore", "warm_ocean", "lukewarm_ocean", "deep_lukewarm_ocean", "ocean", "deep_ocean",
            "cold_ocean", "deep_cold_ocean", "frozen_ocean", "deep_frozen_ocean", "mushroom_fields", "dripstone_caves",
            "lush_caves", "deep_dark", "nether_wastes", "warped_forest", "crimson_forest", "soul_sand_valley",
            "basalt_deltas", "the_end", "end_highlands", "end_midlands", "small_end_islands", "end_barrens"};

    private static volatile Snapshot snapshot = Snapshot.EMPTY;

    private PrBiomes() {
    }

    private record Snapshot(Level level, Map<Biome, Integer> gridIds, Biome plains, Biome swamp, Biome mangroveSwamp) {
        static final Snapshot EMPTY = new Snapshot(null, Map.of(), null, null, null);
    }

    private static Snapshot current() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc == null ? null : mc.level;
        Snapshot s = snapshot;
        if (s.level == level) {
            return s;
        }
        synchronized (PrBiomes.class) {
            s = snapshot;
            if (s.level != level) {
                s = level == null ? Snapshot.EMPTY : build(level);
                snapshot = s;
            }
            return s;
        }
    }

    private static Snapshot build(Level level) {
        Registry<Biome> registry = level.registryAccess().registryOrThrow(Registries.BIOME);
        Map<Biome, Integer> ids = new IdentityHashMap<>();
        int next = 0;
        for (String name : OPTIFINE_ORDER) {
            Biome biome = registry.get(ResourceLocation.withDefaultNamespace(name));
            if (biome != null) {
                ids.putIfAbsent(biome, next);
            }
            next++;
        }
        for (Biome biome : registry) {
            if (!ids.containsKey(biome)) {
                ids.put(biome, next++);
            }
        }
        return new Snapshot(level, ids, registry.get(Biomes.PLAINS), registry.get(Biomes.SWAMP), registry.get(Biomes.MANGROVE_SWAMP));
    }

    /** OptiFine grid column of {@code biome}; unknown biomes fall back to plains (1). */
    public static int gridId(Biome biome) {
        Integer id = current().gridIds.get(biome);
        return id == null ? 1 : id;
    }

    public static boolean isSwamp(Biome biome) {
        Snapshot s = current();
        return biome != null && (biome == s.swamp || biome == s.mangroveSwamp);
    }

    /** OptiFine {@code fixBiome}: swamps borrow plains colours while "Swamp Colors" is off. */
    public static Biome colorBiome(Biome biome) {
        if (!PryzmaConfig.prSwampColors && isSwamp(biome)) {
            Biome plains = current().plains;
            return plains != null ? plains : biome;
        }
        return biome;
    }

    public static Biome plains() {
        return current().plains;
    }

    public static Biome swamp() {
        return current().swamp;
    }

    /** Biome at {@code pos}, read through the getter when it is a level, else the client level. */
    public static Biome biomeAt(BlockAndTintGetter getter, BlockPos pos) {
        Holder<Biome> holder = biomeHolderAt(getter, pos);
        return holder == null ? plains() : holder.value();
    }

    /** Registry holder of the biome at {@code pos}, or {@code null} without a level. */
    public static Holder<Biome> biomeHolderAt(BlockAndTintGetter getter, BlockPos pos) {
        if (getter instanceof LevelReader reader) {
            return reader.getBiome(pos);
        }
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? null : level.getBiome(pos);
    }

    public static float temperature(Biome biome) {
        return biome.getBaseTemperature();
    }

    public static float downfall(Biome biome) {
        return ((BiomeAccessor) (Object) biome).prGetClimateSettings().downfall();
    }
}
