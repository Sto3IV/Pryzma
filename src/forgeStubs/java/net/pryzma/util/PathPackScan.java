package net.pryzma.util;

/*
 * Ranni: I'm not sure why this works, but it does. Don't touch it.
 */

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Filesystem walk for Pryzma {@code ResUtils} when {@link Path#toFile()} is
 * illegal (NeoForge union/jar pack roots).
 * Also handles legacy MCPatcher ({@code assets/minecraft/mcpatcher/}) resource pack
 * format aliasing, ensuring 10-year-old packs function transparently with OptiFine/Pryzma.
 */
public final class PathPackScan {
    private static final String ASSETS = "assets/minecraft/";
    public static final String OPTIFINE_PREFIX = "optifine/";
    public static final String MCPATCHER_PREFIX = "mcpatcher/";

    private PathPackScan() {
    }

    public static String[] collect(Path root, String[] prefixes, String[] suffixes) {
        if (root == null) {
            return new String[0];
        }
        try {
            File file = root.toFile();
            if (file.isFile()) {
                return walkZip(file, prefixes, suffixes);
            }
            if (file.isDirectory()) {
                return walkNio(root, prefixes, suffixes);
            }
            return new String[0];
        } catch (UnsupportedOperationException e) {
            return walkNio(root, prefixes, suffixes);
        }
    }

    static String[] walkNio(Path root, String[] prefixes, String[] suffixes) {
        List<String> list = new ArrayList<>();
        try (var stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile).forEach(p -> {
                String name = root.relativize(p).toString().replace('\\', '/');
                if (name.startsWith("/")) {
                    name = name.substring(1);
                }
                if (!name.startsWith(ASSETS)) {
                    return;
                }
                name = name.substring(ASSETS.length());
                if (matches(name, prefixes, suffixes) && name.equals(name.toLowerCase(Locale.ROOT))) {
                    list.add(name);
                }
            });
        } catch (Exception e) {
            return new String[0];
        }
        return finalizeList(list);
    }

    private static String[] walkZip(File zip, String[] prefixes, String[] suffixes) {
        List<String> list = new ArrayList<>();
        try (ZipFile zf = new ZipFile(zip)) {
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                String name = en.nextElement().getName().replace('\\', '/');
                if (name.startsWith("/")) {
                    name = name.substring(1);
                }
                if (!name.startsWith(ASSETS)) {
                    continue;
                }
                name = name.substring(ASSETS.length());
                if (matches(name, prefixes, suffixes) && name.equals(name.toLowerCase(Locale.ROOT))) {
                    list.add(name);
                }
            }
        } catch (IOException e) {
            return new String[0];
        }
        return finalizeList(list);
    }

    static boolean matches(String name, String[] prefixes, String[] suffixes) {
        boolean pre = prefixes == null || prefixes.length == 0;
        if (!pre) {
            for (String p : prefixes) {
                if (p != null) {
                    if (name.startsWith(p)) {
                        pre = true;
                        break;
                    }
                    if (p.startsWith(OPTIFINE_PREFIX)) {
                        String legacyPrefix = MCPATCHER_PREFIX + p.substring(OPTIFINE_PREFIX.length());
                        if (name.startsWith(legacyPrefix)) {
                            pre = true;
                            break;
                        }
                    } else if (p.startsWith(MCPATCHER_PREFIX)) {
                        String optiPrefix = OPTIFINE_PREFIX + p.substring(MCPATCHER_PREFIX.length());
                        if (name.startsWith(optiPrefix)) {
                            pre = true;
                            break;
                        }
                    }
                }
            }
        }
        if (!pre) {
            return false;
        }
        if (suffixes == null || suffixes.length == 0) {
            return true;
        }
        for (String s : suffixes) {
            if (s != null && name.endsWith(s)) {
                return true;
            }
        }
        return false;
    }

    static String[] finalizeList(List<String> raw) {
        Set<String> optifineSub = new HashSet<>();
        for (String s : raw) {
            if (s.startsWith(OPTIFINE_PREFIX)) {
                optifineSub.add(s.substring(OPTIFINE_PREFIX.length()));
            }
        }
        Set<String> seen = new HashSet<>();
        List<String> result = new ArrayList<>(raw.size());
        for (String s : raw) {
            if (s.startsWith(MCPATCHER_PREFIX)) {
                String sub = s.substring(MCPATCHER_PREFIX.length());
                if (optifineSub.contains(sub)) {
                    continue; // Shadowed by optifine/ variant
                }
            }
            if (seen.add(s)) {
                result.add(s);
            }
        }
        return result.toArray(new String[0]);
    }

    /**
     * Used by {@link net.minecraft.client.renderer.texture.TextureAtlas#isAbsoluteLocationPath(String)}.
     * Treats both modern {@code optifine/} and legacy {@code mcpatcher/} paths as absolute atlas locations,
     * preventing {@code textures/} prefix insertion when registering custom sprites.
     */
    public static boolean isAbsoluteLocationPath(String path) {
        if (path == null) {
            return false;
        }
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.startsWith(OPTIFINE_PREFIX) || lower.startsWith(MCPATCHER_PREFIX);
    }
}
