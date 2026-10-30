package net.pryzma.gui;

import java.util.Optional;

import com.mojang.blaze3d.platform.Monitor;
import com.mojang.blaze3d.platform.VideoMode;
import com.mojang.blaze3d.platform.Window;

import net.minecraft.client.CloudStatus;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.PrioritizeChunkUpdates;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.pryzma.PryzmaConfig;
import net.pryzma.lod.PryzmaLod;

/** The six Pryzma 1.x settings pages, each with its own grid and bottom row. */
final class PrSettingsScreen extends PrScreen {
    enum Page {
        DETAILS("pr.options.detailsTitle"),
        QUALITY("pr.options.qualityTitle"),
        ANIMATIONS("pr.options.animationsTitle"),
        PERFORMANCE("pr.options.performanceTitle"),
        OTHER("pr.options.otherTitle"),
        QUICK_INFO("pr.options.quickInfoTitle"),
        FARLANDS("pr.options.farlandsTitle");

        final String title;

        Page(String title) {
            this.title = title;
        }
    }

    private final Page page;

    PrSettingsScreen(Page page, Screen parent) {
        super(Component.translatable(page.title), parent);
        this.page = page;
    }

    @Override
    protected void init() {
        Options o = minecraft.options;
        switch (page) {
            case DETAILS -> {
                grid(PrOptions.CLOUDS, PrOptions.CLOUD_HEIGHT,
                        PrOptions.TREES, PrOptions.RAIN,
                        PrOptions.SKY, PrOptions.STARS,
                        PrOptions.SUN_MOON, PrOptions.SHOW_CAPES,
                        PrOptions.FOG_FANCY, PrOptions.FOG_START,
                        vanilla("options.viewBobbing", o.bobView()), PrOptions.HELD_ITEM_TOOLTIPS,
                        PrOptions.VIGNETTE, PrOptions.SWAMP_COLORS,
                        PrOptions.DETAIL_DISTANCE, PrOptions.ALTERNATE_BLOCKS,
                        vanilla("options.entityDistanceScaling", o.entityDistanceScaling()),
                        vanilla("options.biomeBlendRadius", o.biomeBlendRadius()));
                int left = width / 2 - 155;
                addRenderableWidget(new PrButton(215, left, bottomY(), 150,
                        Component.translatable("pr.options.farlandsOptions"),
                        b -> minecraft.setScreen(new PrSettingsScreen(Page.FARLANDS, this))));
                done(width / 2 + 5, 150);
                onOptionsChanged();
            }
            case FARLANDS -> {
                grid(PrOptions.FARLANDS_DISTANCE, PrOptions.FARLANDS_FOG, PrOptions.FARLANDS_SOURCE);
                done(width / 2 - 100, 200);
                onOptionsChanged();
            }
            case QUALITY -> {
                grid(PrOptions.MIPMAP_LEVELS, PrOptions.MIPMAP_TYPE,
                        PrOptions.AF_LEVEL, PrOptions.DYNAMIC_FOV,
                        PrOptions.EMISSIVE_TEXTURES, PrOptions.RANDOM_ENTITIES,
                        PrOptions.BETTER_GRASS, PrOptions.BETTER_SNOW,
                        PrOptions.CUSTOM_FONTS, PrOptions.CUSTOM_COLORS,
                        PrOptions.CONNECTED_TEXTURES, PrOptions.NATURAL_TEXTURES,
                        PrOptions.CUSTOM_SKY, PrOptions.CUSTOM_ITEMS,
                        PrOptions.CUSTOM_ENTITY_MODELS, PrOptions.CUSTOM_GUIS,
                        vanilla("options.screenEffectScale", o.screenEffectScale()),
                        vanilla("options.fovEffectScale", o.fovEffectScale()));
                done(width / 2 - 100, 200);
                onOptionsChanged();
            }
            case ANIMATIONS -> {
                grid(PrOptions.ANIMATED_WATER, PrOptions.ANIMATED_LAVA,
                        PrOptions.ANIMATED_FIRE, PrOptions.ANIMATED_PORTAL,
                        PrOptions.ANIMATED_REDSTONE, PrOptions.ANIMATED_EXPLOSION,
                        PrOptions.ANIMATED_FLAME, PrOptions.ANIMATED_SMOKE,
                        PrOptions.VOID_PARTICLES, PrOptions.WATER_PARTICLES,
                        PrOptions.RAIN_SPLASH, PrOptions.PORTAL_PARTICLES,
                        PrOptions.POTION_PARTICLES, PrOptions.DRIPPING_WATER_LAVA,
                        PrOptions.ANIMATED_TERRAIN, PrOptions.ANIMATED_TEXTURES,
                        PrOptions.FIREWORK_PARTICLES, vanilla("options.particles", o.particles()));
                allOnOff(on -> {
                    PryzmaConfig.setAllAnimations(on);
                    o.particles().set(on ? ParticleStatus.ALL : ParticleStatus.MINIMAL);
                });
            }
            case PERFORMANCE -> {
                grid(PrOptions.RENDER_REGIONS, PrOptions.FAST_RENDER,
                        PrOptions.SMART_ANIMATIONS, PrOptions.FAST_MATH,
                        PrOptions.SMOOTH_FPS, PrOptions.SMOOTH_WORLD,
                        PrOptions.CHUNK_UPDATES, vanilla("options.prioritizeChunkUpdates", o.prioritizeChunkUpdates()),
                        PrOptions.FAST_PAINTINGS, PrOptions.ENTITY_CULLING);
                done(width / 2 - 100, 200);
            }
            case OTHER -> {
                grid(PrOptions.LAGOMETER, PrOptions.PROFILER,
                        vanilla("options.attackIndicator", o.attackIndicator()), PrOptions.ADVANCED_TOOLTIPS,
                        PrOptions.WEATHER, PrOptions.TIME,
                        vanilla("options.fullscreen", o.fullscreen()), PrOptions.AUTOSAVE_TICKS,
                        PrOptions.SCREENSHOT_SIZE, PrOptions.SHOW_GL_ERRORS,
                        PrOptions.FEEDBACK_BUTTONS, PrOptions.REMOVE_REALMS,
                        PrOptions.TELEMETRY_ATTRIBUTION, vanilla("options.fullscreen.resolution", fullscreenResolution()),
                        vanilla("options.autosaveIndicator", o.showAutosaveIndicator()), null);
                addRenderableWidget(new PrButton(210, width / 2 - 155, bottomY(), 150,
                        Component.translatable("pr.options.other.reset"), b -> confirmReset()));
                addRenderableWidget(new PrButton(200, width / 2 + 5, bottomY(), 150, CommonComponents.GUI_DONE, b -> {
                    minecraft.getWindow().changeFullscreenVideoMode();
                    onClose();
                }));
            }
            case QUICK_INFO -> {
                grid(PrOptions.QUICK_INFO, PrOptions.QUICK_INFO_FPS,
                        PrOptions.QUICK_INFO_CHUNKS, PrOptions.QUICK_INFO_ENTITIES,
                        PrOptions.QUICK_INFO_PARTICLES, PrOptions.QUICK_INFO_UPDATES,
                        PrOptions.QUICK_INFO_GPU, PrOptions.QUICK_INFO_POS,
                        PrOptions.QUICK_INFO_BIOME, PrOptions.QUICK_INFO_FACING,
                        PrOptions.QUICK_INFO_LIGHT, PrOptions.QUICK_INFO_MEMORY,
                        PrOptions.QUICK_INFO_NATIVE_MEMORY, PrOptions.QUICK_INFO_TARGET_BLOCK,
                        PrOptions.QUICK_INFO_TARGET_FLUID, PrOptions.QUICK_INFO_TARGET_ENTITY,
                        PrOptions.QUICK_INFO_LABELS, PrOptions.QUICK_INFO_BACKGROUND);
                allOnOff(PryzmaConfig::setAllQuickInfos);
                onOptionsChanged();
            }
        }
    }

