package net.pryzma.gui;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.pryzma.PryzmaConfig;
import net.pryzma.perf.PrChunkWorkers;
import net.pryzma.perf.PrServerPriority;

/**
 * Every Pryzma option of the video settings, with the cycle order and labels of Pryzma 1.x
 * ({@code Options.setOptionValuePryzma} / {@code getKeyBindingPryzma}) and the side effect each
 * change needs in the 2.0 engine.
 */
public final class PrOptions {
    private static final int[] QUICK_OFF_COMPACT_FULL = {PryzmaConfig.VALUE_OFF, PryzmaConfig.VALUE_COMPACT, PryzmaConfig.VALUE_FULL};
    private static final int[] QUICK_COMPACT_FULL_DETAILED = {PryzmaConfig.VALUE_COMPACT, PryzmaConfig.VALUE_FULL, PryzmaConfig.VALUE_DETAILED};
    private static final String[] KEYS_DYNAMIC_LIGHTS = {"options.off", "options.graphics.fast", "options.graphics.fancy"};
    private static final int[] AF_VALUES = {1, 2, 4, 8, 16};
    private static final int[] AA_VALUES = {0, 2, 4, 6, 8, 12, 16};
    private static final int AUTOSAVE_STEP = 900;

    private PrOptions() {
    }

    // ------------------------------------------------------------------ main screen

    public static final PrOption.Slider AO_LEVEL = unitSlider("pr.options.AO_LEVEL", true,
            () -> PryzmaConfig.prAoLevel, v -> {
                PryzmaConfig.prAoLevel = v;
                allChanged();
            });
    public static final PrOption.Cycle DYNAMIC_FOV = bool("pr.options.DYNAMIC_FOV",
            () -> PryzmaConfig.prDynamicFov, v -> PryzmaConfig.prDynamicFov = v);
    public static final PrOption.Cycle DYNAMIC_LIGHTS = cycle("pr.options.DYNAMIC_LIGHTS",
            () -> I18n.get(KEYS_DYNAMIC_LIGHTS[Math.max(0, indexOf(PryzmaConfig.prDynamicLights, PryzmaConfig.DYNAMIC_LIGHTS_VALUES))]),
            dir -> PryzmaConfig.prDynamicLights = nextValue(PryzmaConfig.prDynamicLights, PryzmaConfig.DYNAMIC_LIGHTS_VALUES));

    /**
     * Max Framerate as Pryzma 1.x showed it: the lowest position is VSync. Positions follow
     * vanilla's 10 fps steps, the grid its framerate option can store.
     */
    public static final PrOption.Slider FRAMERATE_LIMIT = new PrOption.Slider("options.framerateLimit", 26, false,
            () -> options().enableVsync().get() ? 0 : options().framerateLimit().get(),
            v -> {
                int fps = (int) v;
                options().enableVsync().set(fps == 0);
                options().framerateLimit().set(fps == 0 ? 260 : fps);
            },
            p -> Math.round(p * 26) * 10.0, v -> v / 260.0,
            v -> {
                String caption = I18n.get("options.framerateLimit") + ": ";
                if (v == 0) {
                    return Component.literal(caption + I18n.get("pr.options.framerateLimit.vsync"));
                }
                return Component.literal(caption + (v >= 260 ? I18n.get("options.framerateLimit.max")
                        : I18n.get("options.framerate", (int) v)));
            });

    // ------------------------------------------------------------------ details

