package net.pryzma.lod;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicIntegerArray;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.pryzma.Pryzma;

/**
 * The Farlands LOD palette: per block state the untinted colour of its top face, the tint index its biome tint is
 * asked with, and its {@link LodMaterial}.
 *
 * <p>Colours and tint indices come from the state's baked model and the textures of its top quads, as the active
 * resource packs define them: a pack's greyscale leaves are tinted by the biome, its pre-coloured leaves (a model
 * without tint indices) are not, its colour maps reach the tint through {@code BlockColors}. Models and textures are
 * read on the render thread only, which also owns resource reloads:
 * <ul>
 * <li>Workers read the colour table without locks. A state not computed yet answers with its map colour and is
 * queued; {@link #tick} computes queued states within a time budget and bumps {@link #epoch()}, and meshes built
 * with a stand-in colour are rebuilt.</li>
 * <li>A worker that needs a tint index the table does not hold yet asks the render thread for it and waits; a wrong
 * index would be baked into stored tints.</li>
 * <li>A resource reload clears both tables and bumps {@link #generation()}: every mesh is rebuilt and every stored
 * tint is computed again ({@link PryzmaLod}).</li>
 * </ul>
 * Materials depend on tags and block classes only and are classified on any thread.
 */
public final class LodBlockColors {
    private static final int UNKNOWN = 0;
    private static final int PENDING = 1;
    /** Colour of a state that has neither texture nor map colour. */
    private static final int FALLBACK_RGB = 0x808080;
    /** Texture samples per axis; larger textures are read with a stride. */
    private static final int SAMPLES = 32;
    private static final long MODEL_SEED = 42L;
    private static final long TICK_BUDGET_NANOS = 2_000_000L;
    /** Tint index table entries: tint index + 2, so 0 stays "unknown" and 1 means "not tinted". */
    private static final int TINT_OFFSET = 2;
    /** Material table entries: material + 1, so 0 stays "unknown". */
    private static final int MATERIAL_OFFSET = 1;
    /** Longest a worker waits for the render thread to compute a tint index. */
    private static final long RENDER_THREAD_WAIT_SECONDS = 10L;
    /**
     * Foliage textures have holes through which a canopy shows its shaded inside; the visible colour of a crown is
     * darker than its opaque pixels, by this much where a texture is all holes, proportionally less with coverage.
     */
    private static final float HOLE_SHADE = 0.45F;

    /** Per state id: {@link #UNKNOWN}, {@link #PENDING} or {@code 0xFF000000 | rgb}. */
    private static volatile AtomicIntegerArray colors = new AtomicIntegerArray(0);
    private static volatile AtomicIntegerArray tintIndices = new AtomicIntegerArray(0);
    private static volatile AtomicIntegerArray materials = new AtomicIntegerArray(0);
    private static final ConcurrentLinkedQueue<Integer> QUEUE = new ConcurrentLinkedQueue<>();
    private static volatile int epoch;
    private static volatile int generation;
    private static volatile boolean reloadPending;
    private static boolean computedSinceTick;

    private LodBlockColors() {
    }

    /** Bumped whenever new base colours become available. */
    public static int epoch() {
        return epoch;
    }

    /** Bumped whenever all base colours are recomputed (resource reload). */
    public static int generation() {
        return generation;
    }

    /** A colour source for one mesh job; it remembers whether any colour was a stand-in. */
    public static Source source() {
        return new Source(colorTable());
    }

    /** {@link LodColorSource} over the table of one generation. Not thread-safe: one per job. */
    public static final class Source implements LodColorSource {
        private final AtomicIntegerArray table;
        private boolean usedFallback;

        private Source(AtomicIntegerArray table) {
            this.table = table;
        }

        @Override
        public int baseColor(int stateId) {
            if (stateId >= 0 && stateId < table.length()) {
                int value = table.get(stateId);
                if (value != UNKNOWN && value != PENDING) {
                    return value & 0xFFFFFF;
                }
                if (value == UNKNOWN && table.compareAndSet(stateId, UNKNOWN, PENDING)) {
                    QUEUE.add(stateId);
                }
            }
            usedFallback = true;
            return mapColor(stateId);
        }

