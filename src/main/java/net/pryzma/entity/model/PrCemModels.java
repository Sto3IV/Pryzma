package net.pryzma.entity.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.pryzma.Pryzma;
import net.pryzma.PryzmaConfig;
import net.pryzma.core.res.PrPaths;
import net.pryzma.core.res.PrProperties;
import net.pryzma.core.res.PrResources;
import net.pryzma.entity.PrEntityInfos;
import net.pryzma.entity.PrRandomProperties;
import net.pryzma.entity.PrWorldInfo;

/**
 * Custom Entity Models: finds the {@code .jem} of every model layer as it is baked and swaps in the
 * rebuilt part tree ({@link PrCemBuilder}); builds extra renderers for random model variants
 * ({@code <entity>2.jem ...}, rules in {@code <entity>.properties}) and picks one per entity.
 *
 * <p>Models are read from {@code <namespace>:emf/cem/} first, then {@code optifine/cem/} (and its
 * {@code mcpatcher/} spelling). Everything here runs on the render thread during resource reload.
 */
public final class PrCemModels {
    private static final String PARTS_TABLE = "/assets/pryzma/cem/parts.txt";
    private static final int MAX_BAKES_PER_LAYER = 64;
    private static Map<String, Map<String, String>> partTable;

    private static PrResources resources;
    private static final Map<ResourceLocation, Optional<PrJem>> PARSED = new HashMap<>();
    private static final Map<ModelLayerLocation, Integer> BAKES = new HashMap<>();
    private static boolean reloading;
    private static int variant = 1;
    private static int applied;

    /** Random model variants of one entity type: renderer and model texture per variant index. */
    private record Variants(PrRandomProperties<Integer> rules, Map<Integer, EntityRenderer<?>> renderers,
            Map<Integer, ResourceLocation> textures) {
    }

    private static Map<EntityType<?>, Variants> variants = Map.of();
    private static Map<EntityType<?>, ResourceLocation> baseTextures = Map.of();

    private PrCemModels() {
    }

    // ------------------------------------------------------------------ reload

    /** Starts a reload: the model set is about to be baked from {@code manager}. */
    public static void onModelSetReload(ResourceManager manager) {
        PrCemRender.releaseBuffers();
        PARSED.clear();
        BAKES.clear();
        applied = 0;
        resources = PryzmaConfig.prCustomEntityModels ? new PrResources(manager) : null;
        reloading = true;
        if (partTable == null) {
            partTable = loadPartTable();
        }
    }

    /** Entity renderers exist: applies model shadow sizes, builds the variant renderers, ends the reload. */
    public static void onEntityRenderersCreated(EntityRendererProvider.Context context,
            Map<EntityType<?>, EntityRendererProvider<?>> providers, Map<EntityType<?>, EntityRenderer<?>> created) {
        Map<EntityType<?>, Variants> built = new IdentityHashMap<>();
        Map<EntityType<?>, ResourceLocation> textures = new IdentityHashMap<>();
        if (resources != null) {
            for (Map.Entry<EntityType<?>, EntityRendererProvider<?>> e : providers.entrySet()) {
                EntityType<?> type = e.getKey();
                ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
                String name = entityName(id);
                ResourceLocation base = jemLocation(id.getNamespace(), name, 1);
                PrJem baseJem = base == null ? null : parse(base);
                if (baseJem != null && baseJem.texture() != null) {
                    textures.put(type, baseJem.texture());
                }
                if (baseJem != null && baseJem.shadowSize() >= 0.0F && created.get(type) != null) {
                    created.get(type).shadowRadius = baseJem.shadowSize();
                }
                PrRandomProperties<Integer> rules = variantRules(id.getNamespace(), name);
                if (rules == null) {
                    continue;
                }
                Map<Integer, EntityRenderer<?>> renderers = new HashMap<>();
                Map<Integer, ResourceLocation> variantTextures = new HashMap<>();
                for (Integer index : rules.allResources()) {
                    if (index <= 1 || renderers.containsKey(index)) {
                        continue;
                    }
                    variant = index;
                    try {
                        EntityRenderer<?> renderer = e.getValue().create(context);
                        renderers.put(index, renderer);
                        PrJem jem = parse(jemLocation(id.getNamespace(), name, index));
                        if (jem != null && jem.texture() != null) {
                            variantTextures.put(index, jem.texture());
                        }
                        if (jem != null && jem.shadowSize() >= 0.0F) {
                            renderer.shadowRadius = jem.shadowSize();
                        }
                    } catch (RuntimeException ex) {
                        Pryzma.LOGGER.warn("CEM variant {} of {} failed", index, id, ex);
                    } finally {
                        variant = 1;
                    }
                }
                if (!renderers.isEmpty()) {
                    built.put(type, new Variants(rules, renderers, variantTextures));
                }
            }
        }
        variants = built;
        baseTextures = textures;
        reloading = false;
        if (applied > 0 || !built.isEmpty()) {
            Pryzma.LOGGER.info("Custom entity models: {} layers, {} entities with random models", applied, built.size());
        }
    }

