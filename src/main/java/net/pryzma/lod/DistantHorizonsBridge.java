package net.pryzma.lod;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bridge between Pryzma Farlands LOD and Distant Horizons.
 * <p>
 * Enforces strict mutual exclusivity:
 * <ul>
 *   <li>If Distant Horizons is installed and actively rendering, Farlands LOD is locked to OFF.</li>
 *   <li>If Farlands LOD is active (distance &gt; 0), Distant Horizons rendering is disabled and overridden via DH API.</li>
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
    private static boolean lastFarlandsSuppressionState = false;

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
     * When this returns true, Farlands must not run and its UI controls are locked.
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
     * Called whenever Farlands status changes.
     * If Farlands is enabled, Distant Horizons rendering is disabled.
     * If Farlands is disabled, Distant Horizons override is cleared.
     */
    public static void onFarlandsChanged(boolean farlandsEnabled) {
        lastFarlandsSuppressionState = farlandsEnabled;
        if (!isInstalled() && mockInstalled == null) {
            return;
        }
        setDhRenderingDisabledByFarlands(farlandsEnabled);
    }

    /**
     * Applies or removes the API override on Distant Horizons.
     */
    public static void setDhRenderingDisabledByFarlands(boolean disable) {
        lastFarlandsSuppressionState = disable;
        if (!isInstalled() && mockInstalled == null) {
            return;
        }
        try {
            // Try via DhApi
            Class<?> delayedClass = Class.forName("com.seibel.distanthorizons.api.DhApi$Delayed");
            Object configs = delayedClass.getField("configs").get(null);
            if (configs != null) {
                Object graphics = configs.getClass().getMethod("graphics").invoke(configs);
                if (graphics != null) {
                    Object renderingEnabled = graphics.getClass().getMethod("renderingEnabled").invoke(graphics);
                    if (renderingEnabled != null) {
                        if (disable) {
                            try {
                                renderingEnabled.getClass().getMethod("setValue", Object.class, String.class)
                                        .invoke(renderingEnabled, Boolean.FALSE, OVERRIDE_NAME);
                            } catch (NoSuchMethodException e) {
                                renderingEnabled.getClass().getMethod("setValue", Object.class)
                                        .invoke(renderingEnabled, Boolean.FALSE);
                            }
                        } else {
                            renderingEnabled.getClass().getMethod("clearValue").invoke(renderingEnabled);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            LOGGER.debug("Could not toggle DH rendering via DhApi: {}", t.getMessage());
        }

        // Also sync core config if present
        try {
            Class<?> configClientClass = Class.forName("com.seibel.distanthorizons.core.config.Config$Client");
            Object quickEntry = configClientClass.getField("quickEnableRendering").get(null);
            if (quickEntry != null) {
                if (disable) {
                    quickEntry.getClass().getMethod("set", Object.class).invoke(quickEntry, Boolean.FALSE);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Syncs suppression state on tick / world load if needed.
     */
    public static void syncWithFarlandsState(boolean farlandsEnabled) {
        if (farlandsEnabled != lastFarlandsSuppressionState) {
            onFarlandsChanged(farlandsEnabled);
        }
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
        lastFarlandsSuppressionState = false;
    }

    public static boolean isFarlandsSuppressionActive() {
        return lastFarlandsSuppressionState;
    }
}
