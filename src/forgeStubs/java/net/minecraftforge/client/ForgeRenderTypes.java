package net.minecraftforge.client;

import java.lang.reflect.Field;
import java.util.function.Function;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Stub for Forge's extra text render types. Must not call {@link RenderType#text}
 * (Pryzma's patched method delegates back here).
 */
public final class ForgeRenderTypes {
    private ForgeRenderTypes() {
    }

    public static RenderType getText(ResourceLocation loc) {
        return apply("TEXT", loc);
    }

    public static RenderType getTextIntensity(ResourceLocation loc) {
        return apply("TEXT_INTENSITY", loc);
    }

    public static RenderType getTextPolygonOffset(ResourceLocation loc) {
        return apply("TEXT_POLYGON_OFFSET", loc);
    }

    public static RenderType getTextIntensityPolygonOffset(ResourceLocation loc) {
        return apply("TEXT_INTENSITY_POLYGON_OFFSET", loc);
    }

    public static RenderType getTextSeeThrough(ResourceLocation loc) {
        return apply("TEXT_SEE_THROUGH", loc);
    }

    public static RenderType getTextIntensitySeeThrough(ResourceLocation loc) {
        return apply("TEXT_INTENSITY_SEE_THROUGH", loc);
    }

    @SuppressWarnings("unchecked")
    private static RenderType apply(String field, ResourceLocation loc) {
        try {
            Field f = RenderType.class.getDeclaredField(field);
            f.setAccessible(true);
            Function<ResourceLocation, RenderType> fn = (Function<ResourceLocation, RenderType>) f.get(null);
            return fn.apply(loc);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("ForgeRenderTypes stub missing RenderType." + field, e);
        }
    }
}
