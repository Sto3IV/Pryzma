package net.pryzma;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pryzma video settings. Field names, defaults, value ranges and the {@code optionspr.txt}
 * format are those of Pryzma 1.x, so an existing settings file loads unchanged.
 *
 * <p>Fields are plain statics: render code reads them on hot paths and they are only ever
 * written from the client thread (GUI or load).
 */
public final class PryzmaConfig {
    public static final String FILE_NAME = "optionspr.txt";

    // Option value ids shared by the Quick Info options (Pryzma 1.x OptionValueInt ids).
    public static final int VALUE_OFF = 3;
    public static final int VALUE_COMPACT = 5;
    public static final int VALUE_FULL = 6;
    public static final int VALUE_DETAILED = 7;

    public static final int[] TREES_VALUES = {0, 1, 4, 2};
    public static final int[] DYNAMIC_LIGHTS_VALUES = {3, 1, 2};
    public static final int[] TELEMETRY_VALUES = {0, 1, 2};

    public static int prFogType = 1;
    public static float prFogStart = 0.8F;
    public static int prMipmapType = 0;
    public static boolean prOcclusionFancy = false;
    public static boolean prSmoothFps = false;
    public static boolean prSmoothWorld = isSingleProcessor();
    public static boolean prLazyChunkLoading = isSingleProcessor();
    public static boolean prRenderRegions = false;
    public static boolean prSmartAnimations = false;
    public static double prAoLevel = 1.0;
    public static int prAaLevel = 0;
    public static int prAfLevel = 1;
    public static int prClouds = 0;
    public static double prCloudsHeight = 0.0;
    public static int prTrees = 0;
    public static int prRain = 0;
    public static int prBetterGrass = 3;
    public static int prAutoSaveTicks = 4000;
    public static boolean prLagometer = false;
    public static boolean prProfiler = false;
    public static boolean prWeather = true;
    public static boolean prSky = true;
    public static boolean prStars = true;
    public static boolean prSunMoon = true;
    public static int prVignette = 0;
    public static int prChunkUpdates = 1;
    public static int prTime = 0;
    public static boolean prBetterSnow = false;
    public static boolean prSwampColors = true;
    public static boolean prRandomEntities = true;
    public static boolean prCustomFonts = true;
    public static boolean prCustomColors = true;
    public static boolean prCustomSky = true;
    public static boolean prShowCapes = true;
    public static int prConnectedTextures = 2;
    public static boolean prCustomItems = true;
    public static boolean prNaturalTextures = false;
    public static boolean prEmissiveTextures = true;
    public static boolean prFastMath = false;
    public static boolean prFastRender = false;
    public static boolean prDynamicFov = true;
    public static boolean prAlternateBlocks = true;
    public static int prDynamicLights = 3;
    public static boolean prCustomEntityModels = true;
    public static boolean prCustomGuis = true;
    public static boolean prShowGlErrors = true;
    public static int prScreenshotSize = 1;
    public static int prChatBackground = 0;
    public static boolean prChatShadow = true;
    public static int prTelemetry = 0;
    public static boolean prHeldItemTooltips = true;
    public static int prAnimatedWater = 0;
    public static int prAnimatedLava = 0;
    public static boolean prAnimatedFire = true;
    public static boolean prAnimatedPortal = true;
    public static boolean prAnimatedRedstone = true;
    public static boolean prAnimatedExplosion = true;
    public static boolean prAnimatedFlame = true;
    public static boolean prAnimatedSmoke = true;
    public static boolean prVoidParticles = true;
    public static boolean prWaterParticles = true;
    public static boolean prRainSplash = true;
    public static boolean prPortalParticles = true;
    public static boolean prPotionParticles = true;
    public static boolean prFireworkParticles = true;
    public static boolean prDrippingWaterLava = true;
    public static boolean prAnimatedTerrain = true;
    public static boolean prAnimatedTextures = true;
    public static boolean prFeedbackButtons = false;
    public static boolean prFastPaintings = false;

