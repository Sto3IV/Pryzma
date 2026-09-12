package net.pryzma.util;

/*
 * Ranni: Temporary workaround. Will fix properly in v2.0 (Note: v2.0 was cancelled).
 */

import java.util.AbstractMap;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.unimi.dsi.fastutil.longs.Long2ObjectFunction;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelDataManager;
import net.neoforged.neoforge.common.util.TriState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.client.textures.FluidSpriteCache;

/**
 * NeoForge 21.1 meshing contracts for Pryzma's Forge-era chunk pipeline. Call sites are injected by
 * {@code net.pryzma.neoforge.SectionCompilerTransformer}; their descriptors are pinned by {@code PryzmaModTest}.
 */
public final class NeoForgeMeshing {
    private static final Logger LOGGER = LoggerFactory.getLogger("Pryzma");
    private static volatile boolean offThreadLogged;

    private NeoForgeMeshing() {
    }

    /**
     * Model data of the 3x3x3 sections around {@code pos}, captured as NeoForge's
     * {@code RenderRegionCache.createRegion} does. NeoForge only permits this on the level's owning
     * (render) thread; anywhere else meshing proceeds without model data.
     */
    public static Long2ObjectFunction<ModelData> captureModelData(Level level, SectionPos pos) {
        ModelDataManager manager = level.getModelDataManager();
        if (manager == null) {
            return ModelDataManager.EMPTY_SNAPSHOT;
        }
        try {
            return manager.snapshotSectionRegion(
                    pos.x() - 1, pos.y() - 1, pos.z() - 1, pos.x() + 1, pos.y() + 1, pos.z() + 1);
        } catch (UnsupportedOperationException offThread) {
            if (!offThreadLogged) {
                offThreadLogged = true;
                LOGGER.warn("Section region created off the render thread; meshing it without model data", offThread);
            }
            return ModelDataManager.EMPTY_SNAPSHOT;
        }
    }

    /** Builder thread: {@code pos} in a captured snapshot, {@link ModelData#EMPTY} when none was captured. */
    public static ModelData modelData(Long2ObjectFunction<ModelData> snapshot, BlockPos pos) {
        return snapshot == null ? ModelData.EMPTY : snapshot.get(pos.asLong());
    }

    /** Lookup-only {@code Map<BlockPos, ModelData>} over {@code region.getModelData}: the shape Pryzma's compile reads. */
    public static Map<BlockPos, ModelData> modelDataView(BlockAndTintGetter region) {
        return new AbstractMap<>() {
            @Override
            public ModelData get(Object key) {
                return key instanceof BlockPos pos ? region.getModelData(pos) : null;
            }

            @Override
            public ModelData getOrDefault(Object key, ModelData fallback) {
                ModelData data = get(key);
                return data != null ? data : fallback;
            }

            @Override
            public Set<Map.Entry<BlockPos, ModelData>> entrySet() {
                return Set.of();
            }
        };
    }

    /** NeoForge's {@code ModelBlockRenderer.tesselateBlock} ambient-occlusion decision. */
    public static boolean useAmbientOcclusion(
            BakedModel model, BlockState state, RenderType renderType, ModelData data, BlockAndTintGetter level, BlockPos pos) {
        TriState ao = model.useAmbientOcclusion(state, data == null ? ModelData.EMPTY : data, renderType);
        return ao == TriState.TRUE || ao == TriState.DEFAULT && state.getLightEmission(level, pos) == 0;
    }

    /**
     * Obtains fluid sprites (still, flowing, overlay) from NeoForge's {@link FluidSpriteCache},
     * falling back to the default icons array if unavailable.
     */
    public static TextureAtlasSprite[] getFluidSprites(
            BlockAndTintGetter level, BlockPos pos, FluidState fluidState, TextureAtlasSprite[] fallback) {
        try {
            TextureAtlasSprite[] sprites = FluidSpriteCache.getFluidSprites(level, pos, fluidState);
            if (sprites != null && sprites.length >= 2 && sprites[0] != null && sprites[1] != null) {
                return sprites;
            }
        } catch (Throwable t) {
            LOGGER.warn("Failed retrieving fluid sprites from FluidSpriteCache for {}", fluidState, t);
        }
        return fallback;
    }

    /** Reloads NeoForge's fluid sprite cache on resource reload. */
    public static void reloadFluidSprites() {
        try {
            FluidSpriteCache.reload();
        } catch (Throwable t) {
            LOGGER.warn("Failed reloading FluidSpriteCache", t);
        }
    }
}
