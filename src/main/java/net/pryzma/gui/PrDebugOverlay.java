package net.pryzma.gui;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.SharedConstants;
import net.minecraft.client.ClientBrandRetriever;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.inventory.InventoryMenu;
import net.neoforged.fml.ModList;
import net.pryzma.Pryzma;
import net.pryzma.PryzmaConfig;
import net.pryzma.light.PrDynamicLights;
import net.pryzma.mixin.TextureAtlasAccessor;
import net.pryzma.perf.PrDebugTracker;
import net.pryzma.render.PrAnimations;
import net.pryzma.shader.PrShaders;

/**
 * The Pryzma additions to the F3 screen, in the 1.x layout: the client brand; minimum FPS, section
 * updates and option flags on the FPS line; dynamic lights, version and shader pack after the
 * entity counts; the animation count after the particle counts; native and GPU memory under the
 * heap figures. Lines are found by content, so lines added by other mods do not shift them.
 */
public final class PrDebugOverlay {
    /** Vanilla runs the graphics mode into the frame limit or vsync: "T: inffancy". */
    private static final Pattern MODE_SPACING = Pattern.compile("(?<=[\\dfc])(?=fa)");
    private static String pryzmaVersion;

    private PrDebugOverlay() {
    }

    /** DebugScreenOverlay.getGameInformation, the left column. */
    public static void left(List<String> lines) {
        Minecraft mc = Minecraft.getInstance();
        int i = find(lines, "Minecraft ");
        if (i >= 0) {
            lines.set(i, brand(lines.get(i), ClientBrandRetriever.getClientModName()));
        }
        i = mc.fpsString.isEmpty() ? -1 : lines.indexOf(mc.fpsString);
        if (i >= 0) {
            lines.set(i, fpsLine(lines.get(i), PrDebugTracker.getFpsMin(), PrDebugTracker.getChunkUpdates(), flags()));
        }
        i = find(lines, "E: ");
        if (i >= 0) {
            lines.set(i, lines.get(i) + ", " + versionDebug(PrDynamicLights.isEnabled() ? PrDynamicLights.getSourceCount() : -1,
                    version(), PrShaders.enabled() ? PrShaders.selected() : null));
        }
        i = find(lines, "P: ");
        if (i >= 0) {
            List<TextureAtlasSprite.Ticker> tickers = ((TextureAtlasAccessor) mc.getModelManager()
                    .getAtlas(InventoryMenu.BLOCK_ATLAS)).prGetAnimatedTextures();
            lines.set(i, lines.get(i) + animations(PryzmaConfig.prSmartAnimations, PrAnimations.countRunning(tickers), tickers.size()));
        }
    }

    /** DebugScreenOverlay.getSystemInformation, the right column. */
    public static void right(List<String> lines) {
        insertMemory(lines,
                "Native: " + mb(PrQuickInfoStats.directBytes()) + "/" + mb(Runtime.getRuntime().maxMemory()) + "+"
                        + mb(PrQuickInfoStats.IMAGE_BYTES.get()) + "MB",
                "GPU: " + mb(PrDebugTracker.getGpuBufferBytes()) + "+" + mb(PrDebugTracker.getGpuTextureBytes()) + "MB");
    }

    /** "Minecraft 1.21.1 (1.21.1/neoforge)" becomes "Minecraft 1.21.1 (1.21.1/pryzma)". */
    static String brand(String line, String clientBrand) {
        int at = line.indexOf('/' + clientBrand);
        return at < 0 ? line : line.substring(0, at + 1) + Pryzma.MODID + line.substring(at + 1 + clientBrand.length());
    }

    /**
     * "1449 fps T: inffancy B: 2" becomes "1449/240 fps (35 updates) T: inf fancy B: 2" and the
     * flags. Edits run right to left, so every offset taken from {@code fps} still holds.
     */
    static String fpsLine(String fps, int fpsMin, int chunkUpdates, String flags) {
        StringBuilder sb = new StringBuilder(fps.length() + 32 + flags.length()).append(fps);
        int limit = fps.indexOf("T: ");
        if (limit >= 0) {
            Matcher spacing = MODE_SPACING.matcher(fps);
            if (spacing.find(limit)) {
                sb.insert(spacing.start(), ' ');
            }
            sb.insert(limit, "(" + chunkUpdates + " updates) ");
        }
        int unit = fps.indexOf(" fps ");
        if (unit >= 0 && (limit < 0 || unit < limit)) {
            sb.insert(unit, "/" + fpsMin);
        }
        return sb.append(flags).toString();
    }

    /** The 1.x flags: smooth FPS, fast render, anisotropic filtering, antialiasing, render regions, shaders. */
    private static String flags() {
        return (PryzmaConfig.prSmoothFps ? " sf" : "") + (PryzmaConfig.prFastRender ? " fr" : "")
                + (PryzmaConfig.prAfLevel > 1 ? " af" : "") + (PryzmaConfig.startupAaLevel() > 0 ? " aa" : "")
                + (PryzmaConfig.prRenderRegions ? " rr" : "") + (PrShaders.enabled() ? " sh" : "");
    }

    /** 1.x Config.getVersionDebug: {@code dynamicLights} is -1 while they are off, {@code shaderPack} null. */
    static String versionDebug(int dynamicLights, String version, String shaderPack) {
        StringBuilder sb = new StringBuilder(48);
        if (dynamicLights >= 0) {
            sb.append("DL: ").append(dynamicLights).append(", ");
        }
        sb.append(version);
        if (shaderPack != null) {
            sb.append(", ").append(shaderPack);
        }
        return sb.toString();
    }

    /** Animated block textures; under Smart Animations the running ones first, as 1.x. */
    static String animations(boolean smart, int running, int total) {
        return smart ? ", A: " + running + "/" + total : ", A: " + total;
    }

    /** Places the memory lines after "Allocated:" (after the fourth line without it), each only if absent. */
    static void insertMemory(List<String> lines, String nativeLine, String gpuLine) {
        int allocated = find(lines, "Allocated: ");
        int at = allocated >= 0 ? allocated + 1 : Math.min(4, lines.size());
        if (find(lines, "GPU: ") < 0) {
            lines.add(at, gpuLine);
        }
        if (find(lines, "Native: ") < 0) {
            lines.add(at, nativeLine);
        }
    }

    /** "Pryzma_1.21.1_2.0.0", the 1.x version format. */
    private static String version() {
        if (pryzmaVersion == null) {
            pryzmaVersion = "Pryzma_" + SharedConstants.getCurrentVersion().getName() + "_" + ModList.get()
                    .getModContainerById(Pryzma.MODID).map(c -> c.getModInfo().getVersion().toString()).orElse("dev");
        }
        return pryzmaVersion;
    }

    private static int find(List<String> lines, String prefix) {
        for (int i = 0, n = lines.size(); i < n; i++) {
            String line = lines.get(i);
            if (line != null && line.startsWith(prefix)) {
                return i;
            }
        }
        return -1;
    }

    private static long mb(long bytes) {
        return bytes >> 20;
    }
}