    public static final PrOption.Cycle CLOUDS = cycle("pr.options.CLOUDS",
            () -> defaultFastFancyOff(PryzmaConfig.prClouds),
            dir -> PryzmaConfig.prClouds = PryzmaConfig.prClouds + 1 > 3 ? 0 : PryzmaConfig.prClouds + 1);
    public static final PrOption.Slider CLOUD_HEIGHT = unitSlider("pr.options.CLOUD_HEIGHT", false,
            () -> PryzmaConfig.prCloudsHeight, v -> PryzmaConfig.prCloudsHeight = v);
    public static final PrOption.Cycle TREES = cycle("pr.options.TREES",
            () -> switch (PryzmaConfig.prTrees) {
                case 1 -> fast();
                case 2 -> fancy();
                case 4 -> I18n.get("pr.general.smart");
                default -> byDefault();
            },
            dir -> {
                PryzmaConfig.prTrees = nextValue(PryzmaConfig.prTrees, PryzmaConfig.TREES_VALUES);
                allChanged();
            });
    public static final PrOption.Cycle RAIN = cycle("pr.options.RAIN",
            () -> defaultFastFancyOff(PryzmaConfig.prRain),
            dir -> PryzmaConfig.prRain = PryzmaConfig.prRain + 1 > 3 ? 0 : PryzmaConfig.prRain + 1);
    public static final PrOption.Cycle SKY = bool("pr.options.SKY", () -> PryzmaConfig.prSky, v -> PryzmaConfig.prSky = v);
    public static final PrOption.Cycle STARS = bool("pr.options.STARS", () -> PryzmaConfig.prStars, v -> PryzmaConfig.prStars = v);
    public static final PrOption.Cycle SUN_MOON = bool("pr.options.SUN_MOON", () -> PryzmaConfig.prSunMoon, v -> PryzmaConfig.prSunMoon = v);
    public static final PrOption.Cycle SHOW_CAPES = bool("pr.options.SHOW_CAPES",
            () -> PryzmaConfig.prShowCapes, v -> PryzmaConfig.prShowCapes = v);
    public static final PrOption.Cycle FOG_FANCY = cycle("pr.options.FOG_FANCY",
            () -> switch (PryzmaConfig.prFogType) {
                case 1 -> fast();
                case 2 -> fancy();
                default -> off();
            },
            dir -> PryzmaConfig.prFogType = PryzmaConfig.prFogType == 2 ? 3 : 2);
    public static final PrOption.Cycle FOG_START = cycle("pr.options.FOG_START",
            () -> Float.toString(PryzmaConfig.prFogStart),
            dir -> {
                PryzmaConfig.prFogStart += 0.2F;
                if (PryzmaConfig.prFogStart > 0.81F) {
                    PryzmaConfig.prFogStart = 0.2F;
                }
            });
    public static final PrOption.Cycle HELD_ITEM_TOOLTIPS = bool("pr.options.HELD_ITEM_TOOLTIPS",
            () -> PryzmaConfig.prHeldItemTooltips, v -> PryzmaConfig.prHeldItemTooltips = v);
    public static final PrOption.Cycle SWAMP_COLORS = bool("pr.options.SWAMP_COLORS",
            () -> PryzmaConfig.prSwampColors, v -> {
                PryzmaConfig.prSwampColors = v;
                allChanged();
            });
    public static final PrOption.Cycle VIGNETTE = cycle("pr.options.VIGNETTE",
            () -> switch (PryzmaConfig.prVignette) {
                case 1 -> fast();
                case 2 -> fancy();
                default -> byDefault();
            },
            dir -> PryzmaConfig.prVignette = PryzmaConfig.prVignette + 1 > 2 ? 0 : PryzmaConfig.prVignette + 1);
    public static final PrOption.Cycle ALTERNATE_BLOCKS = bool("pr.options.ALTERNATE_BLOCKS",
            () -> PryzmaConfig.prAlternateBlocks, v -> {
                PryzmaConfig.prAlternateBlocks = v;
                allChanged();
            });

    // ------------------------------------------------------------------ quality

