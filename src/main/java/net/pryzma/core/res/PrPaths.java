package net.pryzma.core.res;

import net.minecraft.resources.ResourceLocation;

/**
 * OptiFine resource path rules. Paths inside {@code .properties} files are resolved with
 * {@link #resolve}, which reproduces OptiFine's {@code TextureUtils.fixResourcePath}:
 *
 * <ul>
 *   <li>{@code assets/minecraft/x} → {@code x}</li>
 *   <li>{@code ./x} → {@code <base>/x}</li>
 *   <li>{@code ~/x}, {@code /~/x} → {@code optifine/x}</li>
 *   <li>{@code /x} → {@code optifine/x}</li>
 *   <li>anything else is used as written, optionally {@code namespace:}-qualified</li>
 * </ul>
 *
 * {@code mcpatcher/} is an alias of {@code optifine/}: {@link #logical} folds it so every engine
 * works on one spelling, and {@link PrResources} maps it back when reading.
 */
public final class PrPaths {
    public static final String OPTIFINE = "optifine/";
    public static final String MCPATCHER = "mcpatcher/";

    private PrPaths() {
    }

    public static String resolve(String path, String basePath) {
        path = path.trim().replace('\\', '/');
        if (path.startsWith("assets/minecraft/")) {
            return path.substring("assets/minecraft/".length());
        }
        if (path.startsWith("./")) {
            String rest = path.substring(2);
            return basePath.isEmpty() ? rest : (basePath.endsWith("/") ? basePath + rest : basePath + "/" + rest);
        }
        if (path.startsWith("/~")) {
            path = path.substring(1);
        }
        if (path.startsWith("~/")) {
            return OPTIFINE + path.substring(2);
        }
        if (path.startsWith("/")) {
            return OPTIFINE + path.substring(1);
        }
        return path;
    }

    /**
     * Resolves {@code path} relative to {@code base} and builds a location. A {@code namespace:}
     * prefix wins; otherwise the namespace of {@code base} is used. Returns {@code null} for an
     * invalid location.
     */
    public static ResourceLocation resolveLocation(String path, ResourceLocation base) {
        String resolved = resolve(path, parent(base.getPath()));
        int colon = resolved.indexOf(':');
        ResourceLocation loc = colon >= 0
                ? ResourceLocation.tryParse(resolved)
                : ResourceLocation.tryBuild(base.getNamespace(), resolved);
        return loc == null ? null : logical(loc);
    }

    /**
     * OptiFine {@code fixTextureName} (colormap sources): like {@link #resolveLocation}, but a bare
     * name that is not rooted in {@code textures/} or {@code optifine/} is taken relative to the
     * properties file.
     */
    public static ResourceLocation resolveSibling(String path, ResourceLocation base) {
        String dir = parent(base.getPath());
        String resolved = resolve(path, dir);
        if (resolved.indexOf(':') < 0 && !resolved.startsWith(dir) && !resolved.startsWith("textures/")
                && !resolved.startsWith(OPTIFINE) && !resolved.startsWith(MCPATCHER)) {
            resolved = dir.isEmpty() ? resolved : dir + "/" + resolved;
        }
        int colon = resolved.indexOf(':');
        ResourceLocation loc = colon >= 0
                ? ResourceLocation.tryParse(resolved)
                : ResourceLocation.tryBuild(base.getNamespace(), resolved);
        return loc == null ? null : logical(loc);
    }

    /** Folds {@code mcpatcher/...} onto {@code optifine/...}; other paths are unchanged. */
    public static ResourceLocation logical(ResourceLocation loc) {
        String p = loc.getPath();
        return p.startsWith(MCPATCHER) ? loc.withPath(OPTIFINE + p.substring(MCPATCHER.length())) : loc;
    }

    public static boolean isOptifine(ResourceLocation loc) {
        return loc.getPath().startsWith(OPTIFINE);
    }

    /** The {@code mcpatcher/} spelling of an {@code optifine/} location, or {@code null}. */
    public static ResourceLocation mcpatcherAlias(ResourceLocation loc) {
        String p = loc.getPath();
        return p.startsWith(OPTIFINE) ? loc.withPath(MCPATCHER + p.substring(OPTIFINE.length())) : null;
    }

    public static String parent(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash);
    }

    public static String baseName(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    public static String withExtension(String path, String ext) {
        return path.endsWith(ext) ? path : path + ext;
    }

    public static String stripExtension(String path, String ext) {
        return path.endsWith(ext) ? path.substring(0, path.length() - ext.length()) : path;
    }
}