    private void done(int x, int w) {
        addRenderableWidget(new PrButton(200, x, bottomY(), w, CommonComponents.GUI_DONE, b -> onClose()));
    }

    /** "All ON" (210), "All OFF" (211) and a half-width Done, the 1.x Animations / Quick Info row. */
    private void allOnOff(java.util.function.Consumer<Boolean> apply) {
        int left = width / 2 - 155;
        addRenderableWidget(new PrButton(210, left, bottomY(), 70, Component.translatable("pr.options.animation.allOn"), b -> {
            apply.accept(true);
            rebuildWidgets();
        }));
        addRenderableWidget(new PrButton(211, left + 80, bottomY(), 70, Component.translatable("pr.options.animation.allOff"), b -> {
            apply.accept(false);
            rebuildWidgets();
        }));
        done(width / 2 + 5, 150);
    }

    /** Quick Info and Details: dependent options only respond when their master option allows it. */
    @Override
    void onOptionsChanged() {
        if (page == Page.DETAILS) {
            boolean farlandsOn = PryzmaConfig.isFarlandsActive();
            boolean cloudsOff = PrOptions.isCloudsOff();
            boolean dhActive = net.pryzma.lod.DistantHorizonsBridge.isDhActive();
            for (AbstractWidget w : widgets()) {
                if (w instanceof PrOptionButton b) {
                    if (b.option() == PrOptions.FOG_FANCY) {
                        b.active = !farlandsOn;
                        if (farlandsOn) {
                            b.setMessage(Component.translatable("pr.options.FOG_FANCY")
                                    .append(": ")
                                    .append(CommonComponents.OPTION_OFF));
                        } else {
                            b.refresh();
                        }
                    } else if (b.option() == PrOptions.FOG_START) {
                        b.active = !farlandsOn;
                        if (farlandsOn) {
                            b.setMessage(Component.translatable("pr.options.FOG_START")
                                    .append(": ")
                                    .append(CommonComponents.OPTION_OFF));
                        } else {
                            b.refresh();
                        }
                    }
                } else if (w instanceof PrOptionSlider s && s.option() == PrOptions.CLOUD_HEIGHT) {
                    s.active = !cloudsOff;
                    if (cloudsOff) {
                        s.setMessage(Component.translatable("pr.options.CLOUD_HEIGHT")
                                .append(": ")
                                .append(CommonComponents.OPTION_OFF));
                    } else {
                        s.refresh();
                    }
                } else if (w instanceof PrButton pb && pb.id == 215) {
                    pb.active = !dhActive;
                    if (dhActive) {
                        pb.setMessage(Component.translatable("pr.options.farlands")
                                .append(": ")
                                .append(Component.translatable("pr.options.farlands.dh_active")));
                        pb.setTooltipKey("pr.options.farlands.locked_dh");
                    } else {
                        pb.setMessage(Component.translatable("pr.options.farlandsOptions"));
                        pb.setTooltipKey(null);
                    }
                }
            }
            return;
        }
        if (page == Page.FARLANDS) {
            boolean dhActive = net.pryzma.lod.DistantHorizonsBridge.isDhActive();
            for (AbstractWidget w : widgets()) {
                if (w instanceof PrOptionSlider s && s.option() == PrOptions.FARLANDS_DISTANCE) {
                    s.active = !dhActive;
                    if (dhActive) {
                        s.setMessage(Component.translatable("pr.options.FARLANDS_DISTANCE")
                                .append(": ")
                                .append(Component.translatable("pr.options.farlands.dh_active")));
                    } else {
                        s.refresh();
                    }
                } else if (w instanceof PrOptionButton b && b.option() == PrOptions.FARLANDS_FOG) {
                    b.active = !dhActive;
                    if (dhActive) {
                        b.setMessage(Component.translatable("pr.options.FARLANDS_FOG")
                                .append(": ")
                                .append(Component.translatable("pr.options.farlands.dh_active")));
                    } else {
                        b.refresh();
                    }
                }
            }
            return;
        }
        if (page == Page.QUALITY) {
            boolean mipmapsOff = minecraft.options.mipmapLevels().get() == 0;
            for (AbstractWidget w : widgets()) {
                if (w instanceof PrOptionButton b && b.option() == PrOptions.MIPMAP_TYPE) {
                    b.active = !mipmapsOff;
                    if (mipmapsOff) {
                        b.setMessage(Component.translatable("pr.options.MIPMAP_TYPE")
                                .append(": ")
                                .append(CommonComponents.OPTION_OFF));
                    } else {
                        b.refresh();
                    }
                } else if (w instanceof PrOptionSlider s && s.option() == PrOptions.AF_LEVEL) {
                    s.active = !mipmapsOff;
                    if (mipmapsOff) {
                        s.setMessage(Component.translatable("pr.options.AF_LEVEL")
                                .append(": ")
                                .append(CommonComponents.OPTION_OFF));
                    } else {
                        s.refresh();
                    }
                }
            }
            return;
        }
        if (page != Page.QUICK_INFO) {
            return;
        }
        for (AbstractWidget w : widgets()) {
            if (w instanceof PrOptionButton b && b.option() != PrOptions.QUICK_INFO) {
                b.active = PryzmaConfig.prQuickInfo;
            }
        }
    }

