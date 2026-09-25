package net.pryzma.core.match;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * An OptiFine block list ({@code matchBlocks}, {@code blocks}): space separated tokens of the form
 * {@code [namespace:]name[:property=value[,value]...]}.
 *
 * <p>MCPatcher forms are accepted as well: numeric ids ({@code 20}), {@code id:meta} pairs
 * ({@code 35:14}) and pre-flattening names ({@code stained_glass}), each narrowed by the file's
 * {@code metadata} list and translated through {@link PrLegacyBlocks}.
 */
public final class PrBlockMatcher {
    private static final PrBlockMatcher EMPTY = new PrBlockMatcher(new IdentityHashMap<>(), false);

    /** Per block: {@code null} element = any state, otherwise every listed property must match. */
    private final Map<Block, List<Map<Property<?>, List<Comparable<?>>>>> byBlock;
    private final boolean legacy;

    private PrBlockMatcher(Map<Block, List<Map<Property<?>, List<Comparable<?>>>>> byBlock, boolean legacy) {
        this.byBlock = byBlock;
        this.legacy = legacy;
    }

    /** Whether any token was a pre-flattening id or name, i.e. whether {@code metadata} applied. */
    public boolean usedLegacy() {
        return legacy;
    }

    public static PrBlockMatcher empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return byBlock.isEmpty();
    }

    public Set<Block> blocks() {
        return Collections.unmodifiableSet(byBlock.keySet());
    }

    public boolean matches(BlockState state) {
        List<Map<Property<?>, List<Comparable<?>>>> alternatives = byBlock.get(state.getBlock());
        if (alternatives == null) {
            return false;
        }
        for (Map<Property<?>, List<Comparable<?>>> constraint : alternatives) {
            if (constraint == null || satisfies(state, constraint)) {
                return true;
            }
        }
        return false;
    }

    public boolean matches(Block block) {
        return byBlock.containsKey(block);
    }

    private static boolean satisfies(BlockState state, Map<Property<?>, List<Comparable<?>>> constraint) {
        for (Map.Entry<Property<?>, List<Comparable<?>>> e : constraint.entrySet()) {
            if (!state.hasProperty(e.getKey()) || !e.getValue().contains(state.getValue(e.getKey()))) {
                return false;
            }
        }
        return true;
    }

    /**
     * @param text     the block list, may be {@code null}
     * @param metadata the file's {@code metadata} value, applied to legacy tokens only
     */
    public static PrBlockMatcher parse(String text, String metadata, Consumer<String> warn) {
        if (text == null || text.isBlank()) {
            return EMPTY;
        }
        PrRangeList metas = metadata == null ? null : PrRangeList.parse(metadata);
        Map<Block, List<Map<Property<?>, List<Comparable<?>>>>> out = new IdentityHashMap<>();
        boolean legacy = false;
        for (String token : text.trim().split("\\s+")) {
            legacy |= parseToken(token, metas, out, warn);
        }
        return out.isEmpty() ? EMPTY : new PrBlockMatcher(out, legacy);
    }

    /** @return whether the token was a legacy (pre-flattening) reference */
    private static boolean parseToken(String token, PrRangeList metas,
            Map<Block, List<Map<Property<?>, List<Comparable<?>>>>> out, Consumer<String> warn) {
        String[] parts = token.split(":");
        if (isDigits(parts[0])) {
            PrRangeList tokenMetas = metas;
            if (parts.length > 1) {
                tokenMetas = PrRangeList.parse(String.join(",", Arrays.copyOfRange(parts, 1, parts.length)));
                if (tokenMetas == null) {
                    warn.accept("Invalid legacy block metadata: " + token);
                    return true;
                }
            }
            addLegacy(Integer.parseInt(parts[0]), tokenMetas, token, out, warn);
            return true;
        }
        String namespace = "minecraft";
        int nameIndex = 0;
        if (parts.length > 1 && !parts[1].isEmpty() && !parts[1].contains("=")) {
            namespace = parts[0];
            nameIndex = 1;
        }
        String name = parts[nameIndex];
        ResourceLocation id = ResourceLocation.tryBuild(namespace, name);
        if (id == null) {
            warn.accept("Invalid block name: " + token);
            return false;
        }
        Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(id);
        if (block.isEmpty()) {
            int legacyId = "minecraft".equals(namespace) ? PrLegacyBlocks.get().idOf(id.toString()) : -1;
            if (legacyId >= 0) {
                addLegacy(legacyId, metas, token, out, warn);
                return true;
            }
            warn.accept("Block not found: " + id);
            return false;
        }
        Map<Property<?>, List<Comparable<?>>> constraint = null;
        if (parts.length > nameIndex + 1) {
            constraint = parseProperties(block.get(), parts, nameIndex + 1, token, warn);
            if (constraint == null) {
                return false;
            }
        }
        out.computeIfAbsent(block.get(), b -> new ArrayList<>()).add(constraint);
        return false;
    }

    private static Map<Property<?>, List<Comparable<?>>> parseProperties(Block block, String[] parts, int from,
            String token, Consumer<String> warn) {
        StateDefinition<Block, BlockState> definition = block.getStateDefinition();
        Map<Property<?>, List<Comparable<?>>> constraint = new IdentityHashMap<>();
        for (int i = from; i < parts.length; i++) {
            String param = parts[i];
            if (param.isEmpty()) {
                continue;
            }
            int eq = param.indexOf('=');
            if (eq <= 0) {
                warn.accept("Invalid block property: " + param + " in " + token);
                return null;
            }
            Property<?> property = definition.getProperty(param.substring(0, eq));
            if (property == null) {
                warn.accept("Property not found: " + param.substring(0, eq) + " for " + block);
                return null;
            }
            List<Comparable<?>> values = constraint.computeIfAbsent(property, p -> new ArrayList<>());
            for (String value : param.substring(eq + 1).split(",")) {
                Optional<? extends Comparable<?>> parsed = property.getValue(value);
                if (parsed.isEmpty()) {
                    warn.accept("Property value not found: " + value + " for " + property.getName() + " of " + block);
                    return null;
                }
                values.add(parsed.get());
            }
        }
        return constraint.isEmpty() ? null : constraint;
    }

    private static void addLegacy(int id, PrRangeList metas, String token,
            Map<Block, List<Map<Property<?>, List<Comparable<?>>>>> out, Consumer<String> warn) {
        List<PrLegacyBlocks.ModernState> states = PrLegacyBlocks.get().states(id, metas);
        if (states.isEmpty()) {
            warn.accept("Unknown legacy block id: " + token);
            return;
        }
        for (PrLegacyBlocks.ModernState state : states) {
            ResourceLocation blockId = ResourceLocation.tryParse(state.block());
            Optional<Block> block = blockId == null ? Optional.empty() : BuiltInRegistries.BLOCK.getOptional(blockId);
            if (block.isEmpty()) {
                continue;
            }
            Map<Property<?>, List<Comparable<?>>> constraint = null;
            if (!state.properties().isEmpty()) {
                constraint = new IdentityHashMap<>();
                StateDefinition<Block, BlockState> definition = block.get().getStateDefinition();
                for (Map.Entry<String, String> p : state.properties().entrySet()) {
                    Property<?> property = definition.getProperty(p.getKey());
                    Optional<? extends Comparable<?>> value = property == null ? Optional.empty() : property.getValue(p.getValue());
                    if (value.isPresent()) {
                        constraint.computeIfAbsent(property, k -> new ArrayList<>()).add(value.get());
                    }
                }
                if (constraint.isEmpty()) {
                    constraint = null;
                }
            }
            out.computeIfAbsent(block.get(), b -> new ArrayList<>()).add(constraint);
        }
    }

    private static boolean isDigits(String s) {
        if (s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
