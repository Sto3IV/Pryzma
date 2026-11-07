package net.pryzma.item;

import java.util.Arrays;

import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

/**
 * Custom enchantment layers and glint control for CIT in Minecraft 26.3 (RenderPearl pipeline).
 * Glint suppression is handled natively via FoilType.NONE in LayerRenderState.
 */
public final class PrCitGlint {
    /** Strength steps: a layer's strength is baked into its render type, so batching survives. */
    static final int STEPS = 16;

    /** The layers of one item, each with the level that selected it, and whether the vanilla glint stays. */
    public record Effects(PrCitEntry[] entries, int[] levels, boolean vanilla) {
        public static final Effects NONE = new Effects(new PrCitEntry[0], new int[0], false);
        public static final Effects HIDE = new Effects(new PrCitEntry[0], new int[0], false);

        public boolean isEmpty() {
            return entries.length == 0;
        }

        /** Strength of each layer now: all full when layered, by level when averaged, one at a time when cycling. */
        public float[] strengths(PrCitGlobal global) {
            float[] out = new float[entries.length];
            switch (global.method()) {
                case LAYERED -> Arrays.fill(out, 1.0F);
                case AVERAGE -> {
                    int total = 0;
                    for (int level : levels) {
                        total += Math.max(level, 0);
                    }
                    for (int i = 0; i < out.length; i++) {
                        out[i] = total <= 0 ? 1.0F / out.length : Math.max(levels[i], 0) / (float) total;
                    }
                }
                case CYCLE -> {
                    float total = 0.0F;
                    for (PrCitEntry entry : entries) {
                        total += Math.max(entry.rule.duration, 0.01F);
                    }
                    float t = (Util.getMillis() % (long) Math.max(1.0F, total * 1000.0F)) / 1000.0F;
                    float start = 0.0F;
                    for (int i = 0; i < out.length; i++) {
                        float duration = Math.max(entries[i].rule.duration, 0.01F);
                        if (t >= start && t < start + duration) {
                            float local = t - start;
                            float fade = global.fade();
                            out[i] = fade <= 0.0F ? 1.0F : Mth.clamp(Math.min(local, duration - local) / fade, 0.0F, 1.0F);
                        }
                        start += duration;
                    }
                }
            }
            return out;
        }
    }

    /** The item being drawn, set by the item, armor and elytra hooks. */
    public static ItemStack stack;
    private static boolean bypass;

    private PrCitGlint() {
    }

    /** Whether a buffer request is the glint code's own and must pass through untouched. */
    public static boolean bypassing() {
        return bypass;
    }

    /** Whether an item is currently being rendered with potential custom glint layers. */
    public static boolean isRendering() {
        return stack != null;
    }

    /** Frees the layer buffers of the previous resource state. */
    static void release() {
        stack = null;
        bypass = false;
    }
}