    private void confirmReset() {
        minecraft.setScreen(new ConfirmScreen(yes -> {
            if (yes) {
                resetAll();
            }
            minecraft.setScreen(this);
        }, Component.literal(I18n.get("pr.message.other.reset")), Component.empty()));
    }

    /** Pryzma 1.x "Reset Video Settings": the vanilla video options and every Pryzma option. */
    private void resetAll() {
        Options o = minecraft.options;
        o.renderDistance().set(8);
        o.simulationDistance().set(8);
        o.entityDistanceScaling().set(1.0);
        o.bobView().set(true);
        o.framerateLimit().set(260);
        o.enableVsync().set(false);
        o.mipmapLevels().set(4);
        o.graphicsMode().set(GraphicsStatus.FANCY);
        o.ambientOcclusion().set(true);
        o.cloudStatus().set(CloudStatus.FANCY);
        o.fov().set(70);
        o.gamma().set(0.0);
        o.guiScale().set(0);
        o.particles().set(ParticleStatus.ALL);
        o.forceUnicodeFont().set(false);
        o.prioritizeChunkUpdates().set(PrioritizeChunkUpdates.NONE);
        o.biomeBlendRadius().set(2);
        PryzmaConfig.resetToDefaults();
        // Farlands LOD resets to Off: free its data and rebuild a shader pack built with its Distant Horizons contract.
        PryzmaLod.onSettingsChanged();
        o.save();
        PryzmaConfig.save();
        minecraft.delayTextureReload();
    }

