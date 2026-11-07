package net.pryzma.render;

import java.util.Map;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.pryzma.mixin.RenderSetupAccessor;
import net.pryzma.mixin.RenderTypeAccessor;
import net.pryzma.mixin.TextureBindingDuck;

/** Render type introspection and a vertex sink that discards everything. */
public final class PrRenderTypes {
    private PrRenderTypes() {
    }

    /** The texture a render type binds, or {@code null} (atlas-less, multi-texture or custom types). */
    public static Identifier texture(RenderType type) {
        if (type instanceof RenderTypeAccessor acc) {
            RenderSetup setup = acc.prGetState();
            if ((Object) setup instanceof RenderSetupAccessor setupAcc) {
                Map<String, ?> textures = setupAcc.prGetTextures();
                if (textures != null) {
                    for (Object binding : textures.values()) {
                        if (binding instanceof TextureBindingDuck tb) {
                            return tb.prGetLocation();
                        }
                    }
                }
            }
        }
        return null;
    }

    /** The registered name of a render type, e.g. {@code entity_cutout_no_cull}. */
    public static String name(RenderType type) {
        if (type instanceof RenderTypeAccessor acc) {
            return acc.prGetName();
        }
        return "";
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
        public VertexConsumer setColor(int color) {
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
        public VertexConsumer setUv3(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            return this;
        }
    };
}
