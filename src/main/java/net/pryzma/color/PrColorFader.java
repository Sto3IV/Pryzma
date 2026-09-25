package net.pryzma.color;

import net.minecraft.world.phys.Vec3;

/**
 * Time-based approach towards a target colour, so crossing a biome border blends the sky, fog and
 * underwater colours over about a second instead of switching them (OptiFine's colour fader).
 */
final class PrColorFader {
    private Vec3 color;
    private long lastMillis;

    synchronized Vec3 approach(double r, double g, double b) {
        long now = System.currentTimeMillis();
        if (color == null) {
            color = new Vec3(r, g, b);
            lastMillis = now;
            return color;
        }
        long dt = now - lastMillis;
        if (dt <= 0) {
            return color;
        }
        lastMillis = now;
        if (Math.abs(r - color.x) < 0.004 && Math.abs(g - color.y) < 0.004 && Math.abs(b - color.z) < 0.004) {
            return color;
        }
        double k = Math.min(1.0, dt * 0.001);
        color = new Vec3(color.x + (r - color.x) * k, color.y + (g - color.y) * k, color.z + (b - color.z) * k);
        return color;
    }

    synchronized void reset() {
        color = null;
    }
}
