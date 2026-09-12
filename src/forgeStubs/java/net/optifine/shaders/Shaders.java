package net.optifine.shaders;

/**
 * Legacy OptiFine shader interface stub for external mods (e.g. Distant Horizons)
 * querying shader state via reflection without triggering global mod ID blacklists.
 * Delegates directly to {@link net.pryzma.shaders.Shaders}.
 */
public class Shaders {

    public static String getShaderPackName() {
        try {
            Class<?> cls = Class.forName("net.pryzma.shaders.Shaders");
            return (String) cls.getMethod("getShaderPackName").invoke(null);
        } catch (Throwable ignored) {
            return null;
        }
    }
}

