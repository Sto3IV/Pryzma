package net.pryzma.lightmap;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.pryzma.Pryzma;

/**
 * The GUI draws with a white lightmap while the game's lightmap texture holds a custom lightmap, as OptiFine
 * does ({@code LightTexture.setAllowed(false)} around the GUI in {@code GameRenderer.render}).
 *
 * <p>Text, hotbar items and every other GUI element are drawn at full brightness and sample the lightmap's
 * (15, 15) texel. Vanilla keeps that texel white; a custom lightmap does not: it is full sky light plus full
 * block light from the pack's image, so at night a warm torch column over a dark sky row turns every label
 * orange. The texture keeps its last custom content after the world is left, so the title screen after a
 * night-time world needs the white lightmap too. Render thread only.
 */
public final class PrGuiLightmap {
    public static final ResourceLocation WHITE_LIGHTMAP = ResourceLocation.fromNamespaceAndPath(Pryzma.MODID, "dynamic/gui_lightmap");

    /** The lightmap texture was last written by the custom lightmap. */
    private static boolean customLightmap;
    /** Between the GUI's GuiGraphics creation and its final flush in GameRenderer.render. */
    private static boolean guiPhase;
    private static DynamicTexture white;

    private PrGuiLightmap() {
    }

    /** Each lightmap update in a level: whether the custom lightmap wrote the texture. */
    public static void onLevelLightmap(boolean custom) {
        customLightmap = custom;
    }

    public static void beginGui() {
        guiPhase = true;
    }

    public static void endGui() {
        guiPhase = false;
    }

    /** Whether {@code LightTexture.turnOnLightLayer} binds {@link #WHITE_LIGHTMAP} instead of the lightmap. */
    public static boolean replacesLightmap() {
        return guiPhase && customLightmap;
    }

    /**
     * {@code location} when the lightmap stays, else the white lightmap, created on first use. 16x16: the text
     * shaders read texel (15, 15) with {@code texelFetch}, which a smaller texture does not have.
     */
    public static ResourceLocation substitute(ResourceLocation location) {
        if (!replacesLightmap()) {
            return location;
        }
        if (white == null) {
            white = new DynamicTexture(16, 16, false);
            NativeImage pixels = white.getPixels();
            if (pixels != null) {
                pixels.fillRect(0, 0, 16, 16, 0xFFFFFFFF);
            }
            white.upload();
            Minecraft.getInstance().getTextureManager().register(WHITE_LIGHTMAP, white);
        }
        return WHITE_LIGHTMAP;
    }
}
