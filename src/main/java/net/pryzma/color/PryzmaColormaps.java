package net.pryzma.color;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.pryzma.Pryzma;
import net.pryzma.PryzmaConfig;
import net.pryzma.core.match.PrBlockMatcher;
import net.pryzma.core.res.PrPaths;
import net.pryzma.core.res.PrProperties;
import net.pryzma.core.res.PrResources;

/**
 * OptiFine / MCPatcher custom colours: {@code color.properties} and {@code colormap/}.
 *
 * <p>All state lives in one immutable {@link Data} snapshot built on the reload worker and swapped
 * in on the render thread, so chunk-build threads never observe a half-loaded palette. Block
 * colormaps are cached per client level with vanilla's own {@link BlockTintCache}, invalidated
 * exactly when vanilla invalidates the biome tint caches.
 */
public final class PryzmaColormaps {
    private static final String COLORMAP = "optifine/colormap/";

    private static volatile Data data = Data.EMPTY;
    private static final Map<PrColormap, BlockTintCache> CACHES = new ConcurrentHashMap<>();
    private static volatile Level cacheLevel;

    private static final PrColorFader SKY_FADER = new PrColorFader();
    private static final PrColorFader FOG_FADER = new PrColorFader();
    private static final PrColorFader UNDERWATER_FADER = new PrColorFader();
    private static final PrColorFader UNDERLAVA_FADER = new PrColorFader();

    private PryzmaColormaps() {
    }

    /** Everything parsed from one resource reload. */
    public record Data(
            PrColormap water, PrColormap pine, PrColormap birch, PrColormap swampGrass, PrColormap swampFoliage,
            PrColormap sky, PrColormap fog, PrColormap underwater, PrColormap underlava,
            PrColormap redstone, PrColormap stem, PrColormap stemPumpkin, PrColormap stemMelon,
            PrColormap xpOrb, PrColormap durability, PrColormap lavaDrop, PrColormap myceliumParticle,
            Map<Block, PrColormap[]> blockColormaps,
            int particleWater, int particlePortal, int lilyPad, int textXpBar, int textBoss, int textSign,
            Vec3 fogNether, Vec3 fogEnd, Vec3 skyEnd, int xpOrbTime) {
        static final Data EMPTY = new Data(null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, Map.of(), -1, -1, -1, -1, -1, -1, null, null, null, -1);

        boolean isEmpty() {
            return this == EMPTY;
        }
    }

    // ------------------------------------------------------------------ loading

