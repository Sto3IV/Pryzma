package net.pryzma.sky;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;
import net.pryzma.core.res.PrProperties;

class PrSkyLayerTest {
    private static PrSkyLayer layer(String text, List<String> warnings) {
        PrProperties p = PrProperties.parse(ResourceLocation.withDefaultNamespace("optifine/sky/world0/sky1.properties"), text);
        return PrSkyLayer.parse(p, 1, warnings::add).orElse(null);
    }

    @Test
    void clockTimesMapToTicks() {
        List<String> w = new ArrayList<>();
        assertEquals(0, PrSkyLayer.parseTime("6:00", w::add));
        assertEquals(12000, PrSkyLayer.parseTime("18:00", w::add));
        assertEquals(22750, PrSkyLayer.parseTime("4:45", w::add));
        assertEquals(18000, PrSkyLayer.parseTime("00:00", w::add));
        assertEquals(-1, PrSkyLayer.parseTime("25:00", w::add));
        assertEquals(1, w.size());
    }

    @Test
    void sunsetCloudLayerFromTheUsersPack() {
        List<String> w = new ArrayList<>();
        PrSkyLayer sky1 = layer("startFadeIn=18:00\nendFadeIn=18:45\nstartFadeOut=18:50\nendFadeOut=19:10\n"
                + "blend=add\nrotate=true\naxis=0.0 -0.2 0.0\nsource=./cloud2.png\n", w);
        assertTrue(w.isEmpty(), w.toString());
        assertEquals(ResourceLocation.withDefaultNamespace("optifine/sky/world0/cloud2.png"), sky1.texture);
        assertEquals(PrSkyBlend.ADD, sky1.blend);
        assertEquals(0.5F, sky1.fadeBrightness(12375), 1e-3);
        assertEquals(1.0F, sky1.fadeBrightness(12800), 1e-3);
        assertEquals(0.0F, sky1.fadeBrightness(6000), 1e-3);
        assertFalse(sky1.isActive(6000, 6000), "noon is inside the off window");
        assertTrue(sky1.isActive(12500, 12500));
    }

    @Test
    void missingStartFadeOutIsDerivedSymmetrically() {
        List<String> w = new ArrayList<>();
        // sky4 of the user's pack: stars from 17:30, full at 20:00, gone at 06:10
        PrSkyLayer stars = layer("startFadeIn=17:30\nendFadeIn=20:00\nendFadeOut=6:10\nblend=add\nsource=./starfield01.png\n", w);
        assertTrue(w.isEmpty(), w.toString());
        assertEquals(1.0F, stars.fadeBrightness(18000), 1e-3, "midnight");
        assertEquals(0.5F, stars.fadeBrightness(12750), 1e-3, "halfway through the 2.5 h fade in");
        assertEquals(0.0F, stars.fadeBrightness(6000), 1e-3, "noon");
        assertTrue(stars.fadeBrightness(23000) > 0.0F && stars.fadeBrightness(23000) < 1.0F, "fading out at 05:00");
    }

    @Test
    void alwaysOnLayerIsVisibleAtEveryTick() {
        PrSkyLayer always = layer("source=./a.png\n", new ArrayList<>());
        for (int t = 0; t < 24000; t += 250) {
            assertTrue(always.isActive(t, t), "tick " + t);
            assertEquals(1.0F, always.fadeBrightness(t), 1e-6);
        }
    }

    @Test
    void fadeTimesMustCoverTheDay() {
        List<String> w = new ArrayList<>();
        assertEquals(null, layer("startFadeIn=18:00\nendFadeIn=18:45\nstartFadeOut=18:30\nendFadeOut=19:10\n", w));
        assertFalse(w.isEmpty());
    }

    @Test
    void weatherFilter() {
        PrSkyLayer clear = layer("source=./a.png\n", new ArrayList<>());
        PrSkyLayer stormy = layer("source=./a.png\nweather=rain thunder\n", new ArrayList<>());
        assertEquals(1.0F, clear.weatherBrightness(0.0F, 0.0F), 1e-6);
        assertEquals(0.25F, clear.weatherBrightness(0.75F, 0.0F), 1e-6);
        assertEquals(0.0F, stormy.weatherBrightness(0.0F, 0.0F), 1e-6);
        assertEquals(1.0F, stormy.weatherBrightness(1.0F, 0.5F), 1e-6);
    }

    @Test
    void axisIsConvertedToOptifineSpace() {
        assertArrayEquals(new float[] {0.0F, -0.2F, -0.0F}, PrSkyLayer.parseAxis("0.0 -0.2 0.0", s -> { }));
        assertArrayEquals(new float[] {1.0F, 0.0F, 0.0F}, PrSkyLayer.parseAxis(null, s -> { }));
        assertArrayEquals(new float[] {1.0F, 0.0F, 0.0F}, PrSkyLayer.parseAxis("0 0 0", s -> { }), "zero axis falls back");
    }

    @Test
    void positionSmoothingConverges() {
        float v = 0.0F;
        for (int frame = 0; frame < 120; frame++) {
            v = PrSkyLayer.smooth(v, 1.0F, 1 / 60.0F, 1.0F);
        }
        assertTrue(v > 0.99F, "two seconds at 60 fps reach the target: " + v);
        assertEquals(1.0F, PrSkyLayer.smooth(0.0F, 1.0F, 2.0F, 1.0F), 1e-6, "a long frame jumps to the target");
    }

    @Test
    void blendNames() {
        assertEquals(PrSkyBlend.ADD, PrSkyBlend.parse(null));
        assertEquals(PrSkyBlend.SCREEN, PrSkyBlend.parse(" Screen "));
        assertEquals(PrSkyBlend.BURN, PrSkyBlend.parse("burn"));
        assertEquals(PrSkyBlend.ADD, PrSkyBlend.parse("unknown"));
    }
}
