package net.pryzma.perf;

import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.pryzma.PryzmaConfig;

/**
 * Smooth World as OptiFine defines it: on a single-core machine the integrated server runs below
 * the render thread so frames stay even; with more cores both keep their usual priorities.
 */
public final class PrServerPriority {
    private PrServerPriority() {
    }

    /** Applies the priorities; safe from any thread, the work runs on the render thread. */
    public static void apply() {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread()) {
            mc.execute(PrServerPriority::apply);
            return;
        }
        boolean single = PryzmaConfig.isSingleProcessor();
        Thread.currentThread().setPriority(single && !PryzmaConfig.prSmoothWorld ? Thread.NORM_PRIORITY : Thread.MAX_PRIORITY);
        IntegratedServer server = mc.getSingleplayerServer();
        if (server != null) {
            server.getRunningThread().setPriority(single && PryzmaConfig.prSmoothWorld ? Thread.MIN_PRIORITY : Thread.NORM_PRIORITY);
        }
    }
}
