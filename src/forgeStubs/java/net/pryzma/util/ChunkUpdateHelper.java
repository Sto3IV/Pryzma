package net.pryzma.util;

/*
 * Ranni: sometimes I wonder if I should just quit and become a farmer.
 */

import java.util.List;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection;
import net.pryzma.Config;

/**
 * Dedicated helper for chunk update dispatching, replacing the legacy single-chunk
 * frame-choke with a modern worker-budgeted capacity engine inspired by Sodium's
 * ChunkBuilder architecture.
 *
 * <p>Preserves exact calls to {@code rebuildSectionAsync(dispatcher, cache)} and
 * {@code setNotDirty()}, ensuring 100% compatibility with Pryzma CTM, CEM, shaders,
 * and NeoForge model data.
 */
public final class ChunkUpdateHelper {

    private ChunkUpdateHelper() {
    }

    /**
     * Calculates the frame budget (maximum number of section rebuilds dispatched per frame)
     * according to the Chunk Updates setting (1..5) and the active worker thread count.
     *
     * <ul>
     *   <li>Option 1 (1 chunk): conservative budget (workers * 2)</li>
     *   <li>Option 2 (2 chunks): balanced budget (workers * 4)</li>
     *   <li>Option 3 (3 chunks): standard capacity (workers * 8)</li>
     *   <li>Option 4 (4 chunks): high throughput (workers * 16)</li>
     *   <li>Option 5 (5 chunks / max): unlimited, dispatches all visible dirty sections</li>
     * </ul>
     */
    public static int getBudgetPerFrame(int option, int workerCount) {
        int workers = Math.max(1, workerCount);
        return switch (option) {
            case 1 -> Math.max(1, workers * 2);
            case 2 -> Math.max(2, workers * 4);
            case 3 -> Math.max(4, workers * 8);
            case 4 -> Math.max(8, workers * 16);
            case 5 -> Integer.MAX_VALUE;
            default -> Math.max(4, workers * 8);
        };
    }

    /**
     * Dispatches async section rebuilds for visible dirty sections up to the dynamic frame budget.
     * Invoked directly from {@code LevelRenderer.compileSections}.
     */
    public static void scheduleChunkUpdates(
            SectionRenderDispatcher dispatcher,
            RenderRegionCache cache,
            List<RenderSection> chunksToUpdate
    ) {
        if (chunksToUpdate == null || chunksToUpdate.isEmpty() || dispatcher == null || cache == null) {
            return;
        }

        int option = Config.getChunkUpdates();
        if (option <= 0) {
            option = Config.getUpdatesPerFrame();
        }
        if (option <= 0) {
            option = 3;
        }

        PryzmaChunkExecutor.applyChunkUpdatesOption(option);
        int workers = PryzmaChunkExecutor.getWorkerCount();
        int budget = getBudgetPerFrame(option, workers);

        int scheduled = 0;
        for (RenderSection section : chunksToUpdate) {
            if (section == null || !section.isDirty()) {
                continue;
            }

            section.rebuildSectionAsync(dispatcher, cache);
            section.setNotDirty();
            scheduled++;

            if (scheduled >= budget) {
                break;
            }
        }
    }
}
