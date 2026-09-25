package net.pryzma.ctm;

/**
 * OptiFine CTM methods and their tile count rules. OptiFine requires an explicit {@code tiles}
 * list for every method; its per-method defaults are unreachable, so there are none here.
 */
public enum PrCtmMethod {
    CTM(47, false),
    CTM_COMPACT(5, false),
    HORIZONTAL(4, true),
    VERTICAL(4, true),
    HORIZONTAL_VERTICAL(7, true),
    VERTICAL_HORIZONTAL(7, true),
    TOP(1, true),
    RANDOM(1, false),
    REPEAT(1, false),
    FIXED(1, true),
    OVERLAY(17, false),
    OVERLAY_CTM(47, false),
    OVERLAY_RANDOM(1, false),
    OVERLAY_REPEAT(1, false),
    OVERLAY_FIXED(1, true);

    /** Required tile count: exact when {@link #exact}, otherwise a minimum. */
    final int tileCount;
    final boolean exact;

    PrCtmMethod(int tileCount, boolean exact) {
        this.tileCount = tileCount;
        this.exact = exact;
    }

    public boolean isOverlay() {
        return ordinal() >= OVERLAY.ordinal();
    }

    static PrCtmMethod parse(String s) {
        if (s == null) {
            return CTM;
        }
        return switch (s.trim()) {
            case "ctm", "glass" -> CTM;
            case "ctm_compact" -> CTM_COMPACT;
            case "horizontal", "bookshelf" -> HORIZONTAL;
            case "vertical" -> VERTICAL;
            case "horizontal+vertical", "h+v" -> HORIZONTAL_VERTICAL;
            case "vertical+horizontal", "v+h" -> VERTICAL_HORIZONTAL;
            case "top" -> TOP;
            case "random" -> RANDOM;
            case "repeat" -> REPEAT;
            case "fixed" -> FIXED;
            case "overlay" -> OVERLAY;
            case "overlay_ctm" -> OVERLAY_CTM;
            case "overlay_random" -> OVERLAY_RANDOM;
            case "overlay_repeat" -> OVERLAY_REPEAT;
            case "overlay_fixed" -> OVERLAY_FIXED;
            default -> null;
        };
    }
}
