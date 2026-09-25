package net.pryzma.shader.id;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.pryzma.shader.PrShaderPack;

/**
 * Parses and indexes {@code block.properties}, {@code item.properties}, and {@code entity.properties}
 * from shader packs, establishing Iris and OptiFine feature parity for block, item, and entity ID mapping.
 *
 * <p>State lookups are indexed by {@link Block#getId(BlockState)} with an O(1) array cache for zero-allocation
 * chunk meshing and vertex assembly.
 */
public final class PrIdMap {
    public record StateRule(Map<String, String> predicates, int id) {
        public boolean matches(BlockState state) {
            for (Map.Entry<String, String> entry : predicates.entrySet()) {
                Property<?> prop = state.getBlock().getStateDefinition().getProperty(entry.getKey());
                if (prop == null) {
                    return false;
                }
                Comparable<?> val = state.getValue(prop);
                if (val == null || !String.valueOf(val).equalsIgnoreCase(entry.getValue())) {
                    return false;
                }
            }
            return true;
        }
    }

    public record TagRule(TagKey<Block> tag, Map<String, String> predicates, int id) {
        public boolean matches(BlockState state) {
            if (!state.is(tag)) {
                return false;
            }
            if (predicates.isEmpty()) {
                return true;
            }
            for (Map.Entry<String, String> entry : predicates.entrySet()) {
                Property<?> prop = state.getBlock().getStateDefinition().getProperty(entry.getKey());
                if (prop == null) {
                    return false;
                }
                Comparable<?> val = state.getValue(prop);
                if (val == null || !String.valueOf(val).equalsIgnoreCase(entry.getValue())) {
                    return false;
                }
            }
            return true;
        }
    }

    private final Map<ResourceLocation, List<StateRule>> stateRules;
    private final Map<ResourceLocation, Integer> simpleBlockIds;
    private final List<TagRule> tagRules;
    private final Map<ResourceLocation, Integer> itemIds;
    private final Map<ResourceLocation, Integer> entityIds;

    // High-performance O(1) lookup cache indexed by Block.getId(state)
    private short[] blockStateCache = new short[0];
    private final Map<BlockState, Integer> dynamicStateMap = new ConcurrentHashMap<>();

    private PrIdMap(Map<ResourceLocation, List<StateRule>> stateRules,
                    Map<ResourceLocation, Integer> simpleBlockIds,
                    List<TagRule> tagRules,
                    Map<ResourceLocation, Integer> itemIds,
                    Map<ResourceLocation, Integer> entityIds) {
        this.stateRules = stateRules;
        this.simpleBlockIds = simpleBlockIds;
        this.tagRules = tagRules;
        this.itemIds = itemIds;
        this.entityIds = entityIds;
    }

    public static PrIdMap empty() {
        Map<ResourceLocation, Integer> defaultBlocks = new HashMap<>();
        PrLegacyBlockIds.populateDefaults(defaultBlocks);
        return new PrIdMap(Collections.emptyMap(), defaultBlocks, Collections.emptyList(),
                Collections.emptyMap(), Collections.emptyMap());
    }

    /**
     * Loads the property maps from the pack, searching first in the dimension-specific folder
     * (e.g. {@code shaders/world0/}) then in the root {@code shaders/}.
     */
    public static PrIdMap load(PrShaderPack pack, String folder, Consumer<String> warn) {
        if (pack == null) {
            return empty();
        }

        Map<ResourceLocation, List<StateRule>> stateRules = new LinkedHashMap<>();
        Map<ResourceLocation, Integer> simpleBlockIds = new LinkedHashMap<>();
        List<TagRule> tagRules = new ArrayList<>();
        Map<ResourceLocation, Integer> itemIds = new LinkedHashMap<>();
        Map<ResourceLocation, Integer> entityIds = new LinkedHashMap<>();

        // 1. block.properties
        parseBlockProperties(pack, folder, stateRules, simpleBlockIds, tagRules, warn);

        // Populate unmapped standard blocks with legacy defaults
        PrLegacyBlockIds.populateDefaults(simpleBlockIds);

        // 2. item.properties
        parseSimpleProperties(pack, folder, "item.properties", "item.", itemIds, warn);

        // 3. entity.properties
        parseSimpleProperties(pack, folder, "entity.properties", "entity.", entityIds, warn);

        return new PrIdMap(stateRules, simpleBlockIds, tagRules, itemIds, entityIds);
    }

