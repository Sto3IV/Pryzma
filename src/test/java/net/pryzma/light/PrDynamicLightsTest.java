package net.pryzma.light;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.pryzma.PryzmaConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrDynamicLightsTest {
    private int originalDynamicLights;

    @BeforeEach
    void setUp() {
        originalDynamicLights = PryzmaConfig.prDynamicLights;
        PrDynamicLights.clear();
    }

    @AfterEach
    void tearDown() {
        PryzmaConfig.prDynamicLights = originalDynamicLights;
        PrDynamicLights.clear();
    }

    @Test
    void configState() {
        PryzmaConfig.prDynamicLights = 3; // OFF
        assertFalse(PrDynamicLights.isEnabled());
        assertFalse(PrDynamicLights.isFancy());

        PryzmaConfig.prDynamicLights = 1; // Fast
        assertTrue(PrDynamicLights.isEnabled());
        assertFalse(PrDynamicLights.isFancy());

        PryzmaConfig.prDynamicLights = 2; // Fancy
        assertTrue(PrDynamicLights.isEnabled());
        assertTrue(PrDynamicLights.isFancy());
    }

    @Test
    void blendLightPreservesSkyLight() {
        // Sky light = 240 (0x00F00000), Block light = 0 (0x0000)
        int packed = 0x00F00000;
        int blended = PrDynamicLights.blendLight(15.0, packed);
        // 15.0 * 16 = 240 (0xF0) -> 0x00F000F0
        assertEquals(0x00F000F0, blended);

        // Sky light = 160 (0x00A00000), Block light = 32 (0x0020)
        packed = 0x00A00020;
        blended = PrDynamicLights.blendLight(8.0, packed);
        // 8.0 * 16 = 128 (0x80) > 32 (0x20) -> 0x00A00080
        assertEquals(0x00A00080, blended);

        // When vanilla block light is already brighter, dynamic light does not dim it
        packed = 0x00A000F0;
        blended = PrDynamicLights.blendLight(8.0, packed);
        assertEquals(0x00A000F0, blended);

        // When dynamic light is 0, unchanged
        packed = 0x00A00020;
        blended = PrDynamicLights.blendLight(0.0, packed);
        assertEquals(0x00A00020, blended);
    }

    @Test
    void levelRendererGetLightColorExists() throws Exception {
        java.lang.reflect.Method m1 = net.minecraft.client.renderer.LevelRenderer.class.getMethod(
                "getLightColor", net.minecraft.world.level.BlockAndTintGetter.class, net.minecraft.core.BlockPos.class);
        org.junit.jupiter.api.Assertions.assertNotNull(m1);
        java.lang.reflect.Method m2 = net.minecraft.client.renderer.LevelRenderer.class.getMethod(
                "getLightColor", net.minecraft.world.level.BlockAndTintGetter.class,
                net.minecraft.world.level.block.state.BlockState.class, net.minecraft.core.BlockPos.class);
        org.junit.jupiter.api.Assertions.assertNotNull(m2);
    }
}