    /** Mipmap Levels with the 1.x label ("Maximum" at 4); applied when the settings close, as vanilla does. */
    public static final PrOption.Slider MIPMAP_LEVELS = new PrOption.Slider("options.mipmapLevels", 4, true,
            () -> options().mipmapLevels().get(), v -> options().mipmapLevels().set((int) v),
            p -> (double) Math.round(p * 4), v -> v / 4.0,
            v -> {
                Component caption = Component.translatable("options.mipmapLevels");
                if (v >= 4) {
                    return Options.genericValueLabel(caption, Component.translatable("pr.general.max"));
                }
                return v == 0 ? CommonComponents.optionStatus(caption, false)
                        : Options.genericValueLabel(caption, Component.literal(Integer.toString((int) v)));
            });
    public static final PrOption.Cycle MIPMAP_TYPE = cycle("pr.options.MIPMAP_TYPE",
            () -> I18n.get(switch (PryzmaConfig.prMipmapType) {
                case 1 -> "pr.options.mipmap.linear";
                case 2 -> "pr.options.mipmap.bilinear";
                case 3 -> "pr.options.mipmap.trilinear";
                default -> "pr.options.mipmap.nearest";
            }),
            dir -> PryzmaConfig.prMipmapType = PryzmaConfig.prMipmapType + dir & 3);
    public static final PrOption.Slider AF_LEVEL = steppedSlider("pr.options.AF_LEVEL", AF_VALUES,
            () -> PryzmaConfig.prAfLevel, v -> PryzmaConfig.prAfLevel = v,
            v -> v == 1 ? off() : Integer.toString(v));
    /** Stored for restart like 1.x; the label says so whenever it differs from the running level. */
    public static final PrOption.Slider AA_LEVEL = steppedSlider("pr.options.AA_LEVEL", AA_VALUES,
            () -> PryzmaConfig.prAaLevel, v -> PryzmaConfig.prAaLevel = v,
            v -> (v == 0 ? off() : Integer.toString(v))
                    + (v != PryzmaConfig.startupAaLevel() ? " (" + I18n.get("pr.general.restart") + ")" : ""));
    public static final PrOption.Cycle EMISSIVE_TEXTURES = bool("pr.options.EMISSIVE_TEXTURES",
            () -> PryzmaConfig.prEmissiveTextures, v -> {
                PryzmaConfig.prEmissiveTextures = v;
                reloadTextures();
            });
    public static final PrOption.Cycle RANDOM_ENTITIES = bool("pr.options.RANDOM_ENTITIES",
            () -> PryzmaConfig.prRandomEntities, v -> {
                PryzmaConfig.prRandomEntities = v;
                reloadTextures();
            });
    public static final PrOption.Cycle BETTER_GRASS = cycle("pr.options.BETTER_GRASS",
            () -> switch (PryzmaConfig.prBetterGrass) {
                case 1 -> fast();
                case 2 -> fancy();
                default -> off();
            },
            dir -> {
                PryzmaConfig.prBetterGrass = PryzmaConfig.prBetterGrass + 1 > 3 ? 1 : PryzmaConfig.prBetterGrass + 1;
                allChanged();
            });
    public static final PrOption.Cycle BETTER_SNOW = bool("pr.options.BETTER_SNOW",
            () -> PryzmaConfig.prBetterSnow, v -> {
                PryzmaConfig.prBetterSnow = v;
                allChanged();
            });
    public static final PrOption.Cycle CUSTOM_FONTS = bool("pr.options.CUSTOM_FONTS",
            () -> PryzmaConfig.prCustomFonts, v -> PryzmaConfig.prCustomFonts = v);
    public static final PrOption.Cycle CUSTOM_COLORS = bool("pr.options.CUSTOM_COLORS",
            () -> PryzmaConfig.prCustomColors, v -> {
                PryzmaConfig.prCustomColors = v;
                allChanged();
            });
    public static final PrOption.Cycle CONNECTED_TEXTURES = cycle("pr.options.CONNECTED_TEXTURES",
            () -> switch (PryzmaConfig.prConnectedTextures) {
                case 1 -> fast();
                case 2 -> fancy();
                default -> off();
            },
            dir -> {
                PryzmaConfig.prConnectedTextures = PryzmaConfig.prConnectedTextures + 1 > 3 ? 1 : PryzmaConfig.prConnectedTextures + 1;
                // Fast to Fancy only re-meshes; switching the rules on or off rebuilds the atlas.
                if (PryzmaConfig.prConnectedTextures == 2) {
                    allChanged();
                } else {
                    reloadTextures();
                }
            });
    public static final PrOption.Cycle NATURAL_TEXTURES = bool("pr.options.NATURAL_TEXTURES",
            () -> PryzmaConfig.prNaturalTextures, v -> {
                PryzmaConfig.prNaturalTextures = v;
                allChanged();
            });
    public static final PrOption.Cycle CUSTOM_SKY = bool("pr.options.CUSTOM_SKY",
            () -> PryzmaConfig.prCustomSky, v -> PryzmaConfig.prCustomSky = v);
    public static final PrOption.Cycle CUSTOM_ITEMS = bool("pr.options.CUSTOM_ITEMS",
            () -> PryzmaConfig.prCustomItems, v -> {
                PryzmaConfig.prCustomItems = v;
                reloadTextures();
            });
    public static final PrOption.Cycle CUSTOM_ENTITY_MODELS = bool("pr.options.CUSTOM_ENTITY_MODELS",
            () -> PryzmaConfig.prCustomEntityModels, v -> {
                PryzmaConfig.prCustomEntityModels = v;
                reloadTextures();
            });
    public static final PrOption.Cycle CUSTOM_GUIS = bool("pr.options.CUSTOM_GUIS",
            () -> PryzmaConfig.prCustomGuis, v -> PryzmaConfig.prCustomGuis = v);

