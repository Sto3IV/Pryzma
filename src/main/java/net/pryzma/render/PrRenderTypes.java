package net.pryzma.render;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.pryzma.mixin.TextureStateShardAccessor;

/** Render type introspection and a vertex sink that discards everything. */
public final class PrRenderTypes {
    private PrRenderTypes() {
    }

    /** The texture a composite render type binds, or {@code null} (atlas-less, multi-texture or custom types). */
    public static ResourceLocation texture(RenderType type) {
        if (type instanceof RenderType.CompositeRenderType composite) {
            return ((TextureStateShardAccessor) composite.state().textureState).prCutoutTexture().orElse(null);
        }
        return null;
    }

    /** The registered name of a render type, e.g. {@code entity_cutout_no_cull}. */
    public static String name(RenderType type) {
        return type.name;
    }

    /** Accepts vertices and draws nothing. */
    public static final VertexConsumer DISCARD = new VertexConsumer() {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }
    };
}
