package net.pryzma.core.res;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;

/**
 * One parsed OptiFine / MCPatcher {@code .properties} file.
 *
 * <p>Parsing follows {@link Properties#load(Reader)} (escapes, continuations, {@code =} or
 * {@code :} separators) because that is what OptiFine uses, with two corrections taken from CIT
 * Resewn so files written for either engine read alike: a byte order mark is dropped, and a key
 * that runs into {@code =} keeps its colons ({@code components.minecraft:custom_name=x} is that
 * key, where {@code Properties} would cut it at the colon and OptiFine would never match the
 * rule). Declaration order is kept, since some formats are order sensitive. Values are trimmed.
 *
 * <p>{@link #location()} is the logical {@code optifine/...} location even when the file was read
 * from {@code mcpatcher/...}: relative references ({@code ./tile.png}) resolve against it.
 */
public final class PrProperties {
    private final ResourceLocation location;
    private final Map<String, String> values;

    private PrProperties(ResourceLocation location, Map<String, String> values) {
        this.location = location;
        this.values = values;
    }

    public static PrProperties parse(ResourceLocation location, Reader reader) throws IOException {
        Map<String, String> ordered = new LinkedHashMap<>();
        Properties sink = new Properties() {
            @Override
            public synchronized Object put(Object key, Object value) {
                ordered.put(((String) key).trim(), ((String) value).trim());
                return null;
            }
        };
        StringBuilder text = new StringBuilder();
        char[] chunk = new char[4096];
        for (int n; (n = reader.read(chunk)) >= 0; ) {
            text.append(chunk, 0, n);
        }
        sink.load(new StringReader(normalize(text.toString())));
        return new PrProperties(location, Collections.unmodifiableMap(ordered));
    }

    /** Drops a byte order mark and escapes the colons of keys that run into {@code =}; see the class note. */
    static String normalize(String text) {
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        } else if (text.startsWith("ï»¿")) {
            text = text.substring(3);
        }
        if (text.indexOf(':') < 0) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length() + 16);
        boolean continued = false;
        int start = 0;
        while (start <= text.length()) {
            int end = text.indexOf('\n', start);
            if (end < 0) {
                end = text.length();
            }
            String line = text.substring(start, end);
            boolean comment = !continued && isComment(line);
            out.append(continued || comment ? line : escapeKeyColons(line));
            continued = !comment && continues(line);
            if (end < text.length()) {
                out.append('\n');
            }
            start = end + 1;
        }
        return out.toString();
    }

    private static boolean isBlank(char c) {
        return c == ' ' || c == '\t' || c == '\f';
    }

    private static boolean isComment(String line) {
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (!isBlank(c)) {
                return c == '#' || c == '!';
            }
        }
        return false;
    }

    /** A line ending in an odd number of backslashes continues on the next one. */
    private static boolean continues(String line) {
        int end = line.endsWith("\r") ? line.length() - 1 : line.length();
        int slashes = 0;
        while (end - slashes > 0 && line.charAt(end - slashes - 1) == '\\') {
            slashes++;
        }
        return (slashes & 1) == 1;
    }

    /** Escapes the unescaped colons of a key token followed, after optional blanks, by {@code =}. */
    private static String escapeKeyColons(String line) {
        int from = 0;
        while (from < line.length() && isBlank(line.charAt(from))) {
            from++;
        }
        int keyEnd = from;
        boolean colon = false;
        for (; keyEnd < line.length(); keyEnd++) {
            char c = line.charAt(keyEnd);
            if (c == '\\') {
                keyEnd++;
            } else if (c == '=' || isBlank(c)) {
                break;
            } else if (c == ':') {
                colon = true;
            }
        }
        int sep = keyEnd;
        while (sep < line.length() && isBlank(line.charAt(sep))) {
            sep++;
        }
        if (!colon || sep >= line.length() || line.charAt(sep) != '=') {
            return line;
        }
        StringBuilder out = new StringBuilder(line.length() + 4).append(line, 0, from);
        for (int i = from; i < keyEnd; i++) {
            char c = line.charAt(i);
            if (c == '\\' && i + 1 < keyEnd) {
                out.append(c).append(line.charAt(++i));
                continue;
            }
            if (c == ':') {
                out.append('\\');
            }
            out.append(c);
        }
        return out.append(line, keyEnd, line.length()).toString();
    }

    public static PrProperties parse(ResourceLocation location, String text) {
        try {
            return parse(location, new StringReader(text));
        } catch (IOException e) {
            throw new IllegalStateException("StringReader cannot fail", e);
        }
    }

    public static PrProperties of(ResourceLocation location, Map<String, String> values) {
        return new PrProperties(location, Collections.unmodifiableMap(new LinkedHashMap<>(values)));
    }

    /** Logical location of this file ({@code namespace:optifine/...}). */
    public ResourceLocation location() {
        return location;
    }

    /** Directory of this file, without a trailing slash; the base for {@code ./} references. */
    public String basePath() {
        return PrPaths.parent(location.getPath());
    }

    /** File name without directory and extension. */
    public String name() {
        return PrPaths.baseName(location.getPath());
    }

    public Map<String, String> asMap() {
        return values;
    }

    public Set<String> keys() {
        return values.keySet();
    }

    public boolean has(String key) {
        return values.containsKey(key);
    }

    public String get(String key) {
        return values.get(key);
    }

    public String get(String key, String def) {
        String v = values.get(key);
        return v == null ? def : v;
    }

    /** OptiFine {@code parseInt}: non-negative integers only, anything else yields {@code def}. */
    public int getInt(String key, int def) {
        int v = parseInt(values.get(key), -1);
        return v < 0 ? def : v;
    }

    /** OptiFine {@code parseIntNeg}: any integer. */
    public int getIntSigned(String key, int def) {
        return parseInt(values.get(key), def);
    }

    public float getFloat(String key, float def) {
        String v = values.get(key);
        if (v == null) {
            return def;
        }
        try {
            return Float.parseFloat(v);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public boolean getBool(String key, boolean def) {
        String v = values.get(key);
        if (v == null) {
            return def;
        }
        if (v.equalsIgnoreCase("true")) {
            return true;
        }
        if (v.equalsIgnoreCase("false")) {
            return false;
        }
        return def;
    }

    /** OptiFine hex colour ({@code rrggbb}); {@code def} when absent or malformed. */
    public int getColor(String key, int def) {
        return parseColor(values.get(key), def);
    }

    /** Every entry whose key starts with {@code prefix}, keyed by the remainder. */
    public Map<String, String> withPrefix(String prefix) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : values.entrySet()) {
            if (e.getKey().startsWith(prefix)) {
                out.put(e.getKey().substring(prefix.length()), e.getValue());
            }
        }
        return out;
    }

    public static int parseInt(String s, int def) {
        if (s == null) {
            return def;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public static int parseColor(String s, int def) {
        if (s == null) {
            return def;
        }
        try {
            return Integer.parseInt(s.trim(), 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return def;
        }
    }

    @Override
    public String toString() {
        return location.toString();
    }
}
