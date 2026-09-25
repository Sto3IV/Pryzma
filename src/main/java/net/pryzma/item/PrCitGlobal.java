package net.pryzma.item;

import java.util.Locale;

import net.pryzma.core.res.PrProperties;

/**
 * Global enchantment settings ({@code optifine/cit.properties}): whether the default glint is
 * drawn ({@code useGlint}), how several matching effects combine ({@code method}: {@code average}
 * weighs them by enchantment level, {@code layered} stacks them at full strength, {@code cycle}
 * shows one at a time), how many are drawn ({@code cap}) and the cross-fade of the cycle in
 * seconds ({@code fade}). OptiFine reads only {@code useGlint}; the rest is MCPatcher's format.
 */
public record PrCitGlobal(boolean useGlint, Method method, int cap, float fade) {
    public enum Method {
        AVERAGE, LAYERED, CYCLE
    }

    public static final PrCitGlobal DEFAULT = new PrCitGlobal(true, Method.AVERAGE, Integer.MAX_VALUE, 0.5F);

    public static PrCitGlobal parse(PrProperties p) {
        Method method = switch (p.get("method", "average").trim().toLowerCase(Locale.ROOT)) {
            case "layered" -> Method.LAYERED;
            case "cycle" -> Method.CYCLE;
            default -> Method.AVERAGE;
        };
        int cap = p.getInt("cap", Integer.MAX_VALUE);
        return new PrCitGlobal(p.getBool("useGlint", true), method, cap <= 0 ? Integer.MAX_VALUE : cap,
                Math.max(0.0F, p.getFloat("fade", 0.5F)));
    }
}
