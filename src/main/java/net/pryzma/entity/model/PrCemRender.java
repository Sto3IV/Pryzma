package net.pryzma.entity.model;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.pryzma.render.PrRenderTypes;

/**
 * Buffers of CEM parts that carry their own texture.
 *
 * <p>A part texture needs its own render type in the middle of a model. Requesting a second
 * shared-buffer type from the buffer source would end the batch the rest of the model is still
 * writing into, so part types are registered as fixed buffers of the main buffer source, which
 * coexist with the model's own (the technique enchantment glint layers use).
 *
 * <p>A model is also drawn by its render layers (eyes, overlays) with their own textures. A
 * textured part belongs to the model's own texture pass only: it is skipped whenever the render
 * type in use is not the first one the entity requested.
 */
public final class PrCemRender {
    private static final Set<RenderType> REGISTERED = new HashSet<>();

    private static MultiBufferSource buffers;
    private static RenderType firstType;
    private static RenderType lastType;
    private static boolean requesting;

    private PrCemRender() {
    }

    /** Scope of one entity or block entity render. */
    public record Scope(MultiBufferSource buffers, RenderType firstType, RenderType lastType) {
    }

    public static boolean isRendering() {
        return buffers != null;
    }

    public static Scope begin(MultiBufferSource source) {
        Scope saved = new Scope(buffers, firstType, lastType);
        buffers = source;
        firstType = null;
        lastType = null;
        return saved;
    }

    public static void end(Scope saved) {
        buffers = saved.buffers();
        firstType = saved.firstType();
        lastType = saved.lastType();
    }

    /** Called for every buffer request of the main buffer source. */
    public static void onBufferRequested(RenderType type) {
        if (buffers == null || requesting) {
            return;
        }
        if (firstType == null) {
            firstType = type;
        }
        lastType = type;
    }

    /** The consumer a part with {@code texture} draws into; a discarding one outside the model's own pass. */
    static VertexConsumer bufferFor(ResourceLocation texture, VertexConsumer fallback) {
        if (buffers == null) {
            return fallback;
        }
        if (firstType != null && lastType != firstType
                && !Objects.equals(PrRenderTypes.texture(lastType), PrRenderTypes.texture(firstType))) {
            return PrRenderTypes.DISCARD;
        }
        String kind = lastType == null ? "" : PrRenderTypes.name(lastType);
        RenderType type = kind.contains("translucent") ? RenderType.entityTranslucent(texture) : RenderType.entityCutoutNoCull(texture);
        Map<RenderType, ByteBufferBuilder> fixed = Minecraft.getInstance().renderBuffers().bufferSource().fixedBuffers;
        if (!fixed.containsKey(type)) {
            fixed.put(type, new ByteBufferBuilder(type.bufferSize()));
            REGISTERED.add(type);
        }
        requesting = true;
        try {
            return buffers.getBuffer(type);
        } finally {
            requesting = false;
        }
    }

    /** Frees the part buffers of the previous resource state. */
    static void releaseBuffers() {
        Map<RenderType, ByteBufferBuilder> fixed = Minecraft.getInstance().renderBuffers().bufferSource().fixedBuffers;
        for (RenderType type : REGISTERED) {
            ByteBufferBuilder builder = fixed.remove(type);
            if (builder != null) {
                builder.close();
            }
        }
        REGISTERED.clear();
    }
}