    public static boolean prQuickInfo = false;
    public static int prQuickInfoFps = VALUE_FULL;
    public static boolean prQuickInfoChunks = true;
    public static boolean prQuickInfoEntities = true;
    public static boolean prQuickInfoParticles = false;
    public static boolean prQuickInfoUpdates = true;
    public static boolean prQuickInfoGpu = false;
    public static int prQuickInfoPos = VALUE_COMPACT;
    public static int prQuickInfoFacing = VALUE_OFF;
    public static boolean prQuickInfoBiome = false;
    public static boolean prQuickInfoLight = false;
    public static int prQuickInfoMemory = VALUE_OFF;
    public static int prQuickInfoNativeMemory = VALUE_OFF;
    public static int prQuickInfoTargetBlock = VALUE_OFF;
    public static int prQuickInfoTargetFluid = VALUE_OFF;
    public static int prQuickInfoTargetEntity = VALUE_OFF;
    public static int prQuickInfoLabels = VALUE_COMPACT;
    public static boolean prQuickInfoBackground = false;

    private static Path gameDir;
    private static int startupAaLevel;

    private PryzmaConfig() {
    }

    public static boolean isSingleProcessor() {
        return Runtime.getRuntime().availableProcessors() <= 1;
    }

    // ---------------------------------------------------------------- derived queries

    public static boolean isConnectedTextures() {
        return prConnectedTextures != 3;
    }

    public static boolean isConnectedTexturesFancy() {
        return prConnectedTextures == 2;
    }

    public static boolean isBetterGrass() {
        return prBetterGrass != 3;
    }

    public static boolean isBetterGrassFancy() {
        return prBetterGrass == 2;
    }

    public static boolean isFogOff() {
        return prFogType == 3;
    }

    // ---------------------------------------------------------------- persistence

    /** Anti-aliasing level the game started with; a different stored level needs a restart. */
    public static int startupAaLevel() {
        return startupAaLevel;
    }

    /** Loads {@code optionspr.txt} from {@code dir}, falling back to the 1.x and OptiFine files. */
    public static void load(Path dir) {
        loadFile(dir);
        startupAaLevel = prAaLevel;
    }

