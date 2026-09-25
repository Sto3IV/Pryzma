package net.pryzma.item;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.pryzma.Pryzma;
import net.pryzma.core.res.PrImage;
import net.pryzma.core.res.PrPaths;
import net.pryzma.core.res.PrProperties;
import net.pryzma.core.res.PrResources;

/**
 * Reads every CIT rule of the current packs with the files it needs: the global settings, the
 * rules in OptiFine order, the pack-local model JSONs they reach, the block atlas sprites of item
 * rules and the plain textures (and glint widths) of armor, elytra and enchantment rules.
 *
 * <p>Rules come in OptiFine's per-pack order: a file that exists in several packs yields a rule
 * per copy, the higher pack's first, so a lower pack's rule still applies where the higher one
 * does not match.
 */
public final class PrCitLoader {
    /** Everything one reload found. */
    public record Result(PrResources resources, PrCitGlobal global, List<PrCitRule> rules,
            Map<ResourceLocation, JsonObject> models, Map<ResourceLocation, Resource> sprites,
            Map<ResourceLocation, ResourceLocation> physical, Map<ResourceLocation, Integer> widths) {
        public static final Result EMPTY = new Result(null, PrCitGlobal.DEFAULT, List.of(), Map.of(), Map.of(), Map.of(), Map.of());
    }

    private static final ResourceLocation GLOBAL = ResourceLocation.withDefaultNamespace("optifine/cit.properties");
    private static final String[] POTION_TYPES = {"normal", "splash", "linger"};

    private PrCitLoader() {
    }

    public static Result load(ResourceManager manager) {
        PrResources res = new PrResources(manager);
        PrCitGlobal global = res.properties(GLOBAL).map(PrCitGlobal::parse).orElse(PrCitGlobal.DEFAULT);
        List<PrCitRule> rules = new ArrayList<>();
        Set<ResourceLocation> written = new HashSet<>();
        for (PrResources.Entry e : res.listAll("cit", ".properties")) {
            written.add(e.location());
            res.properties(e).map(p -> PrCitRule.parse(p, warn(e.location()))).ifPresent(rules::add);
        }
        for (String type : POTION_TYPES) {
            for (PrResources.Entry e : res.listAll("cit/potion/" + type, ".png")) {
                PrProperties p = PrCitPotions.imageRule(e.location(), type);
                // An image with its own properties file is described by that file.
                if (p != null && !written.contains(p.location())) {
                    PrCitRule rule = PrCitRule.parse(p, warn(p.location()));
                    if (rule != null) {
                        rules.add(rule);
                    }
                }
            }
        }
        rules.sort(PrCitRule.ORDER);

        Map<ResourceLocation, JsonObject> models = new HashMap<>();
        Map<ResourceLocation, Resource> sprites = new HashMap<>();
        Map<ResourceLocation, ResourceLocation> physical = new HashMap<>();
        Map<ResourceLocation, Integer> widths = new HashMap<>();
        Deque<ResourceLocation> queue = new ArrayDeque<>();
        for (PrCitRule rule : rules) {
            Consumer<String> warn = warn(rule.location);
            if (rule.type == PrCitRule.Type.ITEM) {
                if (rule.texture != null) {
                    addSprite(res, PrCitRule.spriteOf(rule.texture), rule.texture, sprites, warn);
                }
                rule.textures.values().forEach(file -> addSprite(res, PrCitRule.spriteOf(file), file, sprites, warn));
                if (rule.model != null) {
                    queue.add(rule.model);
                }
                queue.addAll(rule.models.values());
                continue;
            }
            List<ResourceLocation> files = new ArrayList<>(rule.textures.values());
            if (rule.texture != null) {
                files.add(rule.texture);
            }
            for (ResourceLocation file : files) {
                res.physicalLocation(file).ifPresentOrElse(p -> physical.put(file, p), () -> warn.accept("File not found: " + file));
                if (rule.type == PrCitRule.Type.ENCHANTMENT) {
                    widths.computeIfAbsent(file, f -> res.image(f).map(PrImage::width).orElse(16));
                }
            }
        }
        Set<ResourceLocation> visited = new HashSet<>();
        while (!queue.isEmpty()) {
            ResourceLocation model = queue.poll();
            if (!PrCitModelJson.isLocal(model) || !visited.add(model)) {
                continue;
            }
            Consumer<String> warn = warn(model);
            JsonObject json = PrCitModelJson.read(res, model, warn);
            if (json == null) {
                warn.accept("Model not found");
                continue;
            }
            models.put(model, json);
            queue.addAll(PrCitModelJson.dependencies(json));
            for (ResourceLocation sprite : PrCitModelJson.textures(json)) {
                ResourceLocation file = PrPaths.isOptifine(sprite)
                        ? sprite.withSuffix(".png")
                        : sprite.withPath("textures/" + sprite.getPath() + ".png");
                addSprite(res, sprite, file, sprites, warn);
            }
        }
        return new Result(res, global, List.copyOf(rules), models, sprites, physical, widths);
    }

    /**
     * Adds a sprite to the block atlas. Sprites under {@code block/} and {@code item/} are already
     * there: the vanilla atlas lists both directories in every namespace.
     */
    private static void addSprite(PrResources res, ResourceLocation sprite, ResourceLocation file,
            Map<ResourceLocation, Resource> sprites, Consumer<String> warn) {
        String path = sprite.getPath();
        if (path.startsWith("block/") || path.startsWith("item/") || sprites.containsKey(sprite)) {
            return;
        }
        res.find(file).ifPresentOrElse(r -> sprites.put(sprite, r), () -> warn.accept("File not found: " + file));
    }

    static Consumer<String> warn(ResourceLocation location) {
        return message -> Pryzma.LOGGER.warn("CIT {}: {}", location, message);
    }
}