    // ------------------------------------------------------------------ animations

    public static final PrOption.Cycle ANIMATED_WATER = cycle("pr.options.ANIMATED_WATER",
            () -> onDynamicOff(PryzmaConfig.prAnimatedWater), dir -> PryzmaConfig.prAnimatedWater = nextAnim(PryzmaConfig.prAnimatedWater));
    public static final PrOption.Cycle ANIMATED_LAVA = cycle("pr.options.ANIMATED_LAVA",
            () -> onDynamicOff(PryzmaConfig.prAnimatedLava), dir -> PryzmaConfig.prAnimatedLava = nextAnim(PryzmaConfig.prAnimatedLava));
    public static final PrOption.Cycle ANIMATED_FIRE = bool("pr.options.ANIMATED_FIRE",
            () -> PryzmaConfig.prAnimatedFire, v -> PryzmaConfig.prAnimatedFire = v);
    public static final PrOption.Cycle ANIMATED_PORTAL = bool("pr.options.ANIMATED_PORTAL",
            () -> PryzmaConfig.prAnimatedPortal, v -> PryzmaConfig.prAnimatedPortal = v);
    public static final PrOption.Cycle ANIMATED_REDSTONE = bool("pr.options.ANIMATED_REDSTONE",
            () -> PryzmaConfig.prAnimatedRedstone, v -> PryzmaConfig.prAnimatedRedstone = v);
    public static final PrOption.Cycle ANIMATED_EXPLOSION = bool("pr.options.ANIMATED_EXPLOSION",
            () -> PryzmaConfig.prAnimatedExplosion, v -> PryzmaConfig.prAnimatedExplosion = v);
    public static final PrOption.Cycle ANIMATED_FLAME = bool("pr.options.ANIMATED_FLAME",
            () -> PryzmaConfig.prAnimatedFlame, v -> PryzmaConfig.prAnimatedFlame = v);
    public static final PrOption.Cycle ANIMATED_SMOKE = bool("pr.options.ANIMATED_SMOKE",
            () -> PryzmaConfig.prAnimatedSmoke, v -> PryzmaConfig.prAnimatedSmoke = v);
    public static final PrOption.Cycle VOID_PARTICLES = bool("pr.options.VOID_PARTICLES",
            () -> PryzmaConfig.prVoidParticles, v -> PryzmaConfig.prVoidParticles = v);
    public static final PrOption.Cycle WATER_PARTICLES = bool("pr.options.WATER_PARTICLES",
            () -> PryzmaConfig.prWaterParticles, v -> PryzmaConfig.prWaterParticles = v);
    public static final PrOption.Cycle RAIN_SPLASH = bool("pr.options.RAIN_SPLASH",
            () -> PryzmaConfig.prRainSplash, v -> PryzmaConfig.prRainSplash = v);
    public static final PrOption.Cycle PORTAL_PARTICLES = bool("pr.options.PORTAL_PARTICLES",
            () -> PryzmaConfig.prPortalParticles, v -> PryzmaConfig.prPortalParticles = v);
    public static final PrOption.Cycle POTION_PARTICLES = bool("pr.options.POTION_PARTICLES",
            () -> PryzmaConfig.prPotionParticles, v -> PryzmaConfig.prPotionParticles = v);
    public static final PrOption.Cycle DRIPPING_WATER_LAVA = bool("pr.options.DRIPPING_WATER_LAVA",
            () -> PryzmaConfig.prDrippingWaterLava, v -> PryzmaConfig.prDrippingWaterLava = v);
    public static final PrOption.Cycle ANIMATED_TERRAIN = bool("pr.options.ANIMATED_TERRAIN",
            () -> PryzmaConfig.prAnimatedTerrain, v -> PryzmaConfig.prAnimatedTerrain = v);
    public static final PrOption.Cycle ANIMATED_TEXTURES = bool("pr.options.ANIMATED_TEXTURES",
            () -> PryzmaConfig.prAnimatedTextures, v -> PryzmaConfig.prAnimatedTextures = v);
    public static final PrOption.Cycle FIREWORK_PARTICLES = bool("pr.options.FIREWORK_PARTICLES",
            () -> PryzmaConfig.prFireworkParticles, v -> PryzmaConfig.prFireworkParticles = v);