    /** Parses every colour resource. Runs on a reload worker thread. */
    public static Data prepare(PrResources res) {
        List<String> warnings = new ArrayList<>();
        Consumer<String> warn = warnings::add;
        ResourceLocation colorPropsLoc = mc("optifine/color.properties");
        Optional<PrProperties> colorProps = res.properties(colorPropsLoc);
        PrColormap.Format defaultFormat = PrColormap.Format.VANILLA;
        if (colorProps.isPresent()) {
            PrColormap.Format f = PrColormap.Format.parse(colorProps.get().get("palette.format"), PrColormap.Format.VANILLA);
            defaultFormat = f == null || f == PrColormap.Format.FIXED ? PrColormap.Format.VANILLA : f;
        }
        PrColormap.Format fmt = defaultFormat;

        PrColormap water = first(res, fmt, warn, "water.png", "watercolorx.png");
        PrColormap pine = first(res, fmt, warn, "pine.png", "pinecolor.png");
        PrColormap birch = first(res, fmt, warn, "birch.png", "birchcolor.png");
        PrColormap swampGrass = first(res, fmt, warn, "swampgrass.png", "swampgrasscolor.png");
        PrColormap swampFoliage = first(res, fmt, warn, "swampfoliage.png", "swampfoliagecolor.png");
        PrColormap sky = first(res, fmt, warn, "sky0.png", "skycolor0.png");
        PrColormap fog = first(res, fmt, warn, "fog0.png", "fogcolor0.png");
        PrColormap underwater = first(res, fmt, warn, "underwater.png", "underwatercolor.png");
        PrColormap underlava = first(res, fmt, warn, "underlava.png", "underlavacolor.png");
        PrColormap redstone = first(res, fmt, warn, "redstone.png", "redstonecolor.png");
        PrColormap stem = first(res, fmt, warn, "stem.png", "stemcolor.png");
        PrColormap stemPumpkin = first(res, fmt, warn, "pumpkinstem.png");
        PrColormap stemMelon = first(res, fmt, warn, "melonstem.png");
        PrColormap xpOrb = first(res, fmt, warn, "xporb.png");
        PrColormap durability = first(res, fmt, warn, "durability.png");
        PrColormap lavaDrop = first(res, fmt, warn, "lavadrop.png");
        PrColormap mycelium = first(res, fmt, warn, "myceliumparticle.png", "myceliumparticlecolor.png");

        int particleWater = -1;
        int particlePortal = -1;
        int lilyPad = -1;
        int textXpBar = -1;
        int textBoss = -1;
        int textSign = -1;
        int xpOrbTime = -1;
        Vec3 fogNether = null;
        Vec3 fogEnd = null;
        Vec3 skyEnd = null;
        List<PrColormap> palettes = new ArrayList<>();
        if (colorProps.isPresent()) {
            PrProperties p = colorProps.get();
            particleWater = p.getColor("particle.water", p.getColor("drop.water", -1));
            particlePortal = p.getColor("particle.portal", -1);
            lilyPad = p.getColor("lilypad", -1);
            textXpBar = p.getColor("text.xpbar", -1);
            textBoss = p.getColor("text.boss", -1);
            textSign = p.getColor("text.sign", -1);
            xpOrbTime = p.getInt("xporb.time", -1);
            fogNether = vec(p.getColor("fog.nether", -1));
            fogEnd = vec(p.getColor("fog.end", -1));
            skyEnd = vec(p.getColor("sky.end", -1));
            for (Map.Entry<String, String> e : p.withPrefix("palette.block.").entrySet()) {
                ResourceLocation image = PrPaths.resolveLocation(e.getKey(), p.location());
                if (image == null) {
                    warn.accept("Invalid palette path: " + e.getKey());
                    continue;
                }
                Optional<PrColormap> base = PrColormap.load(res, image, fmt, warn);
                if (base.isEmpty()) {
                    warn.accept("Colormap not found: " + image);
                    continue;
                }
                PrBlockMatcher blocks = PrBlockMatcher.parse(e.getValue(), null, warn);
                if (blocks.isEmpty()) {
                    warn.accept("Invalid match blocks for palette " + e.getKey() + ": " + e.getValue());
                    continue;
                }
                palettes.add(base.get().withBlocks(blocks));
            }
        }

        List<PrColormap> blockMaps = new ArrayList<>();
        for (String dir : new String[] {"colormap/custom", "colormap/blocks"}) {
            for (Map.Entry<ResourceLocation, Resource> e : res.list(dir, ".properties").entrySet()) {
                res.properties(e.getKey(), e.getValue())
                        .flatMap(p -> PrColormap.fromProperties(res, p, fmt, true, warn))
                        .ifPresent(blockMaps::add);
            }
        }
        blockMaps.addAll(palettes);
        Map<Block, List<PrColormap>> byBlock = new IdentityHashMap<>();
        for (PrColormap cm : blockMaps) {
            for (Block block : cm.blocks().blocks()) {
                byBlock.computeIfAbsent(block, b -> new ArrayList<>()).add(cm);
            }
        }
        Map<Block, PrColormap[]> blockColormaps = new IdentityHashMap<>();
        byBlock.forEach((b, list) -> blockColormaps.put(b, list.toArray(PrColormap[]::new)));

        warnings.forEach(w -> Pryzma.LOGGER.warn("CustomColors: {}", w));
        Data d = new Data(water, pine, birch, swampGrass, swampFoliage, sky, fog, underwater, underlava, redstone, stem,
                stemPumpkin, stemMelon, xpOrb, durability, lavaDrop, mycelium, blockColormaps, particleWater,
                particlePortal, lilyPad, textXpBar, textBoss, textSign, fogNether, fogEnd, skyEnd, xpOrbTime);
        Pryzma.LOGGER.info("CustomColors: {} block colormaps, water={}, sky={}, fog={}, underwater={}, redstone={}",
                blockMaps.size(), water != null, sky != null, fog != null, underwater != null, redstone != null);
        return d;
    }

    private static PrColormap first(PrResources res, PrColormap.Format fmt, Consumer<String> warn, String... names) {
        for (String name : names) {
            Optional<PrColormap> cm = PrColormap.load(res, mc(COLORMAP + name), fmt, warn);
            if (cm.isPresent()) {
                return cm.get();
            }
        }
        return null;
    }

    /** Swaps in freshly parsed data. Render thread. */
    public static void apply(Data d) {
        data = d;
        clearCaches();
        SKY_FADER.reset();
        FOG_FADER.reset();
        UNDERWATER_FADER.reset();
        UNDERLAVA_FADER.reset();
    }

