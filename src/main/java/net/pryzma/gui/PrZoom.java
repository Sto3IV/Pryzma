package net.pryzma.gui;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * OptiFine zoom: while the key (C) is held with no screen open, the field of view shrinks four
 * times and the smooth camera is on; releasing it restores the player's own smooth camera.
 */
public final class PrZoom {
    public static final KeyMapping KEY = new KeyMapping("pr.key.zoom", GLFW.GLFW_KEY_C, "key.categories.misc");

    private static boolean zooming;
    private static boolean savedSmoothCamera;

    private PrZoom() {
    }

    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(KEY);
    }

    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null && KEY.isDown()) {
            if (!zooming) {
                zooming = true;
                savedSmoothCamera = mc.options.smoothCamera;
                mc.options.smoothCamera = true;
                mc.levelRenderer.needsUpdate();
            }
            event.setFOV(event.getFOV() / 4.0);
        } else if (zooming) {
            zooming = false;
            mc.options.smoothCamera = savedSmoothCamera;
            mc.levelRenderer.needsUpdate();
        }
    }
}
