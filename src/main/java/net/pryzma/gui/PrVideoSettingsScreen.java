package net.pryzma.gui;

import java.util.List;

import com.mojang.blaze3d.platform.Window;

import org.lwjgl.sdl.SDLMouse;

import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;
import net.pryzma.Pryzma;
import net.pryzma.shader.gui.screen.ShaderPackScreen;

/**
 * The Pryzma video settings screen, laid out exactly as in Pryzma 1.x: eleven options in the
 * two-column grid with Quick Info in the twelfth cell, the six page buttons below it and Done.
 */
public final class PrVideoSettingsScreen extends PrScreen {
    private static final int VERSION_COLOR = 0xA0A0A0;

    private int mipmapsApplied;
    private AbstractWidget guiScale;
    /** Set while leaving for a page or the shader screen, which come back here. */
    private boolean toChild;

    public PrVideoSettingsScreen(Screen parent) {
        super(Component.translatable("options.videoTitle"), parent);
        this.mipmapsApplied = Minecraft.getInstance().options.mipmapLevels().get();
    }

    @Override
    protected void init() {
        toChild = false;
        Options o = minecraft.options;
        List<AbstractWidget> cells = grid(
                PrOptions.GRAPHICS, vanilla("options.renderDistance", o.renderDistance()),
                vanilla("options.ao", o.ambientOcclusion()), vanilla("options.simulationDistance", o.simulationDistance()),
                PrOptions.AO_LEVEL, PrOptions.FRAMERATE_LIMIT,
                vanilla("options.guiScale", o.guiScale()), null,
                // Celian: Menu Background Blur has zero business hiding in the Items tab. Put it here next to Brightness.
                // Ranni: Restored to root video grid. Semantic order is satisfied.
                vanilla("options.gamma", o.gamma()), vanilla("options.menuBackgroundBlurriness", o.menuBackgroundBlurriness()),
                PrOptions.DYNAMIC_LIGHTS, null);
        guiScale = cells.get(6);

        addRenderableWidget(new PrButton(225, cellX(7), cellY(7), 150, Component.translatable("pr.options.items"),
                b -> open(PrSettingsScreen.Page.ITEMS)));
        addRenderableWidget(new PrButton(220, cellX(11), cellY(11), 150, Component.translatable("pr.options.quickInfo"),
                b -> open(PrSettingsScreen.Page.QUICK_INFO)));
        int y = cellY(12);
        int left = width / 2 - 155;
        int right = left + 160;
        addRenderableWidget(new PrButton(231, left, y, 150, Component.translatable("pr.options.shaders"), b -> openShaders()));
        addRenderableWidget(new PrButton(202, right, y, 150, Component.translatable("pr.options.quality"),
                b -> open(PrSettingsScreen.Page.QUALITY)));
        y += 21;
        addRenderableWidget(new PrButton(201, left, y, 150, Component.translatable("pr.options.details"),
                b -> open(PrSettingsScreen.Page.DETAILS)));
        addRenderableWidget(new PrButton(212, right, y, 150, Component.translatable("pr.options.performance"),
                b -> open(PrSettingsScreen.Page.PERFORMANCE)));
        y += 21;
        addRenderableWidget(new PrButton(211, left, y, 150, Component.translatable("pr.options.animations"),
                b -> open(PrSettingsScreen.Page.ANIMATIONS)));
        addRenderableWidget(new PrButton(222, right, y, 150, Component.translatable("pr.options.other"),
                b -> open(PrSettingsScreen.Page.OTHER)));
        addRenderableWidget(new PrButton(200, width / 2 - 100, bottomY(), 200, CommonComponents.GUI_DONE, b -> onClose()));
    }

    private void open(PrSettingsScreen.Page page) {
        toChild = true;
        minecraft.gui.setScreen(new PrSettingsScreen(page, this));
    }

    /** The shader pack screen of the Pryzma Shaders; it returns here. */
    private void openShaders() {
        toChild = true;
        minecraft.gui.setScreen(new ShaderPackScreen(this));
    }

    /** 1.x: right-click lowers GUI Scale, wrapping from Auto to the largest scale that fits. */
    @Override
    boolean onRightClick(AbstractWidget widget) {
        if (widget != guiScale) {
            return false;
        }
        Window window = minecraft.getWindow();
        int scale = minecraft.options.guiScale().get() - 1;
        if (scale < 0) {
            scale = window.calculateScale(0, minecraft.isEnforceUnicode());
        }
        minecraft.options.guiScale().set(scale);
        keepCursorOnGuiScale();
        return true;
    }

    /** The GUI scale changed under the cursor; put the cursor back on the (moved) button. */
    private void keepCursorOnGuiScale() {
        minecraft.resizeGui();
        Window window = minecraft.getWindow();
        int x = guiScale.getX() + (guiScale.getWidth() - guiScale.getHeight());
        int y = guiScale.getY() + guiScale.getHeight() / 2;
        SDLMouse.SDL_WarpMouseInWindow(window.handle(), (float) (x * window.getGuiScale()), (float) (y * window.getGuiScale()));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        String version = "Pryzma " + ModList.get().getModContainerById(Pryzma.MODID)
                .map(c -> c.getModInfo().getVersion().toString()).orElse("2.3.0");
        g.text(font, version, 2, height - 10, VERSION_COLOR);
        String mc = "Minecraft " + SharedConstants.getCurrentVersion().name();
        g.text(font, mc, width - font.width(mc) - 2, height - 10, VERSION_COLOR);
    }

    /** Vanilla applies a new mipmap level when its video settings close; so does this screen. */
    @Override
    public void removed() {
        super.removed();
        int mipmaps = minecraft.options.mipmapLevels().get();
        if (mipmaps != mipmapsApplied && !toChild) {
            mipmapsApplied = mipmaps;
            minecraft.updateMaxMipLevel(mipmaps);
            minecraft.delayTextureReload();
        }
    }
}
