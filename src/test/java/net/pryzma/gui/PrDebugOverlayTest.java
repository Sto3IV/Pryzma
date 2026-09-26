package net.pryzma.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.pryzma.PryzmaConfig;
import net.pryzma.render.PrAnimations;

class PrDebugOverlayTest {
    @Test
    void fpsLineReadsAs1x() {
        assertEquals("1449/240 fps (35 updates) T: inf fancy B: 2 GPU: 69%",
                PrDebugOverlay.fpsLine("1449 fps T: inffancy B: 2 GPU: 69%", 240, 35, ""));
    }

    @Test
    void fpsLineSeparatesTheGraphicsModeAndAppendsFlags() {
        assertEquals("60/58 fps (3 updates) T: 60 vsync fabulous fancy-clouds B: 2 sf rr",
                PrDebugOverlay.fpsLine("60 fps T: 60 vsyncfabulous fancy-clouds B: 2", 58, 3, " sf rr"));
        assertEquals("144/90 fps (0 updates) T: 144 fast B: 5", PrDebugOverlay.fpsLine("144 fps T: 144fast B: 5", 90, 0, ""));
        assertEquals("changed by a mod sh", PrDebugOverlay.fpsLine("changed by a mod", 1, 2, " sh"));
    }

    @Test
    void brandBecomesPryzma() {
        assertEquals("Minecraft 1.21.1 (1.21.1/pryzma)", PrDebugOverlay.brand("Minecraft 1.21.1 (1.21.1/neoforge)", "neoforge"));
        assertEquals("Minecraft 1.21.1 (1.21.1/pryzma/snapshot)",
                PrDebugOverlay.brand("Minecraft 1.21.1 (1.21.1/neoforge/snapshot)", "neoforge"));
        assertEquals("Minecraft 1.21.1 (1.21.1/other)", PrDebugOverlay.brand("Minecraft 1.21.1 (1.21.1/other)", "neoforge"));
    }

    @Test
    void versionDebugReadsAs1x() {
        assertEquals("DL: 7, Pryzma_1.21.1_2.0.0", PrDebugOverlay.versionDebug(7, "Pryzma_1.21.1_2.0.0", null));
        assertEquals("Pryzma_1.21.1_2.0.0, BSL_v8.2.09.zip", PrDebugOverlay.versionDebug(-1, "Pryzma_1.21.1_2.0.0", "BSL_v8.2.09.zip"));
    }

    @Test
    void animationCount() {
        assertEquals(", A: 135", PrDebugOverlay.animations(false, 120, 135));
        assertEquals(", A: 120/135", PrDebugOverlay.animations(true, 120, 135));
    }

    @Test
    void runningAnimationsFollowTheirSwitches() {
        TextureAtlasSprite.Ticker plain = new TextureAtlasSprite.Ticker() {
            @Override
            public void tickAndUpload() {
            }

            @Override
            public void close() {
            }
        };
        List<TextureAtlasSprite.Ticker> tickers = List.of(plain, new PrAnimations.SwitchedTicker(plain, PrAnimations.Kind.WATER),
                new PrAnimations.SwitchedTicker(plain, PrAnimations.Kind.LAVA));
        int water = PryzmaConfig.prAnimatedWater;
        int lava = PryzmaConfig.prAnimatedLava;
        try {
            PryzmaConfig.prAnimatedWater = 0;
            PryzmaConfig.prAnimatedLava = 0;
            assertEquals(3, PrAnimations.countRunning(tickers));
            PryzmaConfig.prAnimatedWater = 2;
            assertEquals(2, PrAnimations.countRunning(tickers));
        } finally {
            PryzmaConfig.prAnimatedWater = water;
            PryzmaConfig.prAnimatedLava = lava;
        }
    }

    @Test
    void memoryLinesGoUnderTheHeapFiguresOnce() {
        List<String> right = new ArrayList<>(List.of("Java: 21.0.5 64bit", "Mem: 30% 1234/4096MB", "Allocation rate: 012MB/s",
                "Allocated: 50% 2048MB", "", "CPU: 24x AMD Ryzen 9 5900X"));
        PrDebugOverlay.insertMemory(right, "Native: 1/2+3MB", "GPU: 4+5MB");
        assertEquals(List.of("Java: 21.0.5 64bit", "Mem: 30% 1234/4096MB", "Allocation rate: 012MB/s", "Allocated: 50% 2048MB",
                "Native: 1/2+3MB", "GPU: 4+5MB", "", "CPU: 24x AMD Ryzen 9 5900X"), right);
        PrDebugOverlay.insertMemory(right, "Native: 9/9+9MB", "GPU: 9+9MB");
        assertEquals(8, right.size());
    }

    /** DebugScreenOverlayMixin and TextureAtlasAccessor: a missing member fails silently or at world load. */
    @Test
    void hookedVanillaMembersExist() throws ReflectiveOperationException {
        assertEquals(List.class, DebugScreenOverlay.class.getDeclaredMethod("getGameInformation").getReturnType());
        assertEquals(List.class, DebugScreenOverlay.class.getDeclaredMethod("getSystemInformation").getReturnType());
        assertEquals(List.class, TextureAtlas.class.getDeclaredField("animatedTextures").getType());
    }
}
