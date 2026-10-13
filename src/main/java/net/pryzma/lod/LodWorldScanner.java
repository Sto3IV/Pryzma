package net.pryzma.lod;

import java.util.Arrays;
import java.util.function.IntUnaryOperator;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/**
 * Farlands LOD data extraction: the surface layers of a chunk as {@link LodChunkData}.
 *
 * <p>Two sources feed the same column logic ({@link LodColumnScan} through {@link #resolveColumn}):
 * <ul>
 * <li>Loaded chunks, {@link #capture} on the client thread: the {@code MOTION_BLOCKING} heightmap gives the top
 * block, the light engine the light above each layer, the chunk's biome container the biome. It reads a handful of
 * blocks per column (a few dozen under a tree), so it can run when the chunk unloads.</li>
 * <li>Saved chunks, {@link #decode} on a worker: the chunk's Anvil NBT as the integrated server's storage returns
 * it (see {@link LodStorageIntake}). Heightmap, palettes and light layers are read entry by entry from the packed
 * arrays ({@link LodPalette}).</li>
 * </ul>
 * Both produce a {@link RawChunk}; {@link Finisher#finish} turns it into {@link LodChunkData} on a worker, asking
 * {@code BlockColors} for each layer's biome tint through a getter that answers with the column's biome, and
 * {@link Finisher#retint} recomputes the tints of stored data after a resource reload.
 */
public final class LodWorldScanner {
    /** Deepest fluid column searched for the floor below it. */
    static final int MAX_FLUID_DEPTH = LodColumnScan.MAX_FLUID_DEPTH;
    /** First data version with the 1.18 chunk layout ("sections" at the root, paletted "block_states"). */
    static final int MIN_DATA_VERSION = 2860;
    /** Sky light assumed below a canopy when the light engine has no value for the ground yet. */
    static final int SHADED_SKY_FALLBACK = 12;

    private LodWorldScanner() {
    }

    /** Reads a block at chunk-local {@code x, z} and absolute {@code y}. */
    @FunctionalInterface
    interface BlockReader {
        BlockState state(int x, int y, int z);
    }

    /** The surface of a chunk before block ids and tints are resolved. Filled by one thread, then handed over. */
    static final class RawChunk {
        final int chunkX;
        final int chunkZ;
        final long sequence;
        final int[] solidTop = new int[LodChunkData.AREA];
        final BlockState[] solid = new BlockState[LodChunkData.AREA];
        final int[] fluidTop = new int[LodChunkData.AREA];
        final BlockState[] fluid = new BlockState[LodChunkData.AREA];
        final int[] canopyTop = new int[LodChunkData.AREA];
        final int[] canopyBottom = new int[LodChunkData.AREA];
        final BlockState[] canopy = new BlockState[LodChunkData.AREA];
        final byte[] canopyLight = new byte[LodChunkData.AREA];
        final Object[] biome = new Object[LodChunkData.AREA];
        final byte[] skyLight = new byte[LodChunkData.AREA];
        final byte[] blockLight = new byte[LodChunkData.AREA];

        RawChunk(int chunkX, int chunkZ, long sequence) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.sequence = sequence;
            Arrays.fill(solidTop, LodChunkData.NO_SURFACE);
            Arrays.fill(fluidTop, LodChunkData.NO_FLUID);
            Arrays.fill(canopyTop, LodChunkData.NO_CANOPY);
            Arrays.fill(canopyBottom, LodChunkData.NO_CANOPY);
        }

        @SuppressWarnings("unchecked")
        @Nullable
        Holder<Biome> biome(int i) {
            return (Holder<Biome>) biome[i];
        }

        boolean hasSurface(int i) {
            return solidTop[i] != LodChunkData.NO_SURFACE || fluidTop[i] != LodChunkData.NO_FLUID;
        }

