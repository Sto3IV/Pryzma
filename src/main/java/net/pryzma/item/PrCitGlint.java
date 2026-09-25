package net.pryzma.item;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexMultiConsumer;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.pryzma.render.PrRenderTypes;

/**
 * Custom enchantment layers. Every glint vanilla draws is requested from the main buffer source
 * as one of five glint render types; while an item with custom effects is being drawn, that
 * request is answered with the item's layers instead: one render type per rule, drawn like the
 * vanilla glint (equal depth, colour only) with the rule's texture, blend, scroll speed and
 * rotation. Layer types are fixed buffers of the main buffer source, drawn right after the
 * vanilla glints.
 */
public final class PrCitGlint {
    /** Strength steps: a layer's strength is baked into its render type, so batching survives. */
    static final int STEPS = 16;

    /** The vanilla glint types and how their custom counterparts are drawn. */
    enum Kind {
        GLINT(RenderStateShard.RENDERTYPE_GLINT_SHADER, false, false, false),
        GLINT_TRANSLUCENT(RenderStateShard.RENDERTYPE_GLINT_TRANSLUCENT_SHADER, false, true, false),
        ENTITY_GLINT(RenderStateShard.RENDERTYPE_ENTITY_GLINT_SHADER, true, true, false),
        ENTITY_GLINT_DIRECT(RenderStateShard.RENDERTYPE_ENTITY_GLINT_DIRECT_SHADER, true, false, false),
        ARMOR_ENTITY_GLINT(RenderStateShard.RENDERTYPE_ARMOR_ENTITY_GLINT_SHADER, true, false, true);

        final RenderStateShard.ShaderStateShard shader;
        /** Entity glints use vanilla's 0.16 texture scale where item glints use 8. */
        final boolean entity;
        final boolean itemTarget;
        final boolean viewOffset;

        Kind(RenderStateShard.ShaderStateShard shader, boolean entity, boolean itemTarget, boolean viewOffset) {
            this.shader = shader;
            this.entity = entity;
            this.itemTarget = itemTarget;
            this.viewOffset = viewOffset;
        }

        static Kind of(RenderType type) {
            if (type == RenderType.glint()) {
                return GLINT;
            }
            if (type == RenderType.entityGlint()) {
                return ENTITY_GLINT;
            }
            if (type == RenderType.glintTranslucent()) {
                return GLINT_TRANSLUCENT;
            }
            if (type == RenderType.entityGlintDirect()) {
                return ENTITY_GLINT_DIRECT;
            }
            if (type == RenderType.armorEntityGlint()) {
                return ARMOR_ENTITY_GLINT;
            }
            return null;
        }
    }

    /** The layers of one item, each with the level that selected it, and whether the vanilla glint stays. */
    record Effects(PrCitEntry[] entries, int[] levels, boolean vanilla) {
        static final Effects NONE = new Effects(new PrCitEntry[0], new int[0], false);
        static final Effects HIDE = new Effects(new PrCitEntry[0], new int[0], false);

        boolean isEmpty() {
            return entries.length == 0;
        }

        /** Strength of each layer now: all full when layered, by level when averaged, one at a time when cycling. */
        float[] strengths(PrCitGlobal global) {
            float[] out = new float[entries.length];
            switch (global.method()) {
                case LAYERED -> Arrays.fill(out, 1.0F);
                case AVERAGE -> {
                    int total = 0;
                    for (int level : levels) {
                        total += Math.max(level, 0);
                    }
                    for (int i = 0; i < out.length; i++) {
                        out[i] = total <= 0 ? 1.0F / out.length : Math.max(levels[i], 0) / (float) total;
                    }
                }
                case CYCLE -> {
                    float total = 0.0F;
                    for (PrCitEntry entry : entries) {
                        total += Math.max(entry.rule.duration, 0.01F);
                    }
                    float t = (Util.getMillis() % (long) Math.max(1.0F, total * 1000.0F)) / 1000.0F;
                    float start = 0.0F;
                    for (int i = 0; i < out.length; i++) {
                        float duration = Math.max(entries[i].rule.duration, 0.01F);
                        if (t >= start && t < start + duration) {
                            float local = t - start;
                            float fade = global.fade();
                            out[i] = fade <= 0.0F ? 1.0F : Mth.clamp(Math.min(local, duration - local) / fade, 0.0F, 1.0F);
                        }
                        start += duration;
                    }
                }
            }
            return out;
        }
    }

    /** The item being drawn, set by the item, armor and elytra hooks. */
    static ItemStack stack;
    private static boolean bypass;
    private static final List<RenderType> REGISTERED = new ArrayList<>();
    private static final Map<PrCitBlend, RenderStateShard.TransparencyStateShard> BLENDS = new EnumMap<>(PrCitBlend.class);

    private PrCitGlint() {
    }

    /** Whether a buffer request is the glint code's own and must pass through untouched. */
    public static boolean bypassing() {
        return bypass;
    }

