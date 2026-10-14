package net.pryzma.render;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.VanillaPackResources;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.InactiveProfiler;
import net.pryzma.PryzmaConfig;
import net.pryzma.mixin.MinecraftAccessor;

/**
 * Manages custom font routing, legacy MCPatcher / OptiFine HD bitmap font mapping,
 * resource pack font filtering when custom fonts are disabled, and live font reloading.
 */
public final class PrFontUtils {
    private PrFontUtils() {}

    /**
     * Resolves high-definition bitmap font textures (OptiFine / MCPatcher formats)
     * when Custom Fonts is enabled.
     *
     * <p>Checks {@code assets/<namespace>/optifine/font/...} and {@code assets/<namespace>/mcpatcher/font/...}.
     */
    public static ResourceLocation getHdFontLocation(ResourceLocation fontLoc, ResourceManager rm) {
        if (!PryzmaConfig.prCustomFonts || fontLoc == null || rm == null) {
            return fontLoc;
        }

        String path = fontLoc.getPath();
        String subPath;
        if (path.startsWith("textures/")) {
            subPath = path.substring("textures/".length());
        } else {
            subPath = path;
        }

        // 1. Check OptiFine path: optifine/...
        ResourceLocation optifineLoc = ResourceLocation.fromNamespaceAndPath(fontLoc.getNamespace(), "optifine/" + subPath);
        if (rm.getResource(optifineLoc).isPresent()) {
            return optifineLoc;
        }

        // 2. Check MCPatcher legacy path: mcpatcher/...
        ResourceLocation mcpatcherLoc = ResourceLocation.fromNamespaceAndPath(fontLoc.getNamespace(), "mcpatcher/" + subPath);
        if (rm.getResource(mcpatcherLoc).isPresent()) {
            return mcpatcherLoc;
        }

        return fontLoc;
    }

    /**
     * Opens an image stream for bitmap glyph providers.
     * <ul>
     *   <li>When Custom Fonts is ON: respects HD font overrides or active resource pack textures.</li>
     *   <li>When Custom Fonts is OFF: ignores pack overrides and forces genuine vanilla textures.</li>
     * </ul>
     */
    public static InputStream openFontImageStream(
        ResourceManager rm,
        ResourceLocation location,
        Operation<InputStream> original
    ) throws IOException {
        if (!PryzmaConfig.prCustomFonts) {
            try {
                List<Resource> stack = rm.getResourceStack(location);
                for (Resource res : stack) {
                    if (isVanillaPack(res)) {
                        return res.open();
                    }
                }
            } catch (Exception ignored) {
            }
            return original.call(rm, location);
        }

        ResourceLocation hdLoc = getHdFontLocation(location, rm);
        if (!hdLoc.equals(location)) {
            return rm.open(hdLoc);
        }
        return original.call(rm, location);
    }

    /**
     * Filters font definition resources when Custom Fonts is OFF,
     * stripping any font definitions originating from custom resource packs (e.g. TTF/OTF definitions).
     */
    public static List<Resource> filterCustomFontResources(List<Resource> resources) {
        if (resources == null || resources.isEmpty() || PryzmaConfig.prCustomFonts) {
            return resources;
        }

        List<Resource> filtered = new ArrayList<>();
        for (Resource res : resources) {
            if (isVanillaPack(res)) {
                filtered.add(res);
            }
        }
        return filtered;
    }

    /**
     * Determines whether a resource originates from vanilla or built-in non-custom sources.
     */
    public static boolean isVanillaPack(Resource resource) {
        if (resource == null) {
            return false;
        }
        if (resource.source() instanceof VanillaPackResources) {
            return true;
        }
        String packId = resource.sourcePackId();
        if (packId == null) {
            return false;
        }
        return "vanilla".equals(packId)
            || "mod_resources".equals(packId)
            || "programmer_art".equals(packId)
            || "high_contrast".equals(packId)
            || packId.startsWith("mod:");
    }

    /**
     * Asynchronously triggers a dynamic reload of the Minecraft FontManager without
     * reloading the entire texture manager or triggering an expensive texture atlas rebuild.
     */
    public static void reloadFonts() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        FontManager fontManager = ((MinecraftAccessor) mc).pryzma$getFontManager();
        if (fontManager == null) {
            return;
        }
        ResourceManager resourceManager = mc.getResourceManager();
        if (resourceManager == null) {
            return;
        }

        PreparableReloadListener.PreparationBarrier barrier = CompletableFuture::completedFuture;
        Executor background = Util.backgroundExecutor();
        fontManager.reload(barrier, resourceManager, InactiveProfiler.INSTANCE, InactiveProfiler.INSTANCE, background, mc);
    }
}
