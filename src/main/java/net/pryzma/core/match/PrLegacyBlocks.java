package net.pryzma.core.match;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.pryzma.Pryzma;

/**
 * MCPatcher / OptiFine 1.12 block ids. {@code flattening_ids.txt} maps every numeric id and
 * metadata of the pre-flattening game to the block state that replaced it, so packs that still
 * say {@code matchBlocks=95} with {@code metadata=0} keep working.
 */
public final class PrLegacyBlocks {
    /** A post-flattening block state: block id plus the properties that pin the legacy variant. */
    public record ModernState(String block, Map<String, String> properties) {
    }

    private static final Pattern STATE = Pattern.compile("\\{Name:'([^']+)'(?:,Properties:\\{([^}]*)\\})?\\}");
    private static final Pattern PROPERTY = Pattern.compile("([a-z0-9_]+):'([^']*)'");
    // Blocks renamed after the table was written (1.13 name -> current name).
    private static final Map<String, String> RENAMED = Map.of("minecraft:grass", "minecraft:short_grass");

    private static volatile PrLegacyBlocks instance;

    private final Map<Integer, Map<Integer, List<ModernState>>> byId;
    private final Map<String, Integer> idByLegacyName;

    private PrLegacyBlocks(Map<Integer, Map<Integer, List<ModernState>>> byId, Map<String, Integer> idByLegacyName) {
        this.byId = byId;
        this.idByLegacyName = idByLegacyName;
    }

    public static PrLegacyBlocks get() {
        PrLegacyBlocks table = instance;
        if (table == null) {
            synchronized (PrLegacyBlocks.class) {
                table = instance;
                if (table == null) {
                    table = load();
                    instance = table;
                }
            }
        }
        return table;
    }

    /** Modern states for legacy {@code id}; {@code metas == null} means every metadata. */
    public List<ModernState> states(int id, PrRangeList metas) {
        Map<Integer, List<ModernState>> byMeta = byId.get(id);
        if (byMeta == null) {
            return List.of();
        }
        List<ModernState> out = new ArrayList<>();
        for (Map.Entry<Integer, List<ModernState>> e : byMeta.entrySet()) {
            if (metas == null || metas.contains(e.getKey())) {
                out.addAll(e.getValue());
            }
        }
        return out;
    }

    /** Legacy numeric id of a pre-flattening block name ({@code minecraft:stained_glass}), or -1. */
    public int idOf(String legacyName) {
        return idByLegacyName.getOrDefault(legacyName, -1);
    }

    static PrLegacyBlocks load() {
        Map<Integer, Map<Integer, List<ModernState>>> byId = new HashMap<>();
        Map<String, Integer> names = new HashMap<>();
        try (InputStream in = PrLegacyBlocks.class.getResourceAsStream("flattening_ids.txt")) {
            if (in == null) {
                Pryzma.LOGGER.warn("flattening_ids.txt missing, legacy block ids are unavailable");
                return new PrLegacyBlocks(Map.of(), Map.of());
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                parseLine(line, byId, names);
            }
        } catch (IOException e) {
            Pryzma.LOGGER.warn("Cannot read flattening_ids.txt", e);
        }
        return new PrLegacyBlocks(byId, names);
    }

    static void parseLine(String line, Map<Integer, Map<Integer, List<ModernState>>> byId, Map<String, Integer> names) {
        if (line.isBlank() || line.startsWith("#")) {
            return;
        }
        Matcher m = STATE.matcher(line);
        if (!m.find()) {
            return;
        }
        String modernName = RENAMED.getOrDefault(m.group(1), m.group(1));
        Map<String, String> modernProps = properties(m.group(2));
        if (!m.find()) {
            return;
        }
        String legacyName = m.group(1);
        String[] tail = line.substring(m.end()).trim().split("\\s+");
        if (tail.length < 2) {
            return;
        }
        int id;
        int meta;
        try {
            id = Integer.parseInt(tail[0]);
            meta = Integer.parseInt(tail[1]);
        } catch (NumberFormatException e) {
            return;
        }
        byId.computeIfAbsent(id, k -> new LinkedHashMap<>())
                .computeIfAbsent(meta, k -> new ArrayList<>())
                .add(new ModernState(modernName, modernProps));
        names.putIfAbsent(legacyName, id);
    }

    private static Map<String, String> properties(String text) {
        if (text == null || text.isEmpty()) {
            return Map.of();
        }
        Map<String, String> props = new LinkedHashMap<>();
        Matcher m = PROPERTY.matcher(text);
        while (m.find()) {
            props.put(m.group(1), m.group(2));
        }
        return Collections.unmodifiableMap(props);
    }
}