    // ------------------------------------------------------------------ performance

    public static final PrOption.Cycle RENDER_REGIONS = bool("pr.options.RENDER_REGIONS",
            () -> PryzmaConfig.prRenderRegions, v -> {
                PryzmaConfig.prRenderRegions = v;
                allChanged();
            });
    public static final PrOption.Cycle FAST_RENDER = bool("pr.options.FAST_RENDER",
            () -> PryzmaConfig.prFastRender, v -> PryzmaConfig.prFastRender = v);
    public static final PrOption.Cycle SMART_ANIMATIONS = bool("pr.options.SMART_ANIMATIONS",
            () -> PryzmaConfig.prSmartAnimations, v -> {
                PryzmaConfig.prSmartAnimations = v;
                allChanged();
            });
    public static final PrOption.Cycle FAST_MATH = bool("pr.options.FAST_MATH",
            () -> PryzmaConfig.prFastMath, v -> PryzmaConfig.prFastMath = v);
    public static final PrOption.Cycle SMOOTH_FPS = bool("pr.options.SMOOTH_FPS",
            () -> PryzmaConfig.prSmoothFps, v -> PryzmaConfig.prSmoothFps = v);
    public static final PrOption.Cycle SMOOTH_WORLD = bool("pr.options.SMOOTH_WORLD",
            () -> PryzmaConfig.prSmoothWorld, v -> {
                PryzmaConfig.prSmoothWorld = v;
                PrServerPriority.apply();
            });
    public static final PrOption.Cycle CHUNK_UPDATES = cycle("pr.options.CHUNK_UPDATES",
            () -> Integer.toString(PrChunkWorkers.threadsFor(PryzmaConfig.prChunkUpdates)),
            dir -> {
                int next = PryzmaConfig.prChunkUpdates + dir;
                if (next > 5) {
                    next = 1;
                }
                if (next < 1) {
                    next = 5;
                }
                PryzmaConfig.prChunkUpdates = next;
                PrChunkWorkers.resize();
            });
    /**
     * @deprecated Off the Performance page: nothing reads {@link PryzmaConfig#prLazyChunkLoading}.
     * Kept so references to the option and saved settings still resolve.
     */
    @Deprecated
    public static final PrOption.Cycle LAZY_CHUNK_LOADING = bool("pr.options.LAZY_CHUNK_LOADING",
            () -> PryzmaConfig.prLazyChunkLoading, v -> PryzmaConfig.prLazyChunkLoading = v);
    public static final PrOption.Cycle FAST_PAINTINGS = bool("pr.options.FAST_PAINTINGS",
            () -> PryzmaConfig.prFastPaintings, v -> PryzmaConfig.prFastPaintings = v);

    // ------------------------------------------------------------------ other

