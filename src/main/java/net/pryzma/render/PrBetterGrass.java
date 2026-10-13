package net.pryzma.render;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.DirtPathBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.NyliumBlock;
import net.minecraft.world.level.block.SnowyDirtBlock;
import net.minecraft.world.level.block.SpreadingSnowyDirtBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.pryzma.PryzmaConfig;
import net.pryzma.ctm.PrQuads;

/**
 * Universal Better Grass engine for Vanilla and all modded grass blocks (The Aether, Biomes O' Plenty, etc.).
 * Dynamically projects top quad sprites and tint indices onto exposed side faces in Fast and Fancy modes.
 */
public final class PrBetterGrass {
    private static final ThreadLocal<BlockAndTintGetter> CURRENT_LEVEL = new ThreadLocal<>();
    private static final ThreadLocal<BlockPos> CURRENT_POS = new ThreadLocal<>();
    private static final ThreadLocal<BlockState> CURRENT_STATE = new ThreadLocal<>();

    private record CacheKey(BlockState state, Direction facing, boolean snowy) {}
    private static final Map<CacheKey, List<BakedQuad>> CACHE = new ConcurrentHashMap<>();
    private static volatile TextureAtlasSprite snowSprite;

    private PrBetterGrass() {
    }

    public static void setContext(@Nullable BlockAndTintGetter level, @Nullable BlockPos pos, @Nullable BlockState state) {
        CURRENT_LEVEL.set(level);
        CURRENT_POS.set(pos);
        CURRENT_STATE.set(state);
    }

    public static void clearContext() {
        CURRENT_LEVEL.remove();
        CURRENT_POS.remove();
        CURRENT_STATE.remove();
    }

    @Nullable
    public static BlockAndTintGetter getCurrentLevel() {
        return CURRENT_LEVEL.get();
    }

    @Nullable
    public static BlockPos getCurrentPos() {
        return CURRENT_POS.get();
    }

    @Nullable
    public static BlockState getCurrentState() {
        return CURRENT_STATE.get();
    }

    public static void clearCaches() {
        CACHE.clear();
        snowSprite = null;
    }

