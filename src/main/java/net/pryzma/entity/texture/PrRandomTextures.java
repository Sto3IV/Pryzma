package net.pryzma.entity.texture;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.pryzma.Pryzma;
import net.pryzma.core.res.PrPaths;
import net.pryzma.core.res.PrProperties;
import net.pryzma.core.res.PrResources;
import net.pryzma.entity.PrRandomProperties;

/**
 * Loader of OptiFine random entity textures. A texture {@code textures/<path>.png} varies through
 * {@code optifine/random/<path>.properties} or numbered files {@code optifine/random/<path>2.png ...};
 * the MCPatcher-era layout maps {@code textures/entity/<path>} to {@code optifine/mob/<path>}
 * ({@code mcpatcher/mob/} is read as the same tree). Overlay textures such as {@code spider_eyes}
 * inherit the rules of their base texture when they have none of their own.
 */
public final class PrRandomTextures {
    static final String PREFIX_RANDOM = "optifine/random/";
    static final String PREFIX_MOB = "optifine/mob/";
    private static final String[] DEPENDENT_SUFFIXES = {
        "_armor", "_eyes", "_exploding", "_shooting", "_fur", "_invulnerable", "_angry", "_tame", "_collar"
    };
    private static final String[] TEXTURE_KEYS = {"textures", "skins"};

    private PrRandomTextures() {
    }

    /** Every texture with random variants, keyed by the full texture location. */
    public static Map<ResourceLocation, PrRandomProperties<ResourceLocation>> load(PrResources res) {
        Set<ResourceLocation> randomFiles = new HashSet<>();
        randomFiles.addAll(res.list("random", ".png").keySet());
        randomFiles.addAll(res.list("random", ".properties").keySet());
        randomFiles.addAll(res.list("mob", ".png").keySet());
        randomFiles.addAll(res.list("mob", ".properties").keySet());
        Map<ResourceLocation, PrRandomProperties<ResourceLocation>> out = new HashMap<>();
        Set<ResourceLocation> checked = new HashSet<>();
        for (ResourceLocation file : randomFiles) {
            ResourceLocation texture = baseTexture(file);
            if (texture == null || !checked.add(texture) || !res.exists(texture)) {
                continue;
            }
            PrRandomProperties<ResourceLocation> props = make(res, texture, false);
            if (props == null) {
                props = make(res, texture, true);
            }
            if (props != null) {
                out.put(texture, props);
            }
        }
        if (!out.isEmpty()) {
            Pryzma.LOGGER.info("Random entity textures: {}", out.size());
        }
        return out;
    }

    /**
     * The texture a random-folder file belongs to: suffix, trailing digits and a trailing dot removed,
     * {@code optifine/random/} mapped to {@code textures/} and {@code optifine/mob/} to
     * {@code textures/entity/}. {@code null} for files outside both trees.
     */
    static ResourceLocation baseTexture(ResourceLocation file) {
        String path = PrPaths.logical(file).getPath();
        path = stripSuffix(stripSuffix(path, ".png"), ".properties");
        int end = path.length();
        while (end > 0 && Character.isDigit(path.charAt(end - 1))) {
            end--;
        }
        path = path.substring(0, end);
        if (path.endsWith(".")) {
            path = path.substring(0, path.length() - 1);
        }
        path += ".png";
        if (path.startsWith(PREFIX_RANDOM)) {
            return file.withPath("textures/" + path.substring(PREFIX_RANDOM.length()));
        }
        if (path.startsWith(PREFIX_MOB)) {
            return file.withPath("textures/entity/" + path.substring(PREFIX_MOB.length()));
        }
        return null;
    }

    /** The random-folder counterpart of a texture: {@code optifine/random/...} or legacy {@code optifine/mob/...}. */
    static ResourceLocation randomLocation(ResourceLocation texture, boolean legacy) {
        String path = texture.getPath();
        if (path.startsWith(PrPaths.OPTIFINE)) {
            return texture;
        }
        String from = legacy ? "textures/entity/" : "textures/";
        String to = legacy ? PREFIX_MOB : PREFIX_RANDOM;
        return path.startsWith(from) ? texture.withPath(to + path.substring(from.length())) : null;
    }

    /** OptiFine {@code getLocationIndexed}: {@code a.png} to {@code a2.png}, {@code a1.png} to {@code a1.2.png}. */
    static ResourceLocation indexed(ResourceLocation location, int index) {
        String path = location.getPath();
        int dot = path.lastIndexOf('.');
        if (dot < 0) {
            return null;
        }
        String prefix = path.substring(0, dot);
        String separator = !prefix.isEmpty() && Character.isDigit(prefix.charAt(prefix.length() - 1)) ? "." : "";
        return location.withPath(prefix + separator + index + path.substring(dot));
    }

    private static PrRandomProperties<ResourceLocation> make(PrResources res, ResourceLocation texture, boolean legacy) {
        ResourceLocation random = randomLocation(texture, legacy);
        if (random == null) {
            return null;
        }
        ResourceLocation propsLocation = propertiesFor(res, random);
        if (propsLocation != null) {
            PrProperties props = res.properties(propsLocation).orElse(null);
            if (props != null) {
                return PrRandomProperties.parse(props, TEXTURE_KEYS,
                        index -> resourceFor(res, texture, random, index), PrRandomTextures::isProfession,
                        msg -> Pryzma.LOGGER.warn("Random entities: {}", msg));
            }
        }
        List<ResourceLocation> variants = new ArrayList<>();
        variants.add(texture);
        for (int index = 2; index < variants.size() + 10; index++) {
            ResourceLocation candidate = indexed(random, index);
            if (candidate != null && res.exists(candidate)) {
                // The texture manager loads by location, so keep the spelling that exists.
                variants.add(res.physicalLocation(candidate).orElse(candidate));
            }
        }
        return variants.size() <= 1 ? null : PrRandomProperties.ofVariants(variants.toArray(new ResourceLocation[0]));
    }

    /** {@code <base>.properties}, else the base texture's for dependent overlays like {@code _eyes}. */
    private static ResourceLocation propertiesFor(PrResources res, ResourceLocation random) {
        String base = stripSuffix(random.getPath(), ".png");
        ResourceLocation own = random.withPath(base + ".properties");
        if (res.exists(own)) {
            return own;
        }
        for (String suffix : DEPENDENT_SUFFIXES) {
            if (base.endsWith(suffix)) {
                ResourceLocation parent = random.withPath(base.substring(0, base.length() - suffix.length()) + ".properties");
                return res.exists(parent) ? parent : null;
            }
        }
        return null;
    }

    private static ResourceLocation resourceFor(PrResources res, ResourceLocation texture, ResourceLocation random, int index) {
        if (index <= 1) {
            return texture;
        }
        ResourceLocation variant = indexed(random, index);
        if (variant == null || !res.exists(variant)) {
            return null;
        }
        // Reads through PrResources, which may have found the mcpatcher spelling.
        return res.physicalLocation(variant).orElse(variant);
    }

    private static boolean isProfession(ResourceLocation id) {
        return BuiltInRegistries.VILLAGER_PROFESSION.containsKey(id);
    }

    private static String stripSuffix(String s, String suffix) {
        return s.endsWith(suffix) ? s.substring(0, s.length() - suffix.length()) : s;
    }
}