    /** Vanilla's fullscreen resolution slider, a regular grid cell of Other (1.x spanned both columns). */
    private OptionInstance<Integer> fullscreenResolution() {
        Window window = minecraft.getWindow();
        Monitor monitor = window.findBestMonitor();
        int current = monitor == null ? -1
                : window.getPreferredFullscreenVideoMode().map(monitor::getVideoModeIndex).orElse(-1);
        return new OptionInstance<>("options.fullscreen.resolution", OptionInstance.noTooltip(),
                (caption, value) -> {
                    if (monitor == null) {
                        return Component.translatable("options.fullscreen.unavailable");
                    }
                    return value == -1
                            ? Options.genericValueLabel(caption, Component.translatable("options.fullscreen.current"))
                            : Options.genericValueLabel(caption, Component.literal(monitor.getMode(value).toString()));
                },
                new OptionInstance.IntRange(-1, monitor != null ? monitor.getModeCount() - 1 : -1),
                current,
                value -> {
                    if (monitor != null) {
                        Optional<VideoMode> mode = value == -1 ? Optional.empty() : Optional.of(monitor.getMode(value));
                        window.setPreferredFullscreenVideoMode(mode);
                    }
                });
    }

    @Override
    public void onClose() {
        if (page == Page.OTHER) {
            minecraft.getWindow().changeFullscreenVideoMode();
        }
        super.onClose();
    }
}
