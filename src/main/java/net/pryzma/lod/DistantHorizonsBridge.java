package net.pryzma.lod;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bridge between Pryzma Farlands LOD and Distant Horizons.
 * <p>
 * Ensures seamless coexistence:
 * <ul>
 *   <li>If Distant Horizons is installed and actively rendering, Farlands LOD automatically yields to Distant Horizons.</li>
 *   <li>Pryzma never disables or suppresses Distant Horizons rendering.</li>
 *   <li>If Distant Horizons rendering is turned off in DH configuration, Farlands can be used as a lightweight fallback.</li>
 * </ul>
 * All calls into Distant Horizons are made via reflection and guarded so that Pryzma runs
 * cleanly regardless of whether Distant Horizons is present, absent, or an incompatible version.
 */
public final class DistantHorizonsBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger("Pryzma/DistantHorizonsBridge");
    private static final String OVERRIDE_NAME = "Pryzma Farlands";

    // Testing / mock overrides
    private static Boolean mockInstalled = null;
    private static Boolean mockDhActive = null;

    private static Boolean installedCache = null;

    private DistantHorizonsBridge() {
    }

    /**
     * Checks whether Distant Horizons mod is installed in the game environment.
     */
    public static boolean isInstalled() {
        if (mockInstalled != null) {
            return mockInstalled;
        }
        if (installedCache != null) {
            return installedCache;
        }
        installedCache = detectInstalled();
        return installedCache;
    }

    private static boolean detectInstalled() {
        try {
            net.neoforged.fml.ModList list = net.neoforged.fml.ModList.get();
            if (list != null && list.isLoaded("distanthorizons")) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            net.neoforged.fml.loading.LoadingModList loading = net.neoforged.fml.loading.LoadingModList.get();
            if (loading != null && loading.getModFileById("distanthorizons") != null) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            Class.forName("com.seibel.distanthorizons.api.DhApi");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Returns true if Distant Horizons is installed and currently configured to render LODs.
     * When this returns true, Farlands automatically yields and lets Distant Horizons handle LOD rendering.
     */
    public static boolean isDhActive() {
        if (mockDhActive != null) {
            return mockDhActive;
        }
        if (!isInstalled()) {
            return false;
        }
        return queryDhRenderingActive();
    }

    private static boolean queryDhRenderingActive() {
        // First try official DhApi.Delayed.configs.graphics().renderingEnabled().getValue()
        try {
            Class<?> delayedClass = Class.forName("com.seibel.distanthorizons.api.DhApi$Delayed");
            Object configs = delayedClass.getField("configs").get(null);
            if (configs != null) {
                Object graphics = configs.getClass().getMethod("graphics").invoke(configs);
                if (graphics != null) {
                    Object renderingEnabled = graphics.getClass().getMethod("renderingEnabled").invoke(graphics);
                    if (renderingEnabled != null) {
                        Object val = renderingEnabled.getClass().getMethod("getValue").invoke(renderingEnabled);
                        if (val instanceof Boolean b) {
                            return b;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        // Fallback to core config: Config.Client.quickEnableRendering and rendererMode
        try {
            Class<?> configClientClass = Class.forName("com.seibel.distanthorizons.core.config.Config$Client");
            Object quickEntry = configClientClass.getField("quickEnableRendering").get(null);
            if (quickEntry != null) {
                Object quickVal = quickEntry.getClass().getMethod("get").invoke(quickEntry);
                if (quickVal instanceof Boolean b && !b) {
                    return false;
                }
            }
            Class<?> debugClass = Class.forName("com.seibel.distanthorizons.core.config.Config$Client$Advanced$Debugging");
            Object modeEntry = debugClass.getField("rendererMode").get(null);
            if (modeEntry != null) {
                Object modeVal = modeEntry.getClass().getMethod("get").invoke(modeEntry);
                if (modeVal != null && "DISABLED".equalsIgnoreCase(modeVal.toString())) {
                    return false;
                }
            }
            return true;
        } catch (Throwable ignored) {
        }

        return false;
    }

    /**
     * Clears any legacy override on Distant Horizons if one was set.
     * Pryzma Farlands never suppresses Distant Horizons.
     */
    public static void clearLegacyDhOverrides() {
        if (!isInstalled() && mockInstalled == null) {
            return;
        }
        clearThreadBudget();
        try {
            Class<?> delayedClass = Class.forName("com.seibel.distanthorizons.api.DhApi$Delayed");
            Object configs = delayedClass.getField("configs").get(null);
            if (configs != null) {
                Object graphics = configs.getClass().getMethod("graphics").invoke(configs);
                if (graphics != null) {
                    Object renderingEnabled = graphics.getClass().getMethod("renderingEnabled").invoke(graphics);
                    if (renderingEnabled != null) {
                        try {
                            renderingEnabled.getClass().getMethod("setValue", Object.class, String.class)
                                    .invoke(renderingEnabled, null, OVERRIDE_NAME);
                        } catch (NoSuchMethodException e) {
                            renderingEnabled.getClass().getMethod("clearValue").invoke(renderingEnabled);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Balances thread allocation between Pryzma near-world chunk meshing and Distant Horizons LOD meshing.
     * <p>
     * Distant Horizons by default spawns worker threads for 100% of available CPU cores with NORM_PRIORITY,
     * which starves near-world chunk meshing on Windows (causing terrain holes and stutters).
     * This method applies a graceful background budget to Distant Horizons (max 1-6 threads depending on CPU count)
     * and lowers its priority/runtime ratio so immediate near-world blocks always take precedence.
     */
    public static void applyThreadBudget() {
        if (!isInstalled() && mockInstalled == null) {
            return;
        }
        int cores = Runtime.getRuntime().availableProcessors();
        int safeDhThreads = Math.max(1, Math.min(6, cores / 4));

        // 1. Official DhApi: multiThreading().threadCount().setValue(...)
        try {
            Class<?> delayedClass = Class.forName("com.seibel.distanthorizons.api.DhApi$Delayed");
            Object configs = delayedClass.getField("configs").get(null);
            if (configs != null) {
                Object multiThreading = configs.getClass().getMethod("multiThreading").invoke(configs);
                if (multiThreading != null) {
                    Object threadCount = multiThreading.getClass().getMethod("threadCount").invoke(multiThreading);
                    if (threadCount != null) {
                        try {
                            threadCount.getClass().getMethod("setValue", Object.class, String.class)
                                    .invoke(threadCount, safeDhThreads, OVERRIDE_NAME);
                        } catch (NoSuchMethodException e) {
                            threadCount.getClass().getMethod("setValue", Object.class)
                                    .invoke(threadCount, safeDhThreads);
                        }
                    }
                    if (cores <= 8) {
                        Object runtimeRatio = multiThreading.getClass().getMethod("threadRuntimeRatio").invoke(multiThreading);
                        if (runtimeRatio != null) {
                            try {
                                runtimeRatio.getClass().getMethod("setValue", Object.class, String.class)
                                        .invoke(runtimeRatio, 0.7, OVERRIDE_NAME);
                            } catch (NoSuchMethodException e) {
                                runtimeRatio.getClass().getMethod("setValue", Object.class)
                                        .invoke(runtimeRatio, 0.7);
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        // 2. Adjust DH internal threadPriority entry to below-normal (priority 3) so near-world workers (priority 4) outrank it
        try {
            Class<?> configMtClass = Class.forName("com.seibel.distanthorizons.core.config.Config$Common$MultiThreading");
            Object priorityEntry = configMtClass.getField("threadPriority").get(null);
            if (priorityEntry != null) {
                try {
                    priorityEntry.getClass().getMethod("setApiValue", Object.class).invoke(priorityEntry, 3);
                } catch (Throwable t) {
                    priorityEntry.getClass().getMethod("set", Object.class).invoke(priorityEntry, 3);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Clears any thread count / priority overrides set on Distant Horizons.
     */
    public static void clearThreadBudget() {
        if (!isInstalled() && mockInstalled == null) {
            return;
        }
        try {
            Class<?> delayedClass = Class.forName("com.seibel.distanthorizons.api.DhApi$Delayed");
            Object configs = delayedClass.getField("configs").get(null);
            if (configs != null) {
                Object multiThreading = configs.getClass().getMethod("multiThreading").invoke(configs);
                if (multiThreading != null) {
                    Object threadCount = multiThreading.getClass().getMethod("threadCount").invoke(multiThreading);
                    if (threadCount != null) {
                        threadCount.getClass().getMethod("clearValue").invoke(threadCount);
                    }
                    Object runtimeRatio = multiThreading.getClass().getMethod("threadRuntimeRatio").invoke(multiThreading);
                    if (runtimeRatio != null) {
                        runtimeRatio.getClass().getMethod("clearValue").invoke(runtimeRatio);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            Class<?> configMtClass = Class.forName("com.seibel.distanthorizons.core.config.Config$Common$MultiThreading");
            Object priorityEntry = configMtClass.getField("threadPriority").get(null);
            if (priorityEntry != null) {
                try {
                    priorityEntry.getClass().getMethod("setApiValue", Object.class).invoke(priorityEntry, (Object) null);
                } catch (Throwable t) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Called whenever Farlands status changes.
     * Pryzma never disables DH rendering.
     */
    public static void onFarlandsChanged(boolean farlandsEnabled) {
        clearLegacyDhOverrides();
        applyThreadBudget();
    }

    /**
     * Kept for backward compatibility. Farlands never suppresses DH.
     */
    public static void setDhRenderingDisabledByFarlands(boolean disable) {
        if (!disable) {
            clearLegacyDhOverrides();
        }
    }

    /**
     * Syncs Farlands state and thread budget with Distant Horizons.
     */
    public static void syncWithFarlandsState(boolean farlandsEnabled) {
        // Farlands never suppresses DH.
        applyThreadBudget();
    }

    // ------------------------------------------------------------------ test hooks

    public static void setMockInstalled(@Nullable Boolean installed) {
        mockInstalled = installed;
        installedCache = null;
    }

    public static void setMockDhActive(@Nullable Boolean active) {
        mockDhActive = active;
    }

    public static void resetMocks() {
        mockInstalled = null;
        mockDhActive = null;
        installedCache = null;
    }

    public static boolean isFarlandsSuppressionActive() {
        return false;
    }
}