    /** The consumer answering a request for {@code type}, or {@code null} to let vanilla answer. */
    public static VertexConsumer redirect(MultiBufferSource.BufferSource source, RenderType type) {
        if (bypass || stack == null) {
            return null;
        }
        Kind kind = Kind.of(type);
        if (kind == null || source != Minecraft.getInstance().renderBuffers().bufferSource()) {
            return null;
        }
        Effects effects = PrCit.effects(stack);
        if (effects == null) {
            return null;
        }
        if (effects.isEmpty()) {
            return PrRenderTypes.DISCARD;
        }
        float[] strengths = effects.strengths(PrCit.global());
        List<VertexConsumer> out = new ArrayList<>(effects.entries().length + 1);
        bypass = true;
        try {
            if (effects.vanilla()) {
                out.add(source.getBuffer(type));
            }
            for (int i = 0; i < strengths.length; i++) {
                int step = Math.round(strengths[i] * STEPS);
                RenderType layer = step <= 0 ? null : layer(effects.entries()[i], kind, step);
                if (layer != null) {
                    if (!source.fixedBuffers.containsKey(layer)) {
                        source.fixedBuffers.put(layer, new ByteBufferBuilder(layer.bufferSize()));
                        REGISTERED.add(layer);
                    }
                    out.add(source.getBuffer(layer));
                }
            }
        } finally {
            bypass = false;
        }
        return switch (out.size()) {
            case 0 -> PrRenderTypes.DISCARD;
            case 1 -> out.get(0);
            default -> VertexMultiConsumer.create(out.toArray(new VertexConsumer[0]));
        };
    }

    /** Draws the custom layers; called right after vanilla draws its last glint type. */
    public static void flush(MultiBufferSource.BufferSource source) {
        if (REGISTERED.isEmpty() || source != Minecraft.getInstance().renderBuffers().bufferSource()) {
            return;
        }
        for (RenderType layer : REGISTERED) {
            source.endBatch(layer);
        }
    }

    /** Frees the layer buffers of the previous resource state. */
    static void release() {
        MultiBufferSource.BufferSource main = Minecraft.getInstance().renderBuffers().bufferSource();
        for (RenderType layer : REGISTERED) {
            ByteBufferBuilder builder = main.fixedBuffers.remove(layer);
            if (builder != null) {
                builder.close();
            }
        }
        REGISTERED.clear();
    }

    private static RenderType layer(PrCitEntry entry, Kind kind, int step) {
        if (entry.plainTexture == null) {
            return null;
        }
        if (entry.glintTypes == null) {
            entry.glintTypes = new RenderType[Kind.values().length * (STEPS + 1)];
        }
        int slot = kind.ordinal() * (STEPS + 1) + step;
        RenderType type = entry.glintTypes[slot];
        if (type == null) {
            type = create(entry, kind, step);
            entry.glintTypes[slot] = type;
        }
        return type;
    }

    private static RenderType create(PrCitEntry entry, Kind kind, int step) {
        PrCitRule rule = entry.rule;
        String name = "pryzma_cit_glint " + rule.location + " " + kind + " " + step;
        float scale = entry.width / 2.0F * (kind.entity ? 0.02F : 1.0F);
        float[] color = rule.blend.color(step / (float) STEPS);
        RenderType.CompositeState.CompositeStateBuilder state = RenderType.CompositeState.builder()
                .setShaderState(kind.shader)
                .setTextureState(new RenderStateShard.TextureStateShard(entry.plainTexture, rule.blur, false))
                .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                .setCullState(RenderStateShard.NO_CULL)
                .setDepthTestState(RenderStateShard.EQUAL_DEPTH_TEST)
                .setTransparencyState(BLENDS.computeIfAbsent(rule.blend, PrCitGlint::transparency))
                .setTexturingState(new RenderStateShard.TexturingStateShard(name,
                        () -> setup(rule, scale, color), PrCitGlint::clear));
        if (kind.itemTarget) {
            state.setOutputState(RenderStateShard.ITEM_ENTITY_TARGET);
        }
        if (kind.viewOffset) {
            state.setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING);
        }
        return RenderType.create(name, DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS, 1536,
                state.createCompositeState(false));
    }

    /** OptiFine's layer matrix: scaled by the texture width, scrolled by {@code speed}, turned by {@code rotation}. */
    private static void setup(PrCitRule rule, float scale, float[] color) {
        float offset = rule.speed * (Util.getMillis() % 3000L) / 3000.0F / 8.0F;
        RenderSystem.setTextureMatrix(new Matrix4f().scale(scale).translate(offset, 0.0F, 0.0F)
                .rotateZ(rule.rotation * Mth.DEG_TO_RAD));
        RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
    }

    private static void clear() {
        RenderSystem.resetTextureMatrix();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static RenderStateShard.TransparencyStateShard transparency(PrCitBlend blend) {
        return new RenderStateShard.TransparencyStateShard("pryzma_cit_" + blend.name().toLowerCase(Locale.ROOT), () -> {
            if (blend == PrCitBlend.REPLACE) {
                RenderSystem.disableBlend();
            } else {
                RenderSystem.enableBlend();
                RenderSystem.blendFunc(blend.src, blend.dst);
            }
        }, () -> {
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
        });
    }
}