    public static final PrOption.Cycle LAGOMETER = bool("pr.options.LAGOMETER", () -> PryzmaConfig.prLagometer, v -> {
        PryzmaConfig.prLagometer = v;
        var debug = Minecraft.getInstance().getDebugOverlay();
        if (debug.showDebugScreen() && debug.showFpsCharts() != v) {
            debug.toggleFpsCharts();
        }
    });
    public static final PrOption.Cycle PROFILER = bool("pr.options.PROFILER", () -> PryzmaConfig.prProfiler, v -> {
        PryzmaConfig.prProfiler = v;
        var debug = Minecraft.getInstance().getDebugOverlay();
        if (debug.showDebugScreen() && debug.showProfilerChart() != v) {
            debug.toggleProfilerChart();
        }
    });
    public static final PrOption.Cycle ADVANCED_TOOLTIPS = bool("pr.options.ADVANCED_TOOLTIPS",
            () -> options().advancedItemTooltips, v -> options().advancedItemTooltips = v);
    public static final PrOption.Cycle WEATHER = bool("pr.options.WEATHER", () -> PryzmaConfig.prWeather, v -> PryzmaConfig.prWeather = v);
    public static final PrOption.Cycle TIME = cycle("pr.options.TIME",
            () -> switch (PryzmaConfig.prTime) {
                case 1 -> I18n.get("pr.options.time.dayOnly");
                case 2 -> I18n.get("pr.options.time.nightOnly");
                default -> byDefault();
            },
            dir -> PryzmaConfig.prTime = PryzmaConfig.prTime + 1 > 2 ? 0 : PryzmaConfig.prTime + 1);
    public static final PrOption.Cycle AUTOSAVE_TICKS = cycle("pr.options.AUTOSAVE_TICKS",
            () -> {
                int t = PryzmaConfig.prAutoSaveTicks;
                return I18n.get(t <= AUTOSAVE_STEP ? "pr.options.save.45s"
                        : t <= 2 * AUTOSAVE_STEP ? "pr.options.save.90s"
                        : t <= 4 * AUTOSAVE_STEP ? "pr.options.save.3min"
                        : t <= 8 * AUTOSAVE_STEP ? "pr.options.save.6min"
                        : t <= 16 * AUTOSAVE_STEP ? "pr.options.save.12min" : "pr.options.save.24min");
            },
            dir -> {
                int t = Math.max(PryzmaConfig.prAutoSaveTicks / AUTOSAVE_STEP * AUTOSAVE_STEP, AUTOSAVE_STEP) * 2;
                PryzmaConfig.prAutoSaveTicks = t > 32 * AUTOSAVE_STEP ? AUTOSAVE_STEP : t;
            });
    public static final PrOption.Cycle SCREENSHOT_SIZE = cycle("pr.options.SCREENSHOT_SIZE",
            () -> PryzmaConfig.prScreenshotSize <= 1 ? byDefault() : PryzmaConfig.prScreenshotSize + "x",
            dir -> PryzmaConfig.prScreenshotSize = PryzmaConfig.prScreenshotSize + 1 > 4 ? 1 : PryzmaConfig.prScreenshotSize + 1);
    public static final PrOption.Cycle SHOW_GL_ERRORS = bool("pr.options.SHOW_GL_ERRORS",
            () -> PryzmaConfig.prShowGlErrors, v -> PryzmaConfig.prShowGlErrors = v);
    public static final PrOption.Cycle FEEDBACK_BUTTONS = bool("pr.options.FEEDBACK_BUTTONS",
            () -> PryzmaConfig.prFeedbackButtons, v -> PryzmaConfig.prFeedbackButtons = v);

    // ------------------------------------------------------------------ quick info

