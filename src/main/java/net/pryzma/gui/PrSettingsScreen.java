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

/** The six Pryzma 1.x settings pages, each with its own grid and bottom row. */
final class PrSettingsScreen extends PrScreen {
    enum Page {
        DETAILS("pr.options.detailsTitle"),
        QUALITY("pr.options.qualityTitle"),
        ANIMATIONS("pr.options.animationsTitle"),
        PERFORMANCE("pr.options.performanceTitle"),
        OTHER("pr.options.otherTitle"),
        QUICK_INFO("pr.options.quickInfoTitle");

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
                        vanilla("options.autosaveIndicator", o.showAutosaveIndicator()), PrOptions.SWAMP_COLORS,
                        PrOptions.VIGNETTE, PrOptions.ALTERNATE_BLOCKS,
                        vanilla("options.entityDistanceScaling", o.entityDistanceScaling()),
                        vanilla("options.biomeBlendRadius", o.biomeBlendRadius()));
                done(width / 2 - 100, 200);
            }
            case QUALITY -> {
                grid(PrOptions.MIPMAP_LEVELS, PrOptions.MIPMAP_TYPE,
                        PrOptions.AF_LEVEL, PrOptions.AA_LEVEL,
                        PrOptions.EMISSIVE_TEXTURES, PrOptions.RANDOM_ENTITIES,
                        PrOptions.BETTER_GRASS, PrOptions.BETTER_SNOW,
                        PrOptions.CUSTOM_FONTS, PrOptions.CUSTOM_COLORS,
                        PrOptions.CONNECTED_TEXTURES, PrOptions.NATURAL_TEXTURES,
                        PrOptions.CUSTOM_SKY, PrOptions.CUSTOM_ITEMS,
                        PrOptions.CUSTOM_ENTITY_MODELS, PrOptions.CUSTOM_GUIS,
                        vanilla("options.screenEffectScale", o.screenEffectScale()),
                        vanilla("options.fovEffectScale", o.fovEffectScale()));
                done(width / 2 - 100, 200);
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
                        PrOptions.CHUNK_UPDATES, PrOptions.LAZY_CHUNK_LOADING,
                        vanilla("options.prioritizeChunkUpdates", o.prioritizeChunkUpdates()), PrOptions.FAST_PAINTINGS);
                done(width / 2 - 100, 200);
            }
            case OTHER -> {
                grid(PrOptions.LAGOMETER, PrOptions.PROFILER,
                        vanilla("options.attackIndicator", o.attackIndicator()), PrOptions.ADVANCED_TOOLTIPS,
                        PrOptions.WEATHER, PrOptions.TIME,
                        vanilla("options.fullscreen", o.fullscreen()), PrOptions.AUTOSAVE_TICKS,
                        PrOptions.SCREENSHOT_SIZE, PrOptions.SHOW_GL_ERRORS,
                        PrOptions.FEEDBACK_BUTTONS, null);
                addOption(vanilla("options.fullscreen.resolution", fullscreenResolution()), cellX(12), cellY(12), 310);
                addRenderableWidget(new PrButton(210, width / 2 - 100, bottomY() - 44, 200,
                        Component.translatable("pr.options.other.reset"), b -> confirmReset()));
                addRenderableWidget(new PrButton(200, width / 2 - 100, bottomY(), 200, CommonComponents.GUI_DONE, b -> {
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

    /** Quick Info: the detail options only take effect, and only respond, while Quick Info is on. */
    @Override
    void onOptionsChanged() {
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
        o.save();
        PryzmaConfig.save();
        minecraft.delayTextureReload();
    }

    /** Vanilla's fullscreen resolution slider, which 1.x placed across both columns of Other. */
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
}