    private static Map<String, Map<String, String>> loadPartTable() {
        Map<String, Map<String, String>> table = new HashMap<>();
        try (InputStream in = PrCemModels.class.getResourceAsStream(PARTS_TABLE)) {
            if (in == null) {
                Pryzma.LOGGER.error("Missing {}", PARTS_TABLE);
                return table;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.US_ASCII));
            String line;
            while ((line = reader.readLine()) != null) {
                parsePartLine(line, table);
            }
        } catch (IOException e) {
            Pryzma.LOGGER.error("Cannot read {}", PARTS_TABLE, e);
        }
        return table;
    }

    /** One line of the part table: {@code <model> <optifine part>=<vanilla child> ...}. */
    static void parsePartLine(String line, Map<String, Map<String, String>> table) {
        String s = line.strip();
        if (s.isEmpty() || s.startsWith("#")) {
            return;
        }
        String[] tokens = s.split("\\s+");
        Map<String, String> parts = new LinkedHashMap<>();
        for (int i = 1; i < tokens.length; i++) {
            int eq = tokens[i].indexOf('=');
            if (eq > 0) {
                parts.put(tokens[i].substring(0, eq), tokens[i].substring(eq + 1));
            }
        }
        table.put(tokens[0], Map.copyOf(parts));
    }

    // ------------------------------------------------------------------ baking

    /** The tree for {@code layer}: the vanilla one, or the rebuilt one when a {@code .jem} applies. */
    public static ModelPart apply(ModelLayerLocation layer, ModelPart vanilla) {
        if (resources == null) {
            return vanilla;
        }
        if (!reloading && BAKES.merge(layer, 1, Integer::sum) > MAX_BAKES_PER_LAYER) {
            // Something bakes this layer over and over (a mod making models per frame); stay vanilla.
            return vanilla;
        }
        PrCemNames names = PrCemNames.of(layer.getModel().getNamespace(), layer.getModel().getPath(), layer.getLayer());
        for (String file : names.files()) {
            ResourceLocation location = jemLocation(names.namespace(), file, variant);
            PrJem jem = location == null ? null : parse(location);
            if (jem == null) {
                continue;
            }
            Map<String, String> parts = partTable.getOrDefault(names.mapId(), Map.of());
            ModelPart built = PrCemBuilder.build(file, jem, vanilla, parts,
                    msg -> Pryzma.LOGGER.warn("CEM {}: {}", location, msg));
            if (built != vanilla) {
                applied++;
            }
            return built;
        }
        return vanilla;
    }

    /** The model file of {@code name} (variant {@code index}), {@code emf/cem/} before {@code optifine/cem/}. */
    private static ResourceLocation jemLocation(String namespace, String name, int index) {
        String file = index <= 1 ? name : indexed(name, index);
        for (String root : new String[] {"emf/cem/", PrPaths.OPTIFINE + "cem/"}) {
            ResourceLocation loc = ResourceLocation.tryBuild(namespace, root + file + ".jem");
            if (loc != null && resources.exists(loc)) {
                return loc;
            }
        }
        return null;
    }

    /** OptiFine variant numbering: {@code creeper} to {@code creeper2}, {@code name1} to {@code name1.2}. */
    static String indexed(String name, int index) {
        boolean digit = !name.isEmpty() && Character.isDigit(name.charAt(name.length() - 1));
        return name + (digit ? "." : "") + index;
    }

    private static PrJem parse(ResourceLocation location) {
        return PARSED.computeIfAbsent(location, loc -> {
            try {
                JsonObject json = readJson(loc);
                return json == null ? Optional.empty() : Optional.of(PrJemParser.parse(json, loc, PrCemModels::readJson));
            } catch (RuntimeException e) {
                Pryzma.LOGGER.warn("Cannot load custom entity model {}: {}", loc, e.getMessage());
                return Optional.empty();
            }
        }).orElse(null);
    }

    private static JsonObject readJson(ResourceLocation location) {
        Optional<Resource> resource = resources.find(location);
        if (resource.isEmpty()) {
            return null;
        }
        try (Reader reader = resource.get().openAsReader()) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            Pryzma.LOGGER.warn("Cannot read {}: {}", location, e.getMessage());
            return null;
        }
    }

    // ------------------------------------------------------------------ variants

    /** OptiFine names the model of an entity after its id, with the same renames as the layers. */
    static String entityName(ResourceLocation entityId) {
        return switch (entityId.getPath()) {
            case "ender_dragon" -> "dragon";
            case "leash_knot" -> "lead_knot";
            case "pufferfish" -> "puffer_fish_small";
            default -> entityId.getPath();
        };
    }

    private static PrRandomProperties<Integer> variantRules(String namespace, String name) {
        for (String candidate : new String[] {name, name + "/" + name}) {
            ResourceLocation props = ResourceLocation.tryBuild(namespace, PrPaths.OPTIFINE + "cem/" + candidate + ".properties");
            if (props != null && resources.exists(props)) {
                PrProperties parsed = resources.properties(props).orElse(null);
                if (parsed != null) {
                    PrRandomProperties<Integer> rules = PrRandomProperties.parse(parsed, new String[] {"models"},
                            index -> jemLocation(namespace, name, index) != null ? index : null,
                            id -> BuiltInRegistries.VILLAGER_PROFESSION.containsKey(id),
                            msg -> Pryzma.LOGGER.warn("CEM rules: {}", msg));
                    if (rules != null) {
                        return rules;
                    }
                }
            }
        }
        List<Integer> numbered = new ArrayList<>();
        if (jemLocation(namespace, name, 1) == null) {
            return null;
        }
        numbered.add(1);
        for (int index = 2; index < numbered.size() + 10; index++) {
            if (jemLocation(namespace, name, index) != null) {
                numbered.add(index);
            }
        }
        return numbered.size() <= 1 ? null : PrRandomProperties.ofVariants(numbered.toArray(new Integer[0]));
    }

    /** The renderer for {@code entity}: its random model variant's, or {@code base}. */
    public static EntityRenderer<?> select(Entity entity, EntityRenderer<?> base) {
        Variants v = variants.get(entity.getType());
        if (v == null) {
            return base;
        }
        int[] rule = new int[1];
        Integer index = v.rules().select(PrEntityInfos.entity(entity), PrWorldInfo.client(), 1, rule);
        EntityRenderer<?> renderer = v.renderers().get(index);
        return renderer != null ? renderer : base;
    }

    /** The rule index ({@code rule_index}) and model texture for the entity about to render. */
    public static ResourceLocation modelTexture(Entity entity) {
        Variants v = variants.get(entity.getType());
        if (v == null) {
            return baseTextures.get(entity.getType());
        }
        int[] rule = new int[1];
        Integer index = v.rules().select(PrEntityInfos.entity(entity), PrWorldInfo.client(), 1, rule);
        PrCemContext.setRuleIndex(rule[0]);
        ResourceLocation texture = v.textures().get(index);
        return texture != null ? texture : index == 1 ? baseTextures.get(entity.getType()) : null;
    }

    /** Number of layers a {@code .jem} replaced in the last reload. */
    public static int appliedCount() {
        return applied;
    }
}
