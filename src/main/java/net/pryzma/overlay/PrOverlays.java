package net.pryzma.overlay;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
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
 *   <li>F9: Chunk Boundary Grid (Mode 0: OFF, Mode 1: Chunk Corners &amp; Center, Mode 2: Chunk Cage)</li>
 * </ul>
 * <p>
 * Renders cleanly via {@link Tesselator} and {@link BufferUploader#drawWithShader(MeshData)} with
 * {@link GameRenderer#getPositionColorShader()} and {@code depthMask(false)}, preventing self-depth
 * fighting, flickering, and viewing angle artifacts across all platforms and display resolutions.
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
    private static int chunkGridMode = 0; // 0 = OFF, 1 = CORNERS_AND_CENTER, 2 = CAGE

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
                    PrMobSpawnOverlay.tick(mc);
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

        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();
        double cameraX = camPos.x;
        double cameraY = camPos.y;
        double cameraZ = camPos.z;
        PoseStack.Pose pose = event.getPoseStack().last();

        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.lineWidth(1.0F);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buffer = tess.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

        if (lightOverlayActive) {
            PrMobSpawnOverlay.render(buffer, pose, camera, cameraX, cameraY, cameraZ, mc.player, mc.level);
        }

        if (chunkGridMode > 0) {
            PrChunkGridOverlay.render(buffer, pose, cameraX, cameraY, cameraZ, chunkGridMode, mc.player, mc.level);
        }

        MeshData meshData = buffer.build();
        if (meshData != null) {
            BufferUploader.drawWithShader(meshData);
        }

        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }
}
