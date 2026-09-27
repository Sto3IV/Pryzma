package net.pryzma.lightmap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

class PrGuiLightmapTest {
    private static final ResourceLocation LIGHTMAP = ResourceLocation.withDefaultNamespace("dynamic/light_map_1");

    @Test
    void worldAlwaysKeepsTheLightmap() {
        PrGuiLightmap.onLevelLightmap(true);
        PrGuiLightmap.endGui();
        assertFalse(PrGuiLightmap.replacesLightmap(), "the world must see the custom lightmap");
        assertSame(LIGHTMAP, PrGuiLightmap.substitute(LIGHTMAP));
    }

    @Test
    void guiReplacesOnlyACustomLightmap() {
        PrGuiLightmap.beginGui();
        PrGuiLightmap.onLevelLightmap(false);
        assertFalse(PrGuiLightmap.replacesLightmap(), "vanilla's lightmap keeps (15, 15) white itself");
        assertSame(LIGHTMAP, PrGuiLightmap.substitute(LIGHTMAP));
        PrGuiLightmap.onLevelLightmap(true);
        assertTrue(PrGuiLightmap.replacesLightmap());
        PrGuiLightmap.endGui();
    }

    /** No level (title screen): nothing updates the texture, which still holds the last world's custom lightmap. */
    @Test
    void customStateOutlivesTheWorld() {
        PrGuiLightmap.onLevelLightmap(true);
        PrGuiLightmap.endGui();
        PrGuiLightmap.beginGui();
        assertTrue(PrGuiLightmap.replacesLightmap());
        PrGuiLightmap.endGui();
        assertFalse(PrGuiLightmap.replacesLightmap());
    }
}
