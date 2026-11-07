package net.pryzma.entity.model;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.resources.Identifier;

/**
 * CEM part render helper for parts with custom textures.
 */
public final class PrCemRender {
    private PrCemRender() {
    }

    public static boolean isRendering() {
        return false;
    }

    static VertexConsumer bufferFor(Identifier texture, VertexConsumer fallback) {
        return fallback;
    }

    static void releaseBuffers() {
    }
}
