package net.pryzma.core;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.pryzma.Pryzma;
import net.pryzma.color.PryzmaColormaps;
import net.pryzma.core.expr.PrSmoother;
import net.pryzma.core.res.PrResources;
import net.pryzma.entity.texture.PrEntityTextures;
import net.pryzma.lightmap.PryzmaLightmap;
import net.pryzma.sky.PryzmaSky;

/**
 * Reloads the resource-driven engines. Everything is parsed on the reload worker into immutable
 * snapshots; the render thread only swaps references.
 */
public final class PrReloadListener extends SimplePreparableReloadListener<PrReloadListener.Prepared> {
    public record Prepared(PryzmaColormaps.Data colors, PryzmaLightmap.Data lightmaps, PryzmaSky.Data sky,
            PrEntityTextures.Data entityTextures) {
    }

    @Override
    protected Prepared prepare(ResourceManager manager, ProfilerFiller profiler) {
        long start = System.nanoTime();
        PrResources res = new PrResources(manager);
        profiler.push("pryzma_colors");
        PryzmaColormaps.Data colors = PryzmaColormaps.prepare(res);
        profiler.popPush("pryzma_lightmaps");
        PryzmaLightmap.Data lightmaps = PryzmaLightmap.prepare(res);
        profiler.popPush("pryzma_sky");
        PryzmaSky.Data sky = PryzmaSky.prepare(res);
        profiler.popPush("pryzma_entity_textures");
        PrEntityTextures.Data entityTextures = PrEntityTextures.prepare(res);
        profiler.pop();
        Pryzma.LOGGER.info("Pryzma resources parsed in {} ms", (System.nanoTime() - start) / 1_000_000);
        return new Prepared(colors, lightmaps, sky, entityTextures);
    }

    @Override
    protected void apply(Prepared prepared, ResourceManager manager, ProfilerFiller profiler) {
        PryzmaColormaps.apply(prepared.colors());
        PryzmaLightmap.apply(prepared.lightmaps());
        PryzmaSky.apply(prepared.sky());
        PrEntityTextures.apply(prepared.entityTextures());
        PrSmoother.reset();
    }
}