    public static final PrOption.Cycle QUICK_INFO = bool("pr.options.QUICK_INFO", () -> PryzmaConfig.prQuickInfo, v -> PryzmaConfig.prQuickInfo = v);
    public static final PrOption.Cycle QUICK_INFO_FPS = quick("pr.options.QUICK_INFO_FPS", QUICK_OFF_COMPACT_FULL,
            () -> PryzmaConfig.prQuickInfoFps, v -> PryzmaConfig.prQuickInfoFps = v);
    public static final PrOption.Cycle QUICK_INFO_CHUNKS = bool("pr.options.QUICK_INFO_CHUNKS",
            () -> PryzmaConfig.prQuickInfoChunks, v -> PryzmaConfig.prQuickInfoChunks = v);
    public static final PrOption.Cycle QUICK_INFO_ENTITIES = bool("pr.options.QUICK_INFO_ENTITIES",
            () -> PryzmaConfig.prQuickInfoEntities, v -> PryzmaConfig.prQuickInfoEntities = v);
    public static final PrOption.Cycle QUICK_INFO_PARTICLES = bool("pr.options.QUICK_INFO_PARTICLES",
            () -> PryzmaConfig.prQuickInfoParticles, v -> PryzmaConfig.prQuickInfoParticles = v);
    public static final PrOption.Cycle QUICK_INFO_UPDATES = bool("pr.options.QUICK_INFO_UPDATES",
            () -> PryzmaConfig.prQuickInfoUpdates, v -> PryzmaConfig.prQuickInfoUpdates = v);
    public static final PrOption.Cycle QUICK_INFO_GPU = bool("pr.options.QUICK_INFO_GPU",
            () -> PryzmaConfig.prQuickInfoGpu, v -> PryzmaConfig.prQuickInfoGpu = v);
    public static final PrOption.Cycle QUICK_INFO_POS = quick("pr.options.QUICK_INFO_POS", QUICK_OFF_COMPACT_FULL,
            () -> PryzmaConfig.prQuickInfoPos, v -> PryzmaConfig.prQuickInfoPos = v);
    public static final PrOption.Cycle QUICK_INFO_BIOME = bool("pr.options.QUICK_INFO_BIOME",
            () -> PryzmaConfig.prQuickInfoBiome, v -> PryzmaConfig.prQuickInfoBiome = v);
    public static final PrOption.Cycle QUICK_INFO_FACING = quick("pr.options.QUICK_INFO_FACING", QUICK_OFF_COMPACT_FULL,
            () -> PryzmaConfig.prQuickInfoFacing, v -> PryzmaConfig.prQuickInfoFacing = v);
    public static final PrOption.Cycle QUICK_INFO_LIGHT = bool("pr.options.QUICK_INFO_LIGHT",
            () -> PryzmaConfig.prQuickInfoLight, v -> PryzmaConfig.prQuickInfoLight = v);
    public static final PrOption.Cycle QUICK_INFO_MEMORY = quick("pr.options.QUICK_INFO_MEMORY", QUICK_OFF_COMPACT_FULL,
            () -> PryzmaConfig.prQuickInfoMemory, v -> PryzmaConfig.prQuickInfoMemory = v);
    public static final PrOption.Cycle QUICK_INFO_NATIVE_MEMORY = quick("pr.options.QUICK_INFO_NATIVE_MEMORY", QUICK_OFF_COMPACT_FULL,
            () -> PryzmaConfig.prQuickInfoNativeMemory, v -> PryzmaConfig.prQuickInfoNativeMemory = v);
    public static final PrOption.Cycle QUICK_INFO_TARGET_BLOCK = quick("pr.options.QUICK_INFO_TARGET_BLOCK", QUICK_OFF_COMPACT_FULL,
            () -> PryzmaConfig.prQuickInfoTargetBlock, v -> PryzmaConfig.prQuickInfoTargetBlock = v);
    public static final PrOption.Cycle QUICK_INFO_TARGET_FLUID = quick("pr.options.QUICK_INFO_TARGET_FLUID", QUICK_OFF_COMPACT_FULL,
            () -> PryzmaConfig.prQuickInfoTargetFluid, v -> PryzmaConfig.prQuickInfoTargetFluid = v);
    public static final PrOption.Cycle QUICK_INFO_TARGET_ENTITY = quick("pr.options.QUICK_INFO_TARGET_ENTITY", QUICK_OFF_COMPACT_FULL,
            () -> PryzmaConfig.prQuickInfoTargetEntity, v -> PryzmaConfig.prQuickInfoTargetEntity = v);
    public static final PrOption.Cycle QUICK_INFO_LABELS = quick("pr.options.QUICK_INFO_LABELS", QUICK_COMPACT_FULL_DETAILED,
            () -> PryzmaConfig.prQuickInfoLabels, v -> PryzmaConfig.prQuickInfoLabels = v);
    public static final PrOption.Cycle QUICK_INFO_BACKGROUND = bool("pr.options.QUICK_INFO_BACKGROUND",
            () -> PryzmaConfig.prQuickInfoBackground, v -> PryzmaConfig.prQuickInfoBackground = v);