    private static void loadFile(Path dir) {
        gameDir = dir;
        Path file = dir.resolve(FILE_NAME);
        String legacyPrefix = null;
        if (!Files.isRegularFile(file)) {
            if (Files.isRegularFile(dir.resolve("optionspryzma.txt"))) {
                file = dir.resolve("optionspryzma.txt");
                legacyPrefix = "pryzma";
            } else if (Files.isRegularFile(dir.resolve("optionsof.txt"))) {
                file = dir.resolve("optionsof.txt");
                legacyPrefix = "of";
            } else {
                return;
            }
        }
        Map<String, String> values = new LinkedHashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                int colon = line.indexOf(':');
                if (colon <= 0) {
                    continue;
                }
                String key = line.substring(0, colon).trim();
                if (legacyPrefix != null && key.startsWith(legacyPrefix) && key.length() > legacyPrefix.length()) {
                    key = "pr" + key.substring(legacyPrefix.length());
                }
                values.put(key, line.substring(colon + 1).trim());
            }
        } catch (IOException e) {
            Pryzma.LOGGER.warn("Failed to read {}", file, e);
            return;
        }
        apply(values);
        Pryzma.LOGGER.info("Loaded {} Pryzma options from {}", values.size(), file.getFileName());
    }

    /** Applies parsed {@code key -> value} pairs with the 1.x clamping rules. Unknown keys are ignored. */
    static void apply(Map<String, String> v) {
        prFogType = clampInt(v, "prFogType", prFogType, 2, 3);
        if (v.containsKey("prFogStart")) {
            float f = parseFloat(v.get("prFogStart"), prFogStart);
            prFogStart = f < 0.2F ? 0.2F : f > 0.81F ? 0.8F : f;
        }
        prMipmapType = clampInt(v, "prMipmapType", prMipmapType, 0, 3);
        prOcclusionFancy = bool(v, "prOcclusionFancy", prOcclusionFancy);
        prSmoothFps = bool(v, "prSmoothFps", prSmoothFps);
        prSmoothWorld = bool(v, "prSmoothWorld", prSmoothWorld);
        prAoLevel = clampDouble(v, "prAoLevel", prAoLevel);
        prClouds = clampInt(v, "prClouds", prClouds, 0, 3);
        prCloudsHeight = clampDouble(v, "prCloudsHeight", prCloudsHeight);
        prTrees = oneOf(v, "prTrees", prTrees, TREES_VALUES);
        prRain = clampInt(v, "prRain", prRain, 0, 3);
        prAnimatedWater = clampInt(v, "prAnimatedWater", prAnimatedWater, 0, 2);
        prAnimatedLava = clampInt(v, "prAnimatedLava", prAnimatedLava, 0, 2);
        prAnimatedFire = bool(v, "prAnimatedFire", prAnimatedFire);
        prAnimatedPortal = bool(v, "prAnimatedPortal", prAnimatedPortal);
        prAnimatedRedstone = bool(v, "prAnimatedRedstone", prAnimatedRedstone);
        prAnimatedExplosion = bool(v, "prAnimatedExplosion", prAnimatedExplosion);
        prAnimatedFlame = bool(v, "prAnimatedFlame", prAnimatedFlame);
        prAnimatedSmoke = bool(v, "prAnimatedSmoke", prAnimatedSmoke);
        prVoidParticles = bool(v, "prVoidParticles", prVoidParticles);
        prWaterParticles = bool(v, "prWaterParticles", prWaterParticles);
        prPortalParticles = bool(v, "prPortalParticles", prPortalParticles);
        prPotionParticles = bool(v, "prPotionParticles", prPotionParticles);
        prFireworkParticles = bool(v, "prFireworkParticles", prFireworkParticles);
        prDrippingWaterLava = bool(v, "prDrippingWaterLava", prDrippingWaterLava);
        prAnimatedTerrain = bool(v, "prAnimatedTerrain", prAnimatedTerrain);
        prAnimatedTextures = bool(v, "prAnimatedTextures", prAnimatedTextures);
        prRainSplash = bool(v, "prRainSplash", prRainSplash);
        prLagometer = bool(v, "prLagometer", prLagometer);
        prAutoSaveTicks = clampInt(v, "prAutoSaveTicks", prAutoSaveTicks, 40, 40000);
        prBetterGrass = clampInt(v, "prBetterGrass", prBetterGrass, 1, 3);
        prConnectedTextures = clampInt(v, "prConnectedTextures", prConnectedTextures, 1, 3);
        prWeather = bool(v, "prWeather", prWeather);
        prSky = bool(v, "prSky", prSky);
        prStars = bool(v, "prStars", prStars);
        prSunMoon = bool(v, "prSunMoon", prSunMoon);
        prVignette = clampInt(v, "prVignette", prVignette, 0, 2);
        prChunkUpdates = clampInt(v, "prChunkUpdates", prChunkUpdates, 1, 5);
        prTime = clampInt(v, "prTime", prTime, 0, 2);
        prAaLevel = clampInt(v, "prAaLevel", prAaLevel, 0, 16);
        prAfLevel = clampInt(v, "prAfLevel", prAfLevel, 1, 16);
        prProfiler = bool(v, "prProfiler", prProfiler);
        prBetterSnow = bool(v, "prBetterSnow", prBetterSnow);
        prSwampColors = bool(v, "prSwampColors", prSwampColors);
        prRandomEntities = bool(v, "prRandomEntities", prRandomEntities);
        prCustomFonts = bool(v, "prCustomFonts", prCustomFonts);
        prCustomColors = bool(v, "prCustomColors", prCustomColors);
        prCustomItems = bool(v, "prCustomItems", prCustomItems);
        prCustomSky = bool(v, "prCustomSky", prCustomSky);
        prShowCapes = bool(v, "prShowCapes", prShowCapes);
        prNaturalTextures = bool(v, "prNaturalTextures", prNaturalTextures);
        prEmissiveTextures = bool(v, "prEmissiveTextures", prEmissiveTextures);
        prLazyChunkLoading = bool(v, "prLazyChunkLoading", prLazyChunkLoading);
        prRenderRegions = bool(v, "prRenderRegions", prRenderRegions);
        prSmartAnimations = bool(v, "prSmartAnimations", prSmartAnimations);
        prDynamicFov = bool(v, "prDynamicFov", prDynamicFov);
        prAlternateBlocks = bool(v, "prAlternateBlocks", prAlternateBlocks);
        prDynamicLights = oneOf(v, "prDynamicLights", prDynamicLights, DYNAMIC_LIGHTS_VALUES);
        prScreenshotSize = clampInt(v, "prScreenshotSize", prScreenshotSize, 1, 4);
        prCustomEntityModels = bool(v, "prCustomEntityModels", prCustomEntityModels);
        prCustomGuis = bool(v, "prCustomGuis", prCustomGuis);
        prShowGlErrors = bool(v, "prShowGlErrors", prShowGlErrors);
        prFastMath = bool(v, "prFastMath", prFastMath);
        prFastRender = bool(v, "prFastRender", prFastRender);
        if (v.containsKey("prChatBackground")) {
            prChatBackground = parseInt(v.get("prChatBackground"), prChatBackground);
        }
        prChatShadow = bool(v, "prChatShadow", prChatShadow);
        prTelemetry = oneOf(v, "prTelemetry", prTelemetry, TELEMETRY_VALUES);
        prFeedbackButtons = bool(v, "prFeedbackButtons", prFeedbackButtons);
        prFastPaintings = bool(v, "prFastPaintings", prFastPaintings);
        prHeldItemTooltips = bool(v, "prHeldItemTooltips", prHeldItemTooltips);

        prQuickInfo = bool(v, "prQuickInfo", prQuickInfo);
        prQuickInfoFps = quickValue(v, "prQuickInfoFps", prQuickInfoFps, false);
        prQuickInfoChunks = bool(v, "prQuickInfoChunks", prQuickInfoChunks);
        prQuickInfoEntities = bool(v, "prQuickInfoEntities", prQuickInfoEntities);
        prQuickInfoParticles = bool(v, "prQuickInfoParticles", prQuickInfoParticles);
        prQuickInfoUpdates = bool(v, "prQuickInfoUpdates", prQuickInfoUpdates);
        prQuickInfoGpu = bool(v, "prQuickInfoGpu", prQuickInfoGpu);
        prQuickInfoPos = quickValue(v, "prQuickInfoPos", prQuickInfoPos, false);
        prQuickInfoFacing = quickValue(v, "prQuickInfoFacing", prQuickInfoFacing, false);
        prQuickInfoBiome = bool(v, "prQuickInfoBiome", prQuickInfoBiome);
        prQuickInfoLight = bool(v, "prQuickInfoLight", prQuickInfoLight);
        prQuickInfoMemory = quickValue(v, "prQuickInfoMemory", prQuickInfoMemory, false);
        prQuickInfoNativeMemory = quickValue(v, "prQuickInfoNativeMemory", prQuickInfoNativeMemory, false);
        prQuickInfoTargetBlock = quickValue(v, "prQuickInfoTargetBlock", prQuickInfoTargetBlock, false);
        prQuickInfoTargetFluid = quickValue(v, "prQuickInfoTargetFluid", prQuickInfoTargetFluid, false);
        prQuickInfoTargetEntity = quickValue(v, "prQuickInfoTargetEntity", prQuickInfoTargetEntity, false);
        prQuickInfoLabels = quickValue(v, "prQuickInfoLabels", prQuickInfoLabels, true);
        prQuickInfoBackground = bool(v, "prQuickInfoBackground", prQuickInfoBackground);
    }

    /** Writes every option to {@code optionspr.txt}, atomically. */
    public static void save() {
        if (gameDir == null) {
            return;
        }
        Path file = gameDir.resolve(FILE_NAME);
        Path tmp = gameDir.resolve(FILE_NAME + ".tmp");
        try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, String> e : snapshot().entrySet()) {
                w.write(e.getKey());
                w.write(':');
                w.write(e.getValue());
                w.write('\n');
            }
        } catch (IOException e) {
            Pryzma.LOGGER.warn("Failed to write {}", tmp, e);
            return;
        }
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicFailed) {
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                Pryzma.LOGGER.warn("Failed to replace {}", file, e);
            }
        }
    }

    /** Every option as its saved text, in the 1.x file order. */
    static Map<String, String> snapshot() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("prFastPaintings", str(prFastPaintings));
        m.put("prFeedbackButtons", str(prFeedbackButtons));
        m.put("prFogType", str(prFogType));
        m.put("prFogStart", Float.toString(prFogStart));
        m.put("prMipmapType", str(prMipmapType));
        m.put("prOcclusionFancy", str(prOcclusionFancy));
        m.put("prSmoothFps", str(prSmoothFps));
        m.put("prSmoothWorld", str(prSmoothWorld));
        m.put("prAoLevel", Double.toString(prAoLevel));
        m.put("prClouds", str(prClouds));
        m.put("prCloudsHeight", Double.toString(prCloudsHeight));
        m.put("prTrees", str(prTrees));
        m.put("prRain", str(prRain));
        m.put("prAnimatedWater", str(prAnimatedWater));
        m.put("prAnimatedLava", str(prAnimatedLava));
        m.put("prAnimatedFire", str(prAnimatedFire));
        m.put("prAnimatedPortal", str(prAnimatedPortal));
        m.put("prAnimatedRedstone", str(prAnimatedRedstone));
        m.put("prAnimatedExplosion", str(prAnimatedExplosion));
        m.put("prAnimatedFlame", str(prAnimatedFlame));
        m.put("prAnimatedSmoke", str(prAnimatedSmoke));
        m.put("prVoidParticles", str(prVoidParticles));
        m.put("prWaterParticles", str(prWaterParticles));
        m.put("prPortalParticles", str(prPortalParticles));
        m.put("prPotionParticles", str(prPotionParticles));
        m.put("prFireworkParticles", str(prFireworkParticles));
        m.put("prDrippingWaterLava", str(prDrippingWaterLava));
        m.put("prAnimatedTerrain", str(prAnimatedTerrain));
        m.put("prAnimatedTextures", str(prAnimatedTextures));
        m.put("prRainSplash", str(prRainSplash));
        m.put("prLagometer", str(prLagometer));
        m.put("prAutoSaveTicks", str(prAutoSaveTicks));
        m.put("prBetterGrass", str(prBetterGrass));
        m.put("prConnectedTextures", str(prConnectedTextures));
        m.put("prWeather", str(prWeather));
        m.put("prSky", str(prSky));
        m.put("prStars", str(prStars));
        m.put("prSunMoon", str(prSunMoon));
        m.put("prVignette", str(prVignette));
        m.put("prChunkUpdates", str(prChunkUpdates));
        m.put("prTime", str(prTime));
        m.put("prAaLevel", str(prAaLevel));
        m.put("prAfLevel", str(prAfLevel));
        m.put("prProfiler", str(prProfiler));
        m.put("prBetterSnow", str(prBetterSnow));
        m.put("prSwampColors", str(prSwampColors));
        m.put("prRandomEntities", str(prRandomEntities));
        m.put("prCustomFonts", str(prCustomFonts));
        m.put("prCustomColors", str(prCustomColors));
        m.put("prCustomItems", str(prCustomItems));
        m.put("prCustomSky", str(prCustomSky));
        m.put("prShowCapes", str(prShowCapes));
        m.put("prNaturalTextures", str(prNaturalTextures));
        m.put("prEmissiveTextures", str(prEmissiveTextures));
        m.put("prLazyChunkLoading", str(prLazyChunkLoading));
        m.put("prRenderRegions", str(prRenderRegions));
        m.put("prSmartAnimations", str(prSmartAnimations));
        m.put("prDynamicFov", str(prDynamicFov));
        m.put("prAlternateBlocks", str(prAlternateBlocks));
        m.put("prDynamicLights", str(prDynamicLights));
        m.put("prScreenshotSize", str(prScreenshotSize));
        m.put("prCustomEntityModels", str(prCustomEntityModels));
        m.put("prCustomGuis", str(prCustomGuis));
        m.put("prShowGlErrors", str(prShowGlErrors));
        m.put("prFastMath", str(prFastMath));
        m.put("prFastRender", str(prFastRender));
        m.put("prChatBackground", str(prChatBackground));
        m.put("prChatShadow", str(prChatShadow));
        m.put("prTelemetry", str(prTelemetry));
        m.put("prHeldItemTooltips", str(prHeldItemTooltips));
        m.put("prQuickInfo", str(prQuickInfo));
        m.put("prQuickInfoFps", str(prQuickInfoFps));
        m.put("prQuickInfoChunks", str(prQuickInfoChunks));
        m.put("prQuickInfoEntities", str(prQuickInfoEntities));
        m.put("prQuickInfoParticles", str(prQuickInfoParticles));
        m.put("prQuickInfoUpdates", str(prQuickInfoUpdates));
        m.put("prQuickInfoGpu", str(prQuickInfoGpu));
        m.put("prQuickInfoPos", str(prQuickInfoPos));
        m.put("prQuickInfoFacing", str(prQuickInfoFacing));
        m.put("prQuickInfoBiome", str(prQuickInfoBiome));
        m.put("prQuickInfoLight", str(prQuickInfoLight));
        m.put("prQuickInfoMemory", str(prQuickInfoMemory));
        m.put("prQuickInfoNativeMemory", str(prQuickInfoNativeMemory));
        m.put("prQuickInfoTargetBlock", str(prQuickInfoTargetBlock));
        m.put("prQuickInfoTargetFluid", str(prQuickInfoTargetFluid));
        m.put("prQuickInfoTargetEntity", str(prQuickInfoTargetEntity));
        m.put("prQuickInfoLabels", str(prQuickInfoLabels));
        m.put("prQuickInfoBackground", str(prQuickInfoBackground));
        return m;
    }

    /** Pryzma 1.x "Reset Video Settings" values for the Pryzma-owned options. */
    public static void resetToDefaults() {
        prHeldItemTooltips = true;
        prFastPaintings = false;
        prFeedbackButtons = false;
        prFogType = 2;
        prFogStart = 0.8F;
        prMipmapType = 0;
        prOcclusionFancy = false;
        prSmartAnimations = false;
        prSmoothFps = false;
        prSmoothWorld = isSingleProcessor();
        prLazyChunkLoading = false;
        prRenderRegions = false;
        prFastMath = false;
        prFastRender = false;
        prDynamicFov = true;
        prAlternateBlocks = true;
        prDynamicLights = 3;
        prScreenshotSize = 1;
        prCustomEntityModels = true;
        prCustomGuis = true;
        prShowGlErrors = true;
        prChatBackground = 0;
        prChatShadow = true;
        prTelemetry = 0;
        prAoLevel = 1.0;
        prAaLevel = 0;
        prAfLevel = 1;
        prClouds = 0;
        prCloudsHeight = 0.0;
        prTrees = 0;
        prRain = 0;
        prBetterGrass = 3;
        prAutoSaveTicks = 4000;
        prLagometer = false;
        prProfiler = false;
        prWeather = true;
        prSky = true;
        prStars = true;
        prSunMoon = true;
        prVignette = 0;
        prChunkUpdates = 1;
        prTime = 0;
        prBetterSnow = false;
        prSwampColors = true;
        prRandomEntities = true;
        prCustomFonts = true;
        prCustomColors = true;
        prCustomItems = true;
        prCustomSky = true;
        prShowCapes = true;
        prConnectedTextures = 2;
        prNaturalTextures = false;
        prEmissiveTextures = true;
        setAllAnimations(true);
        prQuickInfo = false;
        prQuickInfoFps = VALUE_FULL;
        prQuickInfoChunks = true;
        prQuickInfoEntities = true;
        prQuickInfoParticles = false;
        prQuickInfoUpdates = true;
        prQuickInfoGpu = false;
        prQuickInfoPos = VALUE_COMPACT;
        prQuickInfoFacing = VALUE_OFF;
        prQuickInfoBiome = false;
        prQuickInfoLight = false;
        prQuickInfoMemory = VALUE_OFF;
        prQuickInfoNativeMemory = VALUE_OFF;
        prQuickInfoTargetBlock = VALUE_OFF;
        prQuickInfoTargetFluid = VALUE_OFF;
        prQuickInfoTargetEntity = VALUE_OFF;
        prQuickInfoLabels = VALUE_COMPACT;
        prQuickInfoBackground = false;
    }

    /** Animations screen "All ON" / "All OFF" (the vanilla particles option is set by the caller). */
    public static void setAllAnimations(boolean on) {
        int anim = on ? 0 : 2;
        prAnimatedWater = anim;
        prAnimatedLava = anim;
        prAnimatedFire = on;
        prAnimatedPortal = on;
        prAnimatedRedstone = on;
        prAnimatedExplosion = on;
        prAnimatedFlame = on;
        prAnimatedSmoke = on;
        prVoidParticles = on;
        prWaterParticles = on;
        prRainSplash = on;
        prPortalParticles = on;
        prPotionParticles = on;
        prFireworkParticles = on;
        prDrippingWaterLava = on;
        prAnimatedTerrain = on;
        prAnimatedTextures = on;
    }

    /** Quick Info screen "All ON" / "All OFF". */
    public static void setAllQuickInfos(boolean on) {
        int value = on ? VALUE_FULL : VALUE_OFF;
        prQuickInfoFps = value;
        prQuickInfoChunks = on;
        prQuickInfoEntities = on;
        prQuickInfoParticles = on;
        prQuickInfoUpdates = on;
        prQuickInfoGpu = on;
        prQuickInfoPos = value;
        prQuickInfoFacing = value;
        prQuickInfoBiome = on;
        prQuickInfoLight = on;
        prQuickInfoMemory = value;
        prQuickInfoNativeMemory = value;
        prQuickInfoTargetBlock = value;
        prQuickInfoTargetFluid = value;
        prQuickInfoTargetEntity = value;
    }

    // ---------------------------------------------------------------- parsing helpers

    private static String str(boolean b) {
        return Boolean.toString(b);
    }

    private static String str(int i) {
        return Integer.toString(i);
    }

    private static boolean bool(Map<String, String> v, String key, boolean def) {
        String s = v.get(key);
        return s == null ? def : Boolean.parseBoolean(s);
    }

    private static int clampInt(Map<String, String> v, String key, int def, int min, int max) {
        String s = v.get(key);
        return s == null ? def : Math.clamp(parseInt(s, def), min, max);
    }

    private static double clampDouble(Map<String, String> v, String key, double def) {
        String s = v.get(key);
        return s == null ? def : Math.clamp(parseFloat(s, (float) def), 0.0, 1.0);
    }

    private static int oneOf(Map<String, String> v, String key, int def, int[] allowed) {
        String s = v.get(key);
        if (s == null) {
            return def;
        }
        int value = parseInt(s, Integer.MIN_VALUE);
        for (int a : allowed) {
            if (a == value) {
                return value;
            }
        }
        return allowed[0];
    }

    private static int quickValue(Map<String, String> v, String key, int def, boolean labels) {
        String s = v.get(key);
        if (s == null) {
            return def;
        }
        int value = parseInt(s, -1);
        boolean valid = labels
                ? value == VALUE_COMPACT || value == VALUE_FULL || value == VALUE_DETAILED
                : value == VALUE_OFF || value == VALUE_COMPACT || value == VALUE_FULL;
        return valid ? value : labels ? VALUE_COMPACT : VALUE_OFF;
    }

    static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    static float parseFloat(String s, float def) {
        try {
            return Float.parseFloat(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
