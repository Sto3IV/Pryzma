package net.pryzma.entity.texture;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.pryzma.PryzmaConfig;
import net.pryzma.core.res.PrPaths;
import net.pryzma.core.res.PrResources;
import net.pryzma.entity.PrEntityInfos;
import net.pryzma.entity.PrRandomProperties;
import net.pryzma.entity.PrWorldInfo;
import net.pryzma.render.PrRenderTypes;

/**
 * Entity texture features at render time: OptiFine random textures and emissive textures.
 *
 * <p>The texture of every entity render type passes through {@link #remap} (a mixin on the
 * {@code RenderType} factories), which knows the entity or block entity being rendered from
 * {@link #begin}. Emissive textures ({@code <texture><suffix>.png}, suffix from
 * {@code optifine/emissive.properties}) are drawn by a second render of the same entity at full
 * brightness in which every texture is swapped for its emissive counterpart and all geometry
 * without one is discarded, OptiFine's two-pass scheme.
 */
public final class PrEntityTextures {
    /** Parsed resource state of one reload. */
    public record Data(Map<ResourceLocation, PrRandomProperties<ResourceLocation>> random, String emissiveSuffix,
            ResourceManager manager) {
        public static final Data EMPTY = new Data(Map.of(), null, null);
    }

    private static volatile Data data = Data.EMPTY;
    /** Texture to its emissive texture; {@link Optional#empty()} caches "none". */
    private static final Map<ResourceLocation, Optional<ResourceLocation>> EMISSIVE = new ConcurrentHashMap<>();
    private static final Set<ResourceLocation> EMISSIVE_TEXTURES = ConcurrentHashMap.newKeySet();

    // Render thread state: the thing being rendered, its CEM model texture and the pass.
    private static Entity entity;
    private static BlockEntity blockEntity;
    private static ResourceLocation modelTextureFrom;
    private static ResourceLocation modelTextureTo;
    private static boolean emissivePass;
    private static boolean sawEmissive;

    private PrEntityTextures() {
    }

    // ------------------------------------------------------------------ loading

    public static Data prepare(PrResources res) {
        String suffix = res.properties(ResourceLocation.withDefaultNamespace(PrPaths.OPTIFINE + "emissive.properties"))
                .map(p -> p.get("suffix.emissive"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .orElse(null);
        return new Data(PrRandomTextures.load(res), suffix, res.manager());
    }

    public static void apply(Data prepared) {
        data = prepared;
        EMISSIVE.clear();
        EMISSIVE_TEXTURES.clear();
    }

    // ------------------------------------------------------------------ render context

    /** Saved context of an enclosing render, restored by {@link #end}. */
    public record Scope(Entity entity, BlockEntity blockEntity, ResourceLocation modelTextureFrom, ResourceLocation modelTextureTo,
            boolean emissivePass, boolean sawEmissive) {
    }

    private static Scope save() {
        return new Scope(entity, blockEntity, modelTextureFrom, modelTextureTo, emissivePass, sawEmissive);
    }

    public static Scope begin(Entity e) {
        Scope saved = save();
        entity = e;
        blockEntity = null;
        modelTextureFrom = null;
        modelTextureTo = null;
        emissivePass = false;
        sawEmissive = false;
        return saved;
    }

    public static Scope begin(BlockEntity be) {
        Scope saved = save();
        entity = null;
        blockEntity = be;
        modelTextureFrom = null;
        modelTextureTo = null;
        emissivePass = false;
        sawEmissive = false;
        return saved;
    }

    public static void end(Scope saved) {
        entity = saved.entity();
        blockEntity = saved.blockEntity();
        modelTextureFrom = saved.modelTextureFrom();
        modelTextureTo = saved.modelTextureTo();
        emissivePass = saved.emissivePass();
        sawEmissive = saved.sawEmissive();
    }

    /** A CEM model's {@code texture}: the renderer's own texture {@code from} is drawn as {@code to}. */
    public static void setModelTexture(ResourceLocation from, ResourceLocation to) {
        modelTextureFrom = from;
        modelTextureTo = to;
    }

    /** The entity being rendered, or {@code null}. */
    public static Entity entity() {
        return entity;
    }

    public static BlockEntity blockEntity() {
        return blockEntity;
    }

    /** Whether the render just finished used a texture with an emissive counterpart. */
    public static boolean needsEmissivePass() {
        return sawEmissive && data.emissiveSuffix() != null && PryzmaConfig.prEmissiveTextures;
    }

    /** Switches to the emissive pass and returns the buffers it must draw into. */
    public static MultiBufferSource beginEmissivePass(MultiBufferSource buffers) {
        emissivePass = true;
        return type -> {
            ResourceLocation texture = PrRenderTypes.texture(type);
            return texture != null && EMISSIVE_TEXTURES.contains(texture) ? buffers.getBuffer(type) : PrRenderTypes.DISCARD;
        };
    }

    public static boolean isEmissivePass() {
        return emissivePass;
    }

    // ------------------------------------------------------------------ texture mapping

    /** The texture an entity render type should bind instead of {@code texture}. */
    public static ResourceLocation remap(ResourceLocation texture) {
        if (entity == null && blockEntity == null) {
            return texture;
        }
        Data d = data;
        ResourceLocation out = modelTextureFrom != null && modelTextureFrom.equals(texture) ? modelTextureTo : texture;
        if (PryzmaConfig.prRandomEntities && !d.random().isEmpty()) {
            PrRandomProperties<ResourceLocation> props = d.random().get(out);
            if (props != null) {
                PrWorldInfo world = PrWorldInfo.client();
                out = entity != null
                        ? props.select(PrEntityInfos.entity(entity), world, out, null)
                        : props.select(PrEntityInfos.blockEntity(blockEntity), world, out, null);
            }
        }
        if (d.emissiveSuffix() != null && PryzmaConfig.prEmissiveTextures) {
            ResourceLocation emissive = emissiveOf(out, d);
            if (emissivePass) {
                return emissive != null ? emissive : out;
            }
            if (emissive != null) {
                sawEmissive = true;
            }
        }
        return out;
    }

    /** {@code <path><suffix>.png} when that resource exists; an emissive texture maps to itself. */
    static ResourceLocation emissiveOf(ResourceLocation texture, Data d) {
        if (EMISSIVE_TEXTURES.contains(texture)) {
            return texture;
        }
        return EMISSIVE.computeIfAbsent(texture, tex -> {
            String path = tex.getPath();
            if (!path.endsWith(".png") || d.manager() == null) {
                return Optional.empty();
            }
            ResourceLocation candidate = tex.withPath(path.substring(0, path.length() - 4) + d.emissiveSuffix() + ".png");
            if (d.manager().getResource(candidate).isEmpty()) {
                return Optional.empty();
            }
            EMISSIVE_TEXTURES.add(candidate);
            return Optional.of(candidate);
        }).orElse(null);
    }
}