    public static Data data() {
        return data;
    }

    private static boolean active() {
        return PryzmaConfig.prCustomColors && !data.isEmpty();
    }

    // ------------------------------------------------------------------ block colours

    /**
     * Custom colour for a block quad, or -1 to keep vanilla's. Covers block palettes, pine and
     * birch leaves, lily pads and stems. Called from chunk-build threads.
     */
    public static int blockColor(BlockState state, BlockAndTintGetter level, BlockPos pos) {
        if (!active() || level == null || pos == null) {
            return -1;
        }
        Data d = data;
        Block block = state.getBlock();
        PrColormap[] candidates = d.blockColormaps.get(block);
        if (candidates != null) {
            BlockState colorState = state;
            BlockPos colorPos = pos;
            if (block instanceof DoublePlantBlock && state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER) {
                colorPos = pos.below();
                colorState = level.getBlockState(colorPos);
            }
            for (PrColormap cm : candidates) {
                if (cm.blocks().matches(colorState) || cm.blocks().matches(state)) {
                    return cached(cm, colorPos);
                }
            }
        }
        if (block == Blocks.LILY_PAD) {
            return d.lilyPad;
        }
        if (block == Blocks.SPRUCE_LEAVES && d.pine != null) {
            return cached(d.pine, pos);
        }
        if (block == Blocks.BIRCH_LEAVES && d.birch != null) {
            return cached(d.birch, pos);
        }
        if (block instanceof StemBlock) {
            PrColormap stem = block == Blocks.PUMPKIN_STEM && d.stemPumpkin != null ? d.stemPumpkin
                    : block == Blocks.MELON_STEM && d.stemMelon != null ? d.stemMelon : d.stem;
            if (stem != null) {
                return stem.byIndex(state.getValue(StemBlock.AGE));
            }
        }
        return -1;
    }

    /** Custom water tint, or -1. */
    public static int waterColor(BlockAndTintGetter level, BlockPos pos) {
        if (!active() || data.water == null || level == null || pos == null) {
            return -1;
        }
        return cached(data.water, pos);
    }

    /**
     * Grass colour for {@code Biome.getGrassColor}, or -1 for vanilla: the swamp grass colormap in
     * swamps, plains grass while "Swamp Colors" is off.
     */
    public static int grass(Biome biome, double x, double z) {
        if (!PrBiomes.isSwamp(biome)) {
            return -1;
        }
        if (!PryzmaConfig.prSwampColors) {
            Biome plains = PrBiomes.plains();
            return plains != null ? plains.getGrassColor(x, z) : -1;
        }
        PrColormap cm = data.swampGrass;
        return cm != null && active() && biome == PrBiomes.swamp() ? cm.colorAt(biome, (int) x, 64, (int) z) : -1;
    }

    /** Foliage colour for {@code Biome.getFoliageColor}, or -1 for vanilla; see {@link #grass}. */
    public static int foliage(Biome biome) {
        if (!PrBiomes.isSwamp(biome)) {
            return -1;
        }
        if (!PryzmaConfig.prSwampColors) {
            Biome plains = PrBiomes.plains();
            return plains != null ? plains.getFoliageColor() : -1;
        }
        PrColormap cm = data.swampFoliage;
        return cm != null && active() && biome == PrBiomes.swamp() ? cm.colorAt(biome, 0, 64, 0) : -1;
    }

    /** Water colour for {@code Biome.getWaterColor}: plains water in swamps while "Swamp Colors" is off. */
    public static int biomeWater(Biome biome) {
        if (PryzmaConfig.prSwampColors || !PrBiomes.isSwamp(biome)) {
            return -1;
        }
        Biome plains = PrBiomes.plains();
        return plains != null ? plains.getWaterColor() : -1;
    }

    /** Redstone wire colour by power level; -1 keeps vanilla. */
    public static int redstone(int power) {
        PrColormap cm = data.redstone;
        return cm != null && active() ? cm.byIndex(power) : -1;
    }

    private static int cached(PrColormap cm, BlockPos pos) {
        if (cm.isConstant()) {
            return cm.colorAt(null, 0, 0, 0);
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return -1;
        }
        if (cacheLevel != level) {
            synchronized (CACHES) {
                if (cacheLevel != level) {
                    CACHES.clear();
                    cacheLevel = level;
                }
            }
        }
        BlockTintCache cache = CACHES.computeIfAbsent(cm, c -> new BlockTintCache(
                p -> c.smooth(level, p, Minecraft.getInstance().options.biomeBlendRadius().get())));
        return cache.getColor(pos);
    }