    // ------------------------------------------------------------------ builders

    private static PrOption.Cycle cycle(String key, Supplier<String> value, IntConsumer step) {
        return new PrOption.Cycle(key, value, step);
    }

    private static PrOption.Cycle bool(String key, BooleanSupplier get, Consumer<Boolean> set) {
        return new PrOption.Cycle(key, () -> get.getAsBoolean() ? on() : off(), dir -> set.accept(!get.getAsBoolean()));
    }

    /** Quick Info value lists: the one kind of 1.x option that steps backwards with Shift. */
    private static PrOption.Cycle quick(String key, int[] values, IntSupplier get, IntConsumer set) {
        return new PrOption.Cycle(key,
                () -> switch (get.getAsInt()) {
                    case PryzmaConfig.VALUE_OFF -> off();
                    case PryzmaConfig.VALUE_COMPACT -> I18n.get("pr.general.compact");
                    case PryzmaConfig.VALUE_FULL -> I18n.get("pr.general.full");
                    case PryzmaConfig.VALUE_DETAILED -> I18n.get("pr.general.detailed");
                    default -> "???";
                },
                dir -> {
                    int next = indexOf(get.getAsInt(), values) + dir;
                    if (next < 0 || next >= values.length) {
                        next = dir > 0 ? 0 : values.length - 1;
                    }
                    set.accept(values[next]);
                });
    }

    /** 0..1 slider with the 1.x percentage label ("Off" at zero). */
    private static PrOption.Slider unitSlider(String key, boolean applyOnRelease, DoubleSupplier get, DoubleConsumer set) {
        DoubleFunction<Component> label = v -> Component.literal(I18n.get(key) + ": "
                + (v == 0.0 ? I18n.get("options.off") : (int) (v * 100.0) + "%"));
        return new PrOption.Slider(key, 0, applyOnRelease, get, set, p -> p, v -> v, label);
    }

    /** Slider over a fixed value list (anti-aliasing, anisotropic filtering). */
    private static PrOption.Slider steppedSlider(String key, int[] values, IntSupplier get, IntConsumer set,
            java.util.function.IntFunction<String> text) {
        int last = values.length - 1;
        return new PrOption.Slider(key, last, true, () -> get.getAsInt(), v -> set.accept((int) v),
                p -> (double) values[(int) Math.round(p * last)],
                v -> Math.max(0, indexOf((int) v, values)) / (double) last,
                v -> Component.literal(I18n.get(key) + ": " + text.apply((int) v)));
    }

    private static String defaultFastFancyOff(int value) {
        return switch (value) {
            case 1 -> fast();
            case 2 -> fancy();
            case 3 -> off();
            default -> byDefault();
        };
    }

    private static String onDynamicOff(int value) {
        return switch (value) {
            case 1 -> I18n.get("pr.options.animation.dynamic");
            case 2 -> off();
            default -> on();
        };
    }

    /** Water and lava animation: On, then Off; the old "dynamic" value is skipped. */
    private static int nextAnim(int value) {
        int next = value + 1;
        if (next == 1) {
            next++;
        }
        return next > 2 ? 0 : next;
    }

    static int nextValue(int value, int[] values) {
        int index = indexOf(value, values);
        return index < 0 ? values[0] : values[(index + 1) % values.length];
    }

    static int indexOf(int value, int[] values) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == value) {
                return i;
            }
        }
        return -1;
    }

    static String on() {
        return I18n.get("options.on");
    }

    static String off() {
        return I18n.get("options.off");
    }

    static String fast() {
        return I18n.get("options.graphics.fast");
    }

    static String fancy() {
        return I18n.get("options.graphics.fancy");
    }

    static String byDefault() {
        return I18n.get("generator.minecraft.normal");
    }

    static Options options() {
        return Minecraft.getInstance().options;
    }

    static void allChanged() {
        Minecraft.getInstance().levelRenderer.allChanged();
    }

    static void reloadTextures() {
        Minecraft.getInstance().delayTextureReload();
    }
}