        boolean hasCanopy(int i) {
            return canopyTop[i] != LodChunkData.NO_CANOPY;
        }
    }

    /** Kinds of the blocks of one column for {@link LodColumnScan}; one instance per scanning thread and chunk. */
    static final class ColumnKinds implements IntUnaryOperator {
        private final BlockReader reader;
        private int x;
        private int z;

        ColumnKinds(BlockReader reader) {
            this.reader = reader;
        }

        ColumnKinds at(int x, int z) {
            this.x = x;
            this.z = z;
            return this;
        }

        @Override
        public int applyAsInt(int y) {
            return kind(reader.state(x, y, z));
        }
    }

    // ------------------------------------------------------------------ loaded chunks

    /**
     * The surface of a loaded chunk. Client thread: the chunk, its light and the block colour palette belong to it.
     *
     * @param sequence capture order, see {@link LodChunkData#sequence()}
     */
    static RawChunk capture(ClientLevel level, LevelChunk chunk, long sequence) {
        return captureChunk(level, level.dimensionType().hasSkyLight(), level.getLightEngine(), chunk, sequence);
    }

    /**
     * The surface of any chunk with its light engine. Thread of the chunk's owner; the palette is computed here only
     * on the render thread and queued for it otherwise.
     */
    public static RawChunk captureChunk(LevelHeightAccessor level, boolean skyLit, @Nullable LevelLightEngine light,
            ChunkAccess chunk, long sequence) {
        ChunkPos pos = chunk.getPos();
        RawChunk raw = new RawChunk(pos.x, pos.z, sequence);
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();
        LayerLightEventListener skyLayer = light != null ? light.getLayerListener(LightLayer.SKY) : null;
        LayerLightEventListener blockLayer = light != null ? light.getLayerListener(LightLayer.BLOCK) : null;
        int baseX = pos.getMinBlockX();
        int baseZ = pos.getMinBlockZ();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockReader reader = (x, y, z) -> chunk.getBlockState(cursor.set(baseX + x, y, baseZ + z));
        ColumnKinds kinds = new ColumnKinds(reader);
        LodColumnScan.Result scan = new LodColumnScan.Result();
        BlockPos.MutableBlockPos lightPos = new BlockPos.MutableBlockPos();
        boolean renderThread = RenderSystem.isOnRenderThread();
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int topY = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                int i = LodChunkData.index(x, z);
                if (!resolveColumn(reader, kinds, scan, x, z, topY, minY, maxY, raw, i)) {
                    continue;
                }
                int surfaceY = raw.fluidTop[i] != LodChunkData.NO_FLUID ? raw.fluidTop[i] : raw.solidTop[i];
                lightPos.set(baseX + x, surfaceY, baseZ + z);
                int sky = skyLayer != null ? skyLayer.getLightValue(lightPos) : 15;
                // A surface open to the sky reads 0 only while its light has not arrived; under a canopy it is shade.
                if (skyLit && sky == 0) {
                    sky = raw.hasCanopy(i) ? SHADED_SKY_FALLBACK : 15;
                }
                raw.skyLight[i] = (byte) sky;
                raw.blockLight[i] = (byte) (blockLayer != null ? blockLayer.getLightValue(lightPos) : 0);
                int biomeY = surfaceY - 1;
                if (raw.hasCanopy(i)) {
                    lightPos.set(baseX + x, raw.canopyTop[i], baseZ + z);
                    int canopySky = skyLayer != null ? skyLayer.getLightValue(lightPos) : 15;
                    if (skyLit && canopySky == 0) {
                        canopySky = 15;
                    }
                    int canopyBlock = blockLayer != null ? blockLayer.getLightValue(lightPos) : 0;
                    raw.canopyLight[i] = (byte) ((canopyBlock & 15) << 4 | canopySky & 15);
                    biomeY = raw.canopyTop[i] - 1;
                }
                raw.biome[i] = chunk.getNoiseBiome(QuartPos.fromBlock(baseX + x), QuartPos.fromBlock(Math.max(minY, biomeY)),
                        QuartPos.fromBlock(baseZ + z));
                if (renderThread) {
                    LodBlockColors.ensure(raw.solid[i]);
                    LodBlockColors.ensure(raw.fluid[i]);
                    LodBlockColors.ensure(raw.canopy[i]);
                }
            }
        }
        return raw;
    }

    // ------------------------------------------------------------------ column logic

    /**
     * Fills column {@code i} of {@code raw} from the heightmap top at {@code topY} (the highest block that blocks
     * motion or holds a fluid), following {@link LodColumnScan}. A snow layer on the canopy or on open ground gives
     * that layer its colour; lava is ground with the lava block's colour; a fluid is stored as its source block.
     *
     * @return false for a column without a surface (a heightmap pointing at air)
     */
    static boolean resolveColumn(BlockReader reader, ColumnKinds kinds, LodColumnScan.Result scan, int x, int z, int topY, int minY,
            int maxY, RawChunk raw, int i) {
        if (!LodColumnScan.scan(kinds.at(x, z), topY, minY, scan)) {
            return false;
        }
        if (scan.hasCanopy()) {
            BlockState canopy = reader.state(x, scan.canopyTop, z);
            if (scan.canopyTop + 1 < maxY) {
                BlockState cover = reader.state(x, scan.canopyTop + 1, z);
                if (cover.is(Blocks.SNOW)) {
                    canopy = cover;
                }
            }
            raw.canopyTop[i] = scan.canopyTop + 1;
            raw.canopyBottom[i] = scan.canopyBottom;
            raw.canopy[i] = canopy;
        }
        if (scan.hasFluid()) {
            raw.fluidTop[i] = scan.fluid + 1;
            raw.fluid[i] = reader.state(x, scan.fluid, z).getFluidState().createLegacyBlock();
        }
        if (scan.hasGround()) {
            BlockState ground = reader.state(x, scan.ground, z);
            if (scan.groundKind == LodColumnScan.LAVA) {
                ground = ground.getFluidState().createLegacyBlock();
            } else if (!scan.hasFluid() && scan.ground + 1 < maxY) {
                BlockState cover = reader.state(x, scan.ground + 1, z);
                if (cover.is(Blocks.SNOW)) {
                    ground = cover;
                }
            }
            raw.solidTop[i] = scan.ground + 1;
            raw.solid[i] = ground;
        }
        return true;
    }

    /** {@link LodColumnScan} kind of a block state. Any thread: states, tags and fluids are immutable. */
    @SuppressWarnings("deprecation") // blocksMotion is the predicate of the MOTION_BLOCKING heightmap itself
    static int kind(BlockState state) {
        if (state.isAir()) {
            return LodColumnScan.AIR;
        }
        boolean motion = state.blocksMotion();
        FluidState fluid = state.getFluidState();
        if (!fluid.isEmpty() && !motion) {
            return fluid.is(FluidTags.LAVA) ? LodColumnScan.LAVA : LodColumnScan.WATER;
        }
        if (isFoliage(state)) {
            return LodColumnScan.FOLIAGE;
        }
        if (!motion) {
            return LodColumnScan.PASSABLE;
        }
        return state.is(BlockTags.LOGS) ? LodColumnScan.TRUNK : LodColumnScan.SOLID;
    }

    /** Leaves, by tag or by class (modded leaves that miss the tag). */
    static boolean isFoliage(BlockState state) {
        return state.is(BlockTags.LEAVES) || state.getBlock() instanceof LeavesBlock;
    }

    /** The heightmap's own predicate: the block the {@code MOTION_BLOCKING} height stops at. */
    @SuppressWarnings("deprecation")
    static boolean isHeightmapTop(BlockState state) {
        return state.blocksMotion() || !state.getFluidState().isEmpty();
    }

    // ------------------------------------------------------------------ saved chunks

    /** What decoding needs from the level, captured on the client thread and shared by worker threads. */
    record DecodeContext(int minY, int height, boolean skyLit, HolderGetter<Block> blocks, Registry<Biome> biomes) {
    }

    static DecodeContext decodeContext(ClientLevel level) {
        return new DecodeContext(level.getMinBuildHeight(), level.getHeight(), level.dimensionType().hasSkyLight(),
                BuiltInRegistries.BLOCK.asLookup(), level.registryAccess().registryOrThrow(Registries.BIOME));
    }

    /**
     * The surface of a saved chunk from its NBT. Worker thread.
     *
     * @return null when the chunk cannot be read: an older layout, or not fully generated
     */
    @Nullable
    static RawChunk decode(CompoundTag tag, int chunkX, int chunkZ, DecodeContext context) {
        if (tag.getInt("DataVersion") < MIN_DATA_VERSION || !tag.contains("sections", Tag.TAG_LIST)) {
            return null;
        }
        String status = tag.getString("Status");
        if (!"minecraft:full".equals(status) && !"full".equals(status)) {
            return null;
        }
        int minSection = context.minY() >> 4;
        int sectionCount = context.height() >> 4;
        NbtSection[] sections = new NbtSection[sectionCount];
        int highest = -1;
        ListTag list = tag.getList("sections", Tag.TAG_COMPOUND);
        for (int s = 0; s < list.size(); s++) {
            CompoundTag sectionTag = list.getCompound(s);
            int index = sectionTag.getByte("Y") - minSection;
            if (index < 0 || index >= sectionCount) {
                continue;
            }
            NbtSection section = NbtSection.read(sectionTag, context);
            sections[index] = section;
            if (!section.isAir()) {
                highest = Math.max(highest, index);
            }
        }
        RawChunk raw = new RawChunk(chunkX, chunkZ, 0L);
        if (highest < 0) {
            return raw;
        }
        int heightBits = LodPalette.heightmapBits(context.height());
        long[] heights = null;
        CompoundTag heightmaps = tag.getCompound("Heightmaps");
        if (heightmaps.contains("MOTION_BLOCKING", Tag.TAG_LONG_ARRAY)) {
            long[] data = heightmaps.getLongArray("MOTION_BLOCKING");
            if (data.length == LodPalette.expectedLongs(LodPalette.HEIGHTMAP_ENTRIES, heightBits)) {
                heights = data;
            }
        }
        BlockReader reader = (x, y, z) -> {
            int index = (y >> 4) - minSection;
            NbtSection section = index >= 0 && index < sectionCount ? sections[index] : null;
            return section == null ? Blocks.AIR.defaultBlockState() : section.state(x, y, z);
        };
        ColumnKinds kinds = new ColumnKinds(reader);
        LodColumnScan.Result scan = new LodColumnScan.Result();
        int maxY = context.minY() + context.height();
        int scanFrom = (highest + minSection << 4) + 15;
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int topY = heights != null
                        ? context.minY() + LodPalette.get(heights, heightBits, LodPalette.heightmapIndex(x, z)) - 1
                        : scanTop(reader, x, z, scanFrom, context.minY());
                int i = LodChunkData.index(x, z);
                if (topY < context.minY() || topY >= maxY || !resolveColumn(reader, kinds, scan, x, z, topY, context.minY(), maxY, raw, i)) {
                    continue;
                }
                int surfaceY = raw.fluidTop[i] != LodChunkData.NO_FLUID ? raw.fluidTop[i] : raw.solidTop[i];
                int sky = sectionSky(sections, minSection, x, surfaceY, z, context.skyLit());
                raw.skyLight[i] = (byte) (sky >= 0 ? sky : context.skyLit() ? raw.hasCanopy(i) ? SHADED_SKY_FALLBACK : 15 : 0);
                raw.blockLight[i] = (byte) sectionBlock(sections, minSection, x, surfaceY, z);
                int biomeY = surfaceY - 1;
                if (raw.hasCanopy(i)) {
                    int canopySky = sectionSky(sections, minSection, x, raw.canopyTop[i], z, context.skyLit());
                    int canopyBlock = sectionBlock(sections, minSection, x, raw.canopyTop[i], z);
                    raw.canopyLight[i] = (byte) (canopyBlock << 4 | (canopySky >= 0 ? canopySky : context.skyLit() ? 15 : 0));
                    biomeY = raw.canopyTop[i] - 1;
                }
                int biomeIndex = (Math.max(context.minY(), biomeY) >> 4) - minSection;
                NbtSection biomeSection = biomeIndex >= 0 && biomeIndex < sectionCount ? sections[biomeIndex] : null;
                if (biomeSection != null) {
                    raw.biome[i] = biomeSection.biome(x, biomeY, z);
                }
            }
        }
        return raw;
    }

    /** Saved sky light at block {@code y} of a column; -1 where the section or its light layer is missing. */
    private static int sectionSky(NbtSection[] sections, int minSection, int x, int y, int z, boolean skyLit) {
        if (!skyLit) {
            return 0;
        }
        int index = (y >> 4) - minSection;
        if (index >= sections.length) {
            return 15;
        }
        NbtSection section = index >= 0 ? sections[index] : null;
        return section == null ? -1 : section.skyLight(x, y, z);
    }

    /** Saved block light at block {@code y} of a column; 0 where it is missing. */
    private static int sectionBlock(NbtSection[] sections, int minSection, int x, int y, int z) {
        int index = (y >> 4) - minSection;
        NbtSection section = index >= 0 && index < sections.length ? sections[index] : null;
        return section == null ? 0 : section.blockLight(x, y, z);
    }

    /** The heightmap top found by walking down from {@code fromY}, for chunks saved without a heightmap. */
    static int scanTop(BlockReader reader, int x, int z, int fromY, int minY) {
        for (int y = fromY; y >= minY; y--) {
            if (isHeightmapTop(reader.state(x, y, z))) {
                return y;
            }
        }
        return minY - 1;
    }

    /** One saved section: its block and biome palettes resolved, the packed arrays read on demand. */
    static final class NbtSection {
        private final BlockState[] palette;
        private final int bits;
        private final long[] data;
        private final Object[] biomePalette;
        private final int biomeBits;
        private final long[] biomeData;
        private final byte[] blockLight;
        private final byte[] skyLight;

        private NbtSection(BlockState[] palette, int bits, long[] data, Object[] biomePalette, int biomeBits, long[] biomeData,
                byte[] blockLight, byte[] skyLight) {
            this.palette = palette;
            this.bits = bits;
            this.data = data;
            this.biomePalette = biomePalette;
            this.biomeBits = biomeBits;
            this.biomeData = biomeData;
            this.blockLight = blockLight;
            this.skyLight = skyLight;
        }

        static NbtSection read(CompoundTag tag, DecodeContext context) {
            CompoundTag states = tag.getCompound("block_states");
            ListTag paletteTag = states.getList("palette", Tag.TAG_COMPOUND);
            BlockState[] palette = new BlockState[Math.max(1, paletteTag.size())];
            palette[0] = Blocks.AIR.defaultBlockState();
            for (int p = 0; p < paletteTag.size(); p++) {
                palette[p] = NbtUtils.readBlockState(context.blocks(), paletteTag.getCompound(p));
            }
            int bits = LodPalette.blockStateBits(paletteTag.size());
            long[] data = bits == 0 ? null : states.getLongArray("data");
            if (data != null && data.length != LodPalette.expectedLongs(LodPalette.SECTION_BLOCKS, bits)) {
                data = null;
            }

            CompoundTag biomes = tag.getCompound("biomes");
            ListTag biomeTag = biomes.getList("palette", Tag.TAG_STRING);
            Object[] biomePalette = new Object[Math.max(1, biomeTag.size())];
            for (int p = 0; p < biomeTag.size(); p++) {
                ResourceLocation id = ResourceLocation.tryParse(biomeTag.getString(p));
                biomePalette[p] = id == null ? null : context.biomes().getHolder(id).orElse(null);
            }
            int biomeBits = LodPalette.biomeBits(biomeTag.size());
            long[] biomeData = biomeBits == 0 ? null : biomes.getLongArray("data");
            if (biomeData != null && biomeData.length != LodPalette.expectedLongs(LodPalette.SECTION_BIOMES, biomeBits)) {
                biomeData = null;
            }
            return new NbtSection(palette, bits, data, biomePalette, biomeBits, biomeData, lightLayer(tag, "BlockLight"),
                    lightLayer(tag, "SkyLight"));
        }

        @Nullable
        private static byte[] lightLayer(CompoundTag tag, String name) {
            byte[] layer = tag.contains(name, Tag.TAG_BYTE_ARRAY) ? tag.getByteArray(name) : null;
            return layer != null && layer.length == 2048 ? layer : null;
        }

        /** Whether the section holds nothing but air. */
        boolean isAir() {
            return data == null && palette[0].isAir();
        }

        /** Block at chunk-local {@code x, z} and absolute {@code y} (only its low four bits are used). */
        BlockState state(int x, int y, int z) {
            if (data == null) {
                return palette[0];
            }
            int p = LodPalette.get(data, bits, LodPalette.blockIndex(x, y, z));
            return p < palette.length ? palette[p] : Blocks.AIR.defaultBlockState();
        }

        int blockLight(int x, int y, int z) {
            return blockLight == null ? 0 : LodPalette.nibble(blockLight, LodPalette.blockIndex(x, y, z));
        }

        /** Saved sky light, -1 without a sky light layer. */
        int skyLight(int x, int y, int z) {
            return skyLight == null ? -1 : LodPalette.nibble(skyLight, LodPalette.blockIndex(x, y, z));
        }

        /** Biome at chunk-local {@code x, z} and absolute {@code y}, in block coordinates. */
        @Nullable
        Object biome(int x, int y, int z) {
            if (biomeData == null) {
                return biomePalette[0];
            }
            int p = LodPalette.get(biomeData, biomeBits, LodPalette.biomeIndex(x >> 2, (y & 15) >> 2, z >> 2));
            return p < biomePalette.length ? biomePalette[p] : null;
        }
    }

    // ------------------------------------------------------------------ ids and tints

    /** A finisher for chunks of {@code level}. Client thread; the finisher itself belongs to one worker task. */
    static Finisher finisher(ClientLevel level) {
        return new Finisher(Minecraft.getInstance().getBlockColors(),
                new TintGetter(level.getLightEngine(), level.getMinBuildHeight(), level.getHeight()),
                level.registryAccess().registryOrThrow(Registries.BIOME));
    }

    /** Resolves block ids and biome tints of a {@link RawChunk}. Not thread-safe: one per task. */
    static final class Finisher {
        private final BlockColors blockColors;
        private final TintGetter tints;
        private final Registry<Biome> biomes;
        private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        Finisher(BlockColors blockColors, TintGetter tints, Registry<Biome> biomes) {
            this.blockColors = blockColors;
            this.tints = tints;
            this.biomes = biomes;
        }

        LodChunkData finish(RawChunk raw) {
            LodChunkData data = new LodChunkData(raw.chunkX, raw.chunkZ, raw.sequence);
            int baseX = raw.chunkX << 4;
            int baseZ = raw.chunkZ << 4;
            for (int i = 0; i < LodChunkData.AREA; i++) {
                if (!raw.hasSurface(i)) {
                    continue;
                }
                int x = baseX + (i & 15);
                int z = baseZ + (i >> 4);
                Holder<Biome> holder = raw.biome(i);
                Biome biome = holder == null ? null : holder.value();
                BlockState solid = raw.solid[i];
                BlockState fluid = raw.fluid[i];
                data.setColumn(i, raw.solidTop[i], solid == null ? 0 : Block.getId(solid),
                        raw.fluidTop[i], fluid == null ? 0 : Block.getId(fluid), raw.skyLight[i], raw.blockLight[i]);
                data.biomeId[i] = biomeId(holder);
                int solidTint = solid == null ? LodChunkData.NO_TINT : tint(solid, biome, cursor.set(x, raw.solidTop[i] - 1, z));
                int fluidTint = fluid == null ? LodChunkData.NO_TINT : tint(fluid, biome, cursor.set(x, raw.fluidTop[i] - 1, z));
                data.setTints(i, solidTint, fluidTint);
                BlockState canopy = raw.canopy[i];
                if (raw.hasCanopy(i) && canopy != null) {
                    data.setCanopy(i, raw.canopyTop[i], raw.canopyBottom[i], Block.getId(canopy),
                            tint(canopy, biome, cursor.set(x, raw.canopyTop[i] - 1, z)), raw.canopyLight[i]);
                }
            }
            return data;
        }

        /**
         * Id of {@code holder}'s biome in the client's registry, by key: chunks from the integrated server carry the
         * server registry's instances. {@link LodChunkData#NO_BIOME} if unknown.
         */
        private int biomeId(@Nullable Holder<Biome> holder) {
            if (holder == null) {
                return LodChunkData.NO_BIOME;
            }
            Biome biome = holder.unwrapKey().map(biomes::get).orElse(null);
            int id = biomes.getId(biome != null ? biome : holder.value());
            return id < 0 ? LodChunkData.NO_BIOME : id;
        }

        /**
         * {@code data} with every tint computed again from its block states and biomes, with the colour providers and
         * colour maps loaded now: what a resource reload changes. Same chunk and sequence.
         */
        LodChunkData retint(LodChunkData data) {
            LodChunkData out = data.copy();
            int baseX = data.chunkX() << 4;
            int baseZ = data.chunkZ() << 4;
            for (int i = 0; i < LodChunkData.AREA; i++) {
                if (!out.hasSurface(i)) {
                    continue;
                }
                int x = baseX + (i & 15);
                int z = baseZ + (i >> 4);
                int biomeId = out.biomeId[i];
                Biome biome = biomeId == LodChunkData.NO_BIOME ? null : biomes.byId(biomeId);
                int solidTint = out.heightSolid[i] == LodChunkData.NO_SURFACE ? LodChunkData.NO_TINT
                        : tint(Block.stateById(out.blockStateId[i]), biome, cursor.set(x, out.heightSolid[i] - 1, z));
                int fluidTint = !out.hasFluid(i) ? LodChunkData.NO_TINT
                        : tint(Block.stateById(out.fluidStateId[i]), biome, cursor.set(x, out.heightFluid[i] - 1, z));
                out.setTints(i, solidTint, fluidTint);
                if (out.hasCanopy(i)) {
                    out.canopyTint[i] = tint(Block.stateById(out.canopyStateId[i]), biome, cursor.set(x, out.canopyTop[i] - 1, z)) & 0xFFFFFF;
                }
            }
            return out;
        }

        private int tint(BlockState state, @Nullable Biome biome, BlockPos pos) {
            if (biome == null) {
                return LodChunkData.NO_TINT;
            }
            int index = LodBlockColors.tintIndex(state);
            if (index < 0) {
                return LodChunkData.NO_TINT;
            }
            tints.biome = biome;
            try {
                int color = blockColors.getColor(state, tints, pos, index);
                return color == -1 ? LodChunkData.NO_TINT : color & 0xFFFFFF;
            } catch (RuntimeException e) {
                // A colour provider that needs a real level: keep the block untinted.
                return LodChunkData.NO_TINT;
            }
        }
    }

    /**
     * The level as colour providers see it while a column is tinted: every tint lookup answers with the column's
     * own biome, without blending; neighbouring blocks read as air. Safe on any thread.
     */
    static final class TintGetter implements BlockAndTintGetter {
        private final LevelLightEngine lightEngine;
        private final int minY;
        private final int height;
        @Nullable
        Biome biome;

        TintGetter(LevelLightEngine lightEngine, int minY, int height) {
            this.lightEngine = lightEngine;
            this.minY = minY;
            this.height = height;
        }

        @Override
        public float getShade(Direction direction, boolean shade) {
            return 1.0F;
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return lightEngine;
        }

        @Override
        public int getBlockTint(BlockPos pos, ColorResolver resolver) {
            Biome current = biome;
            return current == null ? -1 : resolver.getColor(current, pos.getX(), pos.getZ());
        }

        @Nullable
        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return Blocks.AIR.defaultBlockState();
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return Fluids.EMPTY.defaultFluidState();
        }

        @Override
        public int getHeight() {
            return height;
        }

        @Override
        public int getMinBuildHeight() {
            return minY;
        }
    }
}