    /** Mirrors {@code ClientLevel.clearTintCaches}. */
    public static void clearCaches() {
        CACHES.values().forEach(BlockTintCache::invalidateAll);
    }

    /** Mirrors {@code ClientLevel.onChunkLoaded}. */
    public static void invalidateChunk(int chunkX, int chunkZ) {
        CACHES.values().forEach(c -> c.invalidateForChunk(chunkX, chunkZ));
    }

    // ------------------------------------------------------------------ sky and fog

    /**
     * Overworld sky colour: the colormap replaces the biome sky tint while vanilla's time of day and
     * weather factor ({@code vanilla / (0.5, 0.66275, 1)}) is kept, as in OptiFine.
     */
    public static Vec3 skyColor(Vec3 vanilla, ClientLevel level, double x, double y, double z) {
        if (!active()) {
            return vanilla;
        }
        if (isNether(level)) {
            return vanilla;
        }
        if (isEnd(level)) {
            return data.skyEnd != null ? data.skyEnd : vanilla;
        }
        PrColormap cm = data.sky;
        if (cm == null) {
            return vanilla;
        }
        int c = cm.smooth(level, BlockPos.containing(x, y, z), 3);
        return SKY_FADER.approach(
                (c >> 16 & 0xFF) / 255.0 * (vanilla.x / 0.5),
                (c >> 8 & 0xFF) / 255.0 * (vanilla.y / 0.66275),
                (c & 0xFF) / 255.0 * vanilla.z);
    }

    /** Horizon fog colour; see {@link #skyColor} for the multiplier. */
    public static Vec3 fogColor(Vec3 vanilla, ClientLevel level, double x, double y, double z) {
        if (!active()) {
            return vanilla;
        }
        if (isNether(level)) {
            return data.fogNether != null ? data.fogNether : vanilla;
        }
        if (isEnd(level)) {
            return data.fogEnd != null ? data.fogEnd : vanilla;
        }
        PrColormap cm = data.fog;
        if (cm == null) {
            return vanilla;
        }
        int c = cm.smooth(level, BlockPos.containing(x, y, z), 3);
        return FOG_FADER.approach(
                (c >> 16 & 0xFF) / 255.0 * (vanilla.x / 0.753),
                (c >> 8 & 0xFF) / 255.0 * (vanilla.y / 0.8471),
                (c & 0xFF) / 255.0 * vanilla.z);
    }

    /** Underwater fog colour, or {@code null} to keep vanilla. */
    public static Vec3 underwaterColor(ClientLevel level, double x, double y, double z) {
        return underFluid(data.underwater, UNDERWATER_FADER, level, x, y, z);
    }

    /** Under-lava fog colour, or {@code null} to keep vanilla. */
    public static Vec3 underlavaColor(ClientLevel level, double x, double y, double z) {
        return underFluid(data.underlava, UNDERLAVA_FADER, level, x, y, z);
    }

    private static Vec3 underFluid(PrColormap cm, PrColorFader fader, ClientLevel level, double x, double y, double z) {
        if (cm == null || !active()) {
            return null;
        }
        int c = cm.smooth(level, BlockPos.containing(x, y, z), 3);
        return fader.approach((c >> 16 & 0xFF) / 255.0, (c >> 8 & 0xFF) / 255.0, (c & 0xFF) / 255.0);
    }

    /** End sky vertex colour ({@code 0xFF282828} in vanilla), or {@code vanilla} unchanged. */
    public static int endSkyColor(int vanilla) {
        Vec3 c = active() ? data.skyEnd : null;
        if (c == null) {
            return vanilla;
        }
        return 0xFF000000 | (int) (c.x * 255) << 16 | (int) (c.y * 255) << 8 | (int) (c.z * 255);
    }

    private static boolean isNether(Level level) {
        return level.dimension() == Level.NETHER;
    }

    private static boolean isEnd(Level level) {
        return level.dimension() == Level.END;
    }

    private static Vec3 vec(int rgb) {
        return rgb < 0 ? null : new Vec3((rgb >> 16 & 0xFF) / 255.0, (rgb >> 8 & 0xFF) / 255.0, (rgb & 0xFF) / 255.0);
    }

    private static ResourceLocation mc(String path) {
        return ResourceLocation.withDefaultNamespace(path);
    }
}