    private static void parseBlockProperties(PrShaderPack pack, String folder,
                                             Map<ResourceLocation, List<StateRule>> stateRules,
                                             Map<ResourceLocation, Integer> simpleBlockIds,
                                             List<TagRule> tagRules,
                                             Consumer<String> warn) {
        List<String> lines = readLines(pack, folder, "block.properties");
        if (lines.isEmpty() && folder != null) {
            lines = readLines(pack, null, "block.properties");
        }
        if (lines.isEmpty()) {
            return;
        }

        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = line.substring(0, eq).trim();
            String val = line.substring(eq + 1).trim();

            if (!key.startsWith("block.")) {
                continue;
            }
            int id;
            try {
                id = Integer.parseInt(key.substring(6).trim());
            } catch (NumberFormatException e) {
                continue;
            }

            for (String token : val.split("\\s+")) {
                if (token.isEmpty()) {
                    continue;
                }
                parseBlockToken(token, id, stateRules, simpleBlockIds, tagRules);
            }
        }
    }

    private static void parseBlockToken(String token, int id,
                                        Map<ResourceLocation, List<StateRule>> stateRules,
                                        Map<ResourceLocation, Integer> simpleBlockIds,
                                        List<TagRule> tagRules) {
        boolean isTag = token.startsWith("%");
        String clean = isTag ? token.substring(1) : token;
        String[] parts = clean.split(":");

        if (isTag) {
            // Tag syntax: %tag, %namespace:tag, %namespace:tag:prop=val...
            ResourceLocation tagLoc;
            int startPredicate;
            if (parts.length >= 2 && !parts[1].contains("=")) {
                tagLoc = ResourceLocation.fromNamespaceAndPath(parts[0], parts[1]);
                startPredicate = 2;
            } else {
                tagLoc = ResourceLocation.withDefaultNamespace(parts[0]);
                startPredicate = 1;
            }
            Map<String, String> predicates = parsePredicates(parts, startPredicate);
            TagKey<Block> tagKey = TagKey.create(Registries.BLOCK, tagLoc);
            tagRules.add(new TagRule(tagKey, predicates, id));
            return;
        }

        // Block syntax: block, namespace:block, block:prop=val, namespace:block:prop=val
        ResourceLocation blockLoc;
        int startPredicate;
        if (parts.length >= 2 && !parts[1].contains("=")) {
            blockLoc = ResourceLocation.fromNamespaceAndPath(parts[0], parts[1]);
            startPredicate = 2;
        } else {
            blockLoc = ResourceLocation.withDefaultNamespace(parts[0]);
            startPredicate = 1;
        }

        Map<String, String> predicates = parsePredicates(parts, startPredicate);
        if (predicates.isEmpty()) {
            // First declaration wins (OptiFine parity)
            simpleBlockIds.putIfAbsent(blockLoc, id);
        } else {
            stateRules.computeIfAbsent(blockLoc, k -> new ArrayList<>()).add(new StateRule(predicates, id));
        }
    }

    private static Map<String, String> parsePredicates(String[] parts, int start) {
        if (start >= parts.length) {
            return Collections.emptyMap();
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = start; i < parts.length; i++) {
            int eq = parts[i].indexOf('=');
            if (eq > 0) {
                map.put(parts[i].substring(0, eq).trim(), parts[i].substring(eq + 1).trim());
            }
        }
        return map;
    }

    private static void parseSimpleProperties(PrShaderPack pack, String folder, String filename,
                                              String prefix, Map<ResourceLocation, Integer> target,
                                              Consumer<String> warn) {
        List<String> lines = readLines(pack, folder, filename);
        if (lines.isEmpty() && folder != null) {
            lines = readLines(pack, null, filename);
        }
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = line.substring(0, eq).trim();
            String val = line.substring(eq + 1).trim();
            if (!key.startsWith(prefix)) {
                continue;
            }
            int id;
            try {
                id = Integer.parseInt(key.substring(prefix.length()).trim());
            } catch (NumberFormatException e) {
                continue;
            }
            for (String token : val.split("\\s+")) {
                if (!token.isEmpty()) {
                    ResourceLocation loc = token.contains(":")
                            ? ResourceLocation.parse(token)
                            : ResourceLocation.withDefaultNamespace(token);
                    target.putIfAbsent(loc, id);
                }
            }
        }
    }

    private static List<String> readLines(PrShaderPack pack, String folder, String filename) {
        String path = folder == null ? "shaders/" + filename : "shaders/" + folder + "/" + filename;
        String content = pack.read(path);
        if (content == null) {
            return Collections.emptyList();
        }
        List<String> lines = new ArrayList<>();
        StringBuilder continuation = new StringBuilder();
        for (String l : content.split("\r?\n")) {
            if (l.endsWith("\\")) {
                continuation.append(l, 0, l.length() - 1).append(" ");
            } else {
                continuation.append(l);
                lines.add(continuation.toString());
                continuation.setLength(0);
            }
        }
        if (continuation.length() > 0) {
            lines.add(continuation.toString());
        }
        return lines;
    }

    /** Resolves the integer block ID for the given {@link BlockState}. */
    public int getBlockId(BlockState state) {
        if (state == null) {
            return -1;
        }

        try {
            int stateId = Block.getId(state);
            if (stateId >= 0 && stateId < blockStateCache.length) {
                short cached = blockStateCache[stateId];
                if (cached != 0) {
                    return cached == -2 ? -1 : cached;
                }
            }

            int resolved = resolveBlockId(state);
            int idToStore = resolved == -1 ? -2 : resolved;

            if (stateId >= 0) {
                if (stateId >= blockStateCache.length) {
                    synchronized (this) {
                        if (stateId >= blockStateCache.length) {
                            blockStateCache = Arrays.copyOf(blockStateCache, Math.max(stateId + 256, blockStateCache.length * 2));
                        }
                    }
                }
                blockStateCache[stateId] = (short) idToStore;
            }
            return resolved;
        } catch (Throwable t) {
            return dynamicStateMap.computeIfAbsent(state, this::resolveBlockId);
        }
    }

    /** Resolves the block ID by ResourceLocation alone (without state predicates). */
    public int getBlockId(ResourceLocation blockLoc) {
        if (blockLoc == null) {
            return -1;
        }
        Integer simple = simpleBlockIds.get(blockLoc);
        if (simple != null) {
            return simple;
        }
        return PrLegacyBlockIds.get(blockLoc);
    }

    private int resolveBlockId(BlockState state) {
        ResourceLocation loc = BuiltInRegistries.BLOCK.getKey(state.getBlock());

        // 1. State specific rules (e.g. wheat:age=7)
        List<StateRule> rules = stateRules.get(loc);
        if (rules != null) {
            for (StateRule rule : rules) {
                if (rule.matches(state)) {
                    return rule.id();
                }
            }
        }

        // 2. Simple block ID
        Integer simple = simpleBlockIds.get(loc);
        if (simple != null) {
            return simple;
        }

        // 3. Tag rules (%leaves, %crops...)
        for (TagRule rule : tagRules) {
            if (rule.matches(state)) {
                return rule.id();
            }
        }

        // 4. Fallback legacy
        return PrLegacyBlockIds.get(loc);
    }

    /** Resolves the integer block ID for the given {@link BlockEntity}. */
    public int getBlockEntityId(BlockEntity blockEntity) {
        if (blockEntity == null) {
            return -1;
        }
        return getBlockId(blockEntity.getBlockState());
    }

    /** Resolves the integer item ID for the given item location. */
    public int getItemId(ResourceLocation itemLoc) {
        return itemIds.getOrDefault(itemLoc, -1);
    }

    /** Resolves the integer item ID for the given item stack. */
    public int getItemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return -1;
        }
        ResourceLocation loc = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return loc != null ? getItemId(loc) : -1;
    }

    /** Resolves the integer entity ID for the given entity type location. */
    public int getEntityId(ResourceLocation entityLoc) {
        return entityIds.getOrDefault(entityLoc, -1);
    }

    /** Resolves the integer entity ID for the given entity. */
    public int getEntityId(Entity entity) {
        if (entity == null) {
            return -1;
        }
        ResourceLocation loc = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return loc != null ? getEntityId(loc) : -1;
    }
}
