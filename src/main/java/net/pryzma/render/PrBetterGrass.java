package net.pryzma.render;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.NyliumBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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
        if (block == Blocks.DIRT_PATH || block instanceof NyliumBlock) {
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
            try {
                BlockStateModel snowModel = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(Blocks.SNOW.defaultBlockState());
                List<BlockStateModelPart> parts = new ArrayList<>();
                snowModel.collectParts(RandomSource.create(), parts);
                if (!parts.isEmpty()) {
                    List<BakedQuad> quads = parts.get(0).getQuads(Direction.UP);
                    if (!quads.isEmpty()) {
                        s = quads.get(0).materialInfo().sprite();
                        snowSprite = s;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return s;
    }

    /**
     * Returns replacement side quads for Better Grass, or {@code null} if vanilla quads should be used.
     */
    @Nullable
    public static List<BakedQuad> getFaceQuads(
            BlockStateModelPart part,
            Direction facing
    ) {
        BlockState state = CURRENT_STATE.get();
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
            if (adjState.isSolidRender()) {
                return null;
            }

            BlockPos belowPos = adjPos.below();
            BlockState belowState = level.getBlockState(belowPos);
            if (snowy) {
                boolean neighborHasSnow = adjState.is(Blocks.SNOW)
                        || adjState.is(Blocks.SNOW_BLOCK)
                        || (PryzmaConfig.prBetterSnow && PrBetterSnow.shouldRender(level, adjState, adjPos));
                if (!neighborHasSnow && !belowState.is(Blocks.SNOW) && !belowState.is(Blocks.SNOW_BLOCK)) {
                    return null;
                }
            } else {
                if (adjState.getBlock() != state.getBlock() && belowState.getBlock() != state.getBlock()) {
                    return null;
                }
            }
        }

        CacheKey key = new CacheKey(state, facing, snowy);
        List<BakedQuad> cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }

        List<BakedQuad> topQuads = snowy ? Collections.emptyList() : part.getQuads(Direction.UP);
        TextureAtlasSprite topSprite = topQuads.isEmpty() ? (snowy ? getSnowSprite() : null) : topQuads.get(0).materialInfo().sprite();
        if (topSprite == null && snowy) {
            topSprite = getSnowSprite();
        }
        if (topSprite == null) {
            return null;
        }

        int tintIndex = snowy ? -1 : (topQuads.isEmpty() ? -1 : topQuads.get(0).materialInfo().tintIndex());
        List<BakedQuad> sideQuads = part.getQuads(facing);
        if (sideQuads.isEmpty()) {
            return null;
        }

        List<BakedQuad> result = new ArrayList<>(sideQuads.size());
        for (BakedQuad side : sideQuads) {
            BakedQuad replaced = PrQuads.retexture(side, topSprite, tintIndex);
            result.add(replaced);
        }

        CACHE.put(key, result);
        return result;
    }
}
