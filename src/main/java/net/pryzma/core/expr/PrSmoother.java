package net.pryzma.core.expr;

import java.util.HashMap;
import java.util.Map;

/**
 * State of OptiFine's {@code smooth()}: one exponentially approaching value per id, shared by every
 * expression that uses that id. Fade times are seconds of wall-clock time.
 */
public final class PrSmoother {
    private static final Map<Integer, float[]> VALUES = new HashMap<>();
    private static final Map<Integer, long[]> TIMES = new HashMap<>();
    private static int nextId = 1;

    private PrSmoother() {
    }

    /** A fresh id for a {@code smooth()} call written without one. */
    public static synchronized int nextId() {
        // OptiFine counts automatic ids from 1 too; explicit ids in packs are small literals, so the
        // automatic range starts high enough never to collide with them.
        return 1_000_000 + nextId++;
    }

    public static synchronized void reset() {
        VALUES.clear();
        TIMES.clear();
    }

    public static synchronized float smooth(int id, float target, float fadeUpSec, float fadeDownSec, long nowMs) {
        float[] value = VALUES.get(id);
        long[] time = TIMES.get(id);
        if (value == null) {
            VALUES.put(id, new float[] {target});
            TIMES.put(id, new long[] {nowMs});
            return target;
        }
        float previous = value[0];
        float deltaSec = (nowMs - time[0]) / 1000.0F;
        float result = step(previous, target, deltaSec, target >= previous ? fadeUpSec : fadeDownSec);
        value[0] = result;
        time[0] = nowMs;
        return result;
    }

    /** OptiFine {@code SmoothFloat.getSmoothValue}: a frame-rate independent exponential approach. */
    static float step(float previous, float target, float deltaSec, float fadeSec) {
        if (deltaSec <= 0.0F) {
            return previous;
        }
        float delta = target - previous;
        if (fadeSec > 0.0F && deltaSec < fadeSec && Math.abs(delta) > 1.0E-6F) {
            float updates = fadeSec / deltaSec;
            float correction = 4.61F - 1.0F / (0.13F + updates / 10.0F);
            float k = Math.clamp(deltaSec / fadeSec * correction, 0.0F, 1.0F);
            return previous + delta * k;
        }
        return target;
    }
}