    /**
     * Determines whether {@code state} is a grass-like block eligible for Better Grass.
     * Supports Vanilla, The Aether, Nylium, Podzol, Mycelium, Dirt Path, and modded grass blocks.
     */
    public static boolean isGrassLike(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof BushBlock || block instanceof DoublePlantBlock) {
            return false;
        }
        if (block instanceof SpreadingSnowyDirtBlock
                || block instanceof SnowyDirtBlock
                || block instanceof NyliumBlock
                || block instanceof DirtPathBlock) {
            return true;
        }
        if (state.hasProperty(BlockStateProperties.SNOWY)) {
            return true;
        }
        String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
        return path.endsWith("_grass_block") || path.contains("grass_block");
    }

    /**
     * Checks whether the block at {@code pos} is currently covered with snow.
     */
    public static boolean isSnowy(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        if (state.hasProperty(BlockStateProperties.SNOWY) && state.getValue(BlockStateProperties.SNOWY)) {
            return true;
        }
        BlockState above = level.getBlockState(pos.above());
        if (above.is(Blocks.SNOW) || above.is(Blocks.SNOW_BLOCK)) {
            return true;
        }
        if (PryzmaConfig.prBetterSnow && PrBetterSnow.shouldRender(level, above, pos.above())) {
            return true;
        }
        return false;
    }

    @Nullable
    private static TextureAtlasSprite getSnowSprite() {
        TextureAtlasSprite s = snowSprite;
        if (s == null) {
            BakedModel snowModel = Minecraft.getInstance().getBlockRenderer().getBlockModel(Blocks.SNOW.defaultBlockState());
            List<BakedQuad> quads = snowModel.getQuads(Blocks.SNOW.defaultBlockState(), Direction.UP, RandomSource.create(), ModelData.EMPTY, null);
            if (!quads.isEmpty()) {
                s = quads.get(0).getSprite();
                snowSprite = s;
            }
        }
        return s;
    }

    /**
     * Returns replacement side quads for Better Grass, or {@code null} if vanilla quads should be used.
     */
    @Nullable
    public static List<BakedQuad> getFaceQuads(
            BakedModel model,
            BlockState state,
            Direction facing,
            RandomSource random,
            ModelData modelData,
            @Nullable RenderType renderType
    ) {
        if (state == null || facing == null || !facing.getAxis().isHorizontal() || !isGrassLike(state)) {
            return null;
        }

        BlockAndTintGetter level = CURRENT_LEVEL.get();
        BlockPos pos = CURRENT_POS.get();
        if (level == null || pos == null) {
            return null;
        }

        boolean snowy = isSnowy(level, pos, state);

        if (PryzmaConfig.isBetterGrassFancy()) {
            BlockPos adjPos = pos.relative(facing);
            BlockState adjState = level.getBlockState(adjPos);
            if (adjState.isSolidRender(level, adjPos)) {
                return null;
            }

            BlockPos belowPos = adjPos.below();
            BlockState belowState = level.getBlockState(belowPos);
            if (snowy) {
                boolean neighborHasSnow = adjState.is(Blocks.SNOW)
                        || adjState.is(Blocks.SNOW_BLOCK)
                        || (PryzmaConfig.prBetterSnow && PrBetterSnow.shouldRender(level, adjState, adjPos))
                        || belowState.is(Blocks.SNOW)
                        || belowState.is(Blocks.SNOW_BLOCK)
                        || (belowState.hasProperty(BlockStateProperties.SNOWY) && belowState.getValue(BlockStateProperties.SNOWY))
                        || (PryzmaConfig.prBetterSnow && PrBetterSnow.shouldRender(level, belowState, belowPos));
                if (!neighborHasSnow) {
                    return null;
                }
            } else {
                boolean matches = belowState.getBlock() == state.getBlock() || (isGrassLike(belowState) && isGrassLike(state));
                if (!matches) {
                    return null;
                }
            }
        }

        CacheKey key = new CacheKey(state, facing, snowy);
        List<BakedQuad> cached = CACHE.computeIfAbsent(key, k -> bakeQuads(model, state, facing, random, modelData, renderType, snowy));
        return cached.isEmpty() ? null : cached;
    }

    private static List<BakedQuad> bakeQuads(
            BakedModel model,
            BlockState state,
            Direction facing,
            RandomSource random,
            ModelData modelData,
            @Nullable RenderType renderType,
            boolean snowy
    ) {
        if (snowy) {
            TextureAtlasSprite snow = getSnowSprite();
            if (snow != null) {
                return List.of(PrQuads.fullFace(facing, snow, -1));
            }
            return Collections.emptyList();
        }

        List<BakedQuad> upQuads = model.getQuads(state, Direction.UP, random, modelData, renderType);
        if (upQuads.isEmpty()) {
            upQuads = model.getQuads(state, null, random, modelData, renderType);
        }
        if (upQuads.isEmpty()) {
            return Collections.emptyList();
        }

        float yMax = (state.getBlock() instanceof DirtPathBlock) ? 15.0F : 16.0F;
        List<BakedQuad> sideQuads = new ArrayList<>();
        for (BakedQuad uq : upQuads) {
            if (uq.getDirection() == Direction.UP || uq.getDirection() == null) {
                sideQuads.add(PrQuads.faceQuad(facing, uq.getSprite(), uq.getTintIndex(), yMax));
            }
        }
        if (sideQuads.isEmpty()) {
            BakedQuad uq = upQuads.get(0);
            sideQuads.add(PrQuads.faceQuad(facing, uq.getSprite(), uq.getTintIndex(), yMax));
        }

        return Collections.unmodifiableList(sideQuads);
    }
}