        @Override
        public int material(int stateId) {
            return LodBlockColors.material(stateId);
        }

        /** Whether any colour this source gave was a map-colour stand-in. */
        public boolean usedFallback() {
            return usedFallback;
        }
    }

    /**
     * Computes the base colour and tint index of {@code state} now unless known. Render thread; null is ignored, and
     * on any other thread the state is only queued for {@link #tick}.
     */
    public static void ensure(BlockState state) {
        if (state == null) {
            return;
        }
        AtomicIntegerArray table = colorTable();
        int id = Block.getId(state);
        if (id < 0 || id >= table.length()) {
            return;
        }
        if (!RenderSystem.isOnRenderThread()) {
            if (table.compareAndSet(id, UNKNOWN, PENDING)) {
                QUEUE.add(id);
            }
            return;
        }
        int value = table.get(id);
        if (value == UNKNOWN || value == PENDING) {
            table.set(id, 0xFF000000 | compute(state));
            computedSinceTick = true;
        }
        tintIndex(state);
    }

    /**
     * Tint index of {@code state}'s top face for {@code BlockColors}: the first tinted top quad's index, -1 when its
     * top quads are untinted, 0 for states drawn without model quads (fluids). Any thread: an index the table does
     * not hold is computed on the render thread, a worker waits for it.
     */
    public static int tintIndex(BlockState state) {
        AtomicIntegerArray table = tintTable();
        int id = Block.getId(state);
        boolean cached = id >= 0 && id < table.length();
        if (cached) {
            int value = table.get(id);
            if (value != UNKNOWN) {
                return value - TINT_OFFSET;
            }
        }
        int index;
        if (RenderSystem.isOnRenderThread()) {
            index = topTintIndex(state);
        } else {
            Integer computed = onRenderThread(state);
            if (computed == null) {
                // The render thread is gone (shutdown) or stalled: answer without remembering the guess.
                return 0;
            }
            index = computed;
        }
        if (cached && table == tintIndices) {
            table.set(id, index + TINT_OFFSET);
        }
        return index;
    }

    /** {@link LodMaterial} of state {@code stateId}. Any thread. */
    public static int material(int stateId) {
        AtomicIntegerArray table = materialTable();
        if (stateId < 0 || stateId >= table.length()) {
            return LodMaterial.UNKNOWN;
        }
        int value = table.get(stateId);
        if (value != UNKNOWN) {
            return value - MATERIAL_OFFSET;
        }
        int material;
        try {
            material = classify(Block.stateById(stateId));
        } catch (RuntimeException e) {
            material = LodMaterial.UNKNOWN;
        }
        table.set(stateId, material + MATERIAL_OFFSET);
        return material;
    }

    /** A resource reload replaced models and textures; the palette is rebuilt once the reload has finished. */
    public static void onResourcesReloaded() {
        reloadPending = true;
    }

    /** Computes queued colours within a time budget. Render thread, once per client tick. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getOverlay() != null) {
            return;
        }
        if (reloadPending) {
            reloadPending = false;
            resetTables();
            QUEUE.clear();
            computedSinceTick = false;
            generation++;
            epoch++;
            return;
        }
        AtomicIntegerArray table = colorTable();
        long deadline = System.nanoTime() + TICK_BUDGET_NANOS;
        Integer id;
        while (System.nanoTime() < deadline && (id = QUEUE.poll()) != null) {
            if (id < table.length() && table.get(id) == PENDING) {
                BlockState state = Block.stateById(id);
                table.set(id, 0xFF000000 | compute(state));
                tintIndex(state);
                computedSinceTick = true;
            }
        }
        if (computedSinceTick) {
            computedSinceTick = false;
            epoch++;
        }
    }

    /** Map colour of state {@code stateId}, safe on any thread; grey when the state has none. */
    static int mapColor(int stateId) {
        try {
            MapColor color = Block.stateById(stateId).getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            if (color != null && color.col != 0) {
                return color.col;
            }
        } catch (RuntimeException e) {
            // A modded block whose map colour needs a real level: grey.
        }
        return FALLBACK_RGB;
    }

