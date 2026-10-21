package net.pryzma.util;

import com.mojang.blaze3d.platform.GlUtil;
import java.util.Locale;

/**
 * Utility to identify whether the current runtime environment is an AMD / ATI Radeon GPU
 * running on Windows with the proprietary OpenGL ICD driver.
 */
public final class AmdGpuDetector {
    private static volatile Boolean isAmdWindows = null;

    private AmdGpuDetector() {}

    /**
     * Checks if the active GPU vendor/renderer indicates AMD Radeon on Windows.
     * Safe to call at any time; returns {@code false} before OpenGL initialization
     * without freezing the cached status.
     */
    public static boolean isAmdWindows() {
        if (isAmdWindows != null) {
            return isAmdWindows;
        }

        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!os.contains("win")) {
            isAmdWindows = Boolean.FALSE;
            return false;
        }

        try {
            String vendor = GlUtil.getVendor();
            String renderer = GlUtil.getRenderer();
            if (vendor == null && renderer == null) {
                return false; // OpenGL context not ready yet
            }

            if (vendor != null) {
                String v = vendor.toLowerCase(Locale.ROOT);
                if (v.contains("amd") || v.contains("ati") || v.contains("radeon") || v.contains("advanced micro devices")) {
                    isAmdWindows = Boolean.TRUE;
                    return true;
                }
            }

            if (renderer != null) {
                String r = renderer.toLowerCase(Locale.ROOT);
                if (r.contains("radeon") || r.contains("amd")) {
                    isAmdWindows = Boolean.TRUE;
                    return true;
                }
            }

            isAmdWindows = Boolean.FALSE;
            return false;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
