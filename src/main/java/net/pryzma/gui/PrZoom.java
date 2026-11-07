package net.pryzma.gui;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * OptiFine zoom: while the key (C) is held with no screen open, the field of view shrinks four
 * times and the smooth camera is on; releasing it restores the player's own smooth camera.
 */
public final class PrZoom {
    public static final KeyMapping KEY = new KeyMapping("pr.key.zoom", InputConstants.KEY_C, KeyMapping.Category.MISC);

    private static boolean zooming;
    private static boolean savedSmoothCamera;

    private PrZoom() {
    }

    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(KEY);
    }

    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() == null && KEY.isDown()) {
            if (!zooming) {
                zooming = true;
                savedSmoothCamera = mc.options.smoothCamera;
                mc.options.smoothCamera = true;
            }
            event.setFOV((float) (event.getFOV() / 4.0));
        } else if (zooming) {
            zooming = false;
            mc.options.smoothCamera = savedSmoothCamera;
        }
    }

    public static boolean isZooming() {
        return zooming;
    }

    public static float zoomFactor() {
        return zooming ? 4.0F : 1.0F;
    }
}