    /** The material Distant Horizons would report for {@code state}: tags first, then a few vanilla families. */
    @SuppressWarnings("deprecation") // blocksMotion and getLightEmission are what the heightmap and light engine use
    static int classify(BlockState state) {
        if (state.isAir()) {
            return LodMaterial.AIR;
        }
        FluidState fluid = state.getFluidState();
        if (!fluid.isEmpty() && !state.blocksMotion()) {
            return fluid.is(FluidTags.LAVA) ? LodMaterial.LAVA : LodMaterial.WATER;
        }
        if (LodWorldScanner.isFoliage(state)) {
            return LodMaterial.LEAVES;
        }
        if (state.getLightEmission() > 0) {
            return LodMaterial.ILLUMINATED;
        }
        if (state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW) || state.is(BlockTags.ICE)) {
            return LodMaterial.SNOW;
        }
        if (state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL) || state.is(Blocks.SANDSTONE) || state.is(Blocks.RED_SANDSTONE)) {
            return LodMaterial.SAND;
        }
        if (state.is(BlockTags.TERRACOTTA)) {
            return LodMaterial.TERRACOTTA;
        }
        if (state.is(BlockTags.BASE_STONE_NETHER) || state.is(BlockTags.NYLIUM) || state.is(BlockTags.SOUL_FIRE_BASE_BLOCKS)) {
            return LodMaterial.NETHER_STONE;
        }
        if (state.is(Blocks.DEEPSLATE) || state.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES)) {
            return LodMaterial.DEEPSLATE;
        }
        if (state.is(BlockTags.LOGS) || state.is(BlockTags.PLANKS)) {
            return LodMaterial.WOOD;
        }
        if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.MYCELIUM) || state.is(Blocks.MOSS_BLOCK)) {
            return LodMaterial.GRASS;
        }
        if (state.is(BlockTags.DIRT)) {
            return LodMaterial.DIRT;
        }
        if (state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.STONE_ORE_REPLACEABLES)) {
            return LodMaterial.STONE;
        }
        if (state.is(Blocks.IRON_BLOCK) || state.is(Blocks.GOLD_BLOCK) || state.is(Blocks.COPPER_BLOCK)
                || state.is(Blocks.NETHERITE_BLOCK)) {
            return LodMaterial.METAL;
        }
        return LodMaterial.UNKNOWN;
    }

    /** {@link #topTintIndex} on the render thread, for a worker; null if it did not answer in time. */
    private static Integer onRenderThread(BlockState state) {
        CompletableFuture<Integer> future = Minecraft.getInstance().submit(() -> topTintIndex(state));
        try {
            return future.get(RENDER_THREAD_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException | TimeoutException e) {
            return null;
        }
    }

    /**
     * Texture average of the top face, else the particle texture, else the map colour. Render thread. Where some top
     * quads are tinted only those count: the tint multiplies the result, and an untinted overlay would be tinted with
     * it. Foliage is darkened by its texture's holes.
     */
    private static int compute(BlockState state) {
        try {
            BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
            List<BakedQuad> quads = topQuads(model, state);
            boolean anyTinted = false;
            for (BakedQuad quad : quads) {
                anyTinted |= quad.isTinted();
            }
            long[] sum = new long[5];
            for (BakedQuad quad : quads) {
                if (!anyTinted || quad.isTinted()) {
                    addSprite(quad.getSprite(), sum);
                }
            }
            if (sum[3] == 0) {
                addSprite(model.getParticleIcon(ModelData.EMPTY), sum);
            }
            if (sum[3] > 0) {
                int rgb = (int) (sum[0] / sum[3]) << 16 | (int) (sum[1] / sum[3]) << 8 | (int) (sum[2] / sum[3]);
                if (LodWorldScanner.isFoliage(state)) {
                    float coverage = sum[4] / (1000.0F * sum[3]);
                    rgb = LodColors.shade(rgb, 1.0F - HOLE_SHADE * (1.0F - coverage));
                }
                return rgb;
            }
        } catch (RuntimeException e) {
            Pryzma.LOGGER.debug("Farlands LOD: no texture colour for {}", state, e);
        }
        return mapColor(Block.getId(state));
    }

    /**
     * Adds the average opaque colour of the sprite's first frame to {@code sum} (r, g, b, count, coverage x 1000) as
     * one sample; coverage is the share of opaque texels.
     */
    private static void addSprite(TextureAtlasSprite sprite, long[] sum) {
        if (sprite == null) {
            return;
        }
        SpriteContents contents = sprite.contents();
        NativeImage image = contents.getOriginalImage();
        int width = Math.min(contents.width(), image.getWidth());
        int height = Math.min(contents.height(), image.getHeight());
        if (width <= 0 || height <= 0 || image.format() != NativeImage.Format.RGBA) {
            return;
        }
        int step = Math.max(1, Math.max(width, height) / SAMPLES);
        long r = 0;
        long g = 0;
        long b = 0;
        long n = 0;
        long total = 0;
        for (int y = 0; y < height; y += step) {
            for (int x = 0; x < width; x += step) {
                total++;
                int abgr = image.getPixelRGBA(x, y);
                if (abgr >>> 24 < 16) {
                    continue;
                }
                int rgb = LodColors.abgrToRgb(abgr);
                r += rgb >> 16 & 0xFF;
                g += rgb >> 8 & 0xFF;
                b += rgb & 0xFF;
                n++;
            }
        }
        if (n > 0) {
            sum[0] += r / n;
            sum[1] += g / n;
            sum[2] += b / n;
            sum[3]++;
            sum[4] += 1000L * n / total;
        }
    }

    private static int topTintIndex(BlockState state) {
        try {
            BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
            List<BakedQuad> quads = topQuads(model, state);
            if (quads.isEmpty()) {
                return 0;
            }
            for (BakedQuad quad : quads) {
                if (quad.isTinted()) {
                    return quad.getTintIndex();
                }
            }
            return -1;
        } catch (RuntimeException e) {
            return -1;
        }
    }

    /** The upward quads of a block model: the culled top face and unculled quads facing up. */
    private static List<BakedQuad> topQuads(BakedModel model, BlockState state) {
        List<BakedQuad> quads = new ArrayList<>(model.getQuads(state, Direction.UP, RandomSource.create(MODEL_SEED), ModelData.EMPTY, null));
        for (BakedQuad quad : model.getQuads(state, null, RandomSource.create(MODEL_SEED), ModelData.EMPTY, null)) {
            if (quad.getDirection() == Direction.UP) {
                quads.add(quad);
            }
        }
        return quads;
    }

    private static AtomicIntegerArray colorTable() {
        if (colors.length() == 0) {
            initTables();
        }
        return colors;
    }

    private static AtomicIntegerArray tintTable() {
        if (tintIndices.length() == 0) {
            initTables();
        }
        return tintIndices;
    }

    private static AtomicIntegerArray materialTable() {
        if (materials.length() == 0) {
            initTables();
        }
        return materials;
    }

    /** Allocates the tables on first use; the block state registry is complete once a level exists. */
    private static synchronized void initTables() {
        if (colors.length() == 0 || tintIndices.length() == 0 || materials.length() == 0) {
            resetTables();
        }
    }

    /** Fresh tables sized to the block state registry. Materials survive: they do not depend on resources. */
    private static synchronized void resetTables() {
        int states = Math.max(1, Block.BLOCK_STATE_REGISTRY.size());
        colors = new AtomicIntegerArray(states);
        tintIndices = new AtomicIntegerArray(states);
        if (materials.length() != states) {
            materials = new AtomicIntegerArray(states);
        }
    }
}
