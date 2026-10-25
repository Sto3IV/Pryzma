package net.pryzma.overlay;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * OptiFine / classic utility overlays:
 * <ul>
 *   <li>F7: Light Level / Mob Spawn Overlay (shows yellow / red crosses on blocks)</li>
 *   <li>F9: Chunk Boundary Grid (Mode 0: OFF, Mode 1: Chunk Corners, Mode 2: Chunks)</li>
 * </ul>
 */
public final class PrOverlays {
    public static final KeyMapping KEY_LIGHT_OVERLAY = new KeyMapping(
            "pr.key.light_overlay",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F7,
            "key.categories.misc"
    );

    public static final KeyMapping KEY_CHUNK_GRID = new KeyMapping(
            "pr.key.chunk_grid",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F9,
            "key.categories.misc"
    );

    private static boolean lightOverlayActive = false;
    private static int chunkGridMode = 0; // 0 = OFF, 1 = CURRENT_CHUNK, 2 = AREA_3X3

    private PrOverlays() {
    }

    public static boolean isLightOverlayActive() {
        return lightOverlayActive;
    }

    public static int getChunkGridMode() {
        return chunkGridMode;
    }

    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(KEY_LIGHT_OVERLAY);
        event.register(KEY_CHUNK_GRID);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }

        while (KEY_LIGHT_OVERLAY.consumeClick()) {
            if (mc.screen == null) {
                lightOverlayActive = !lightOverlayActive;
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                mc.gui.setOverlayMessage(
                        Component.translatable(lightOverlayActive ? "pr.overlay.light.on" : "pr.overlay.light.off"),
                        false
                );
                if (!lightOverlayActive) {
                    PrMobSpawnOverlay.clear();
                } else {
                    PrMobSpawnOverlay.markDirty();
                }
            }
        }

        while (KEY_CHUNK_GRID.consumeClick()) {
            if (mc.screen == null) {
                chunkGridMode = (chunkGridMode + 1) % 3;
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                mc.gui.setOverlayMessage(
                        Component.translatable("pr.overlay.chunk." + chunkGridMode),
                        false
                );
            }
        }

        if (lightOverlayActive) {
            PrMobSpawnOverlay.tick(mc);
        }
    }

    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }

        if (!lightOverlayActive && chunkGridMode == 0) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer vc = bufferSource.getBuffer(PrOverlayRenderType.OVERLAY_QUADS);

        if (lightOverlayActive) {
            PrMobSpawnOverlay.render(poseStack, vc);
        }

        if (chunkGridMode > 0) {
            PrChunkGridOverlay.render(poseStack, vc, chunkGridMode, mc.player, mc.level);
        }

        bufferSource.endBatch(PrOverlayRenderType.OVERLAY_QUADS);
        poseStack.popPose();
    }
}
