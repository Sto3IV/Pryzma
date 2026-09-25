package net.pryzma.gui;

import java.util.List;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.pryzma.shader.PrShaders;

/**
 * Shader pack selection, as OptiFine's Shaders screen: the packs in {@code shaderpacks/} (and
 * "OFF" for Pryzma's own rendering), their options, a reload, and the folder.
 */
public final class PrShaderScreen extends Screen {
    private static final int TEXT = 0xFFFFFF;
    private static final int DIM = 0xA0A0A0;
    private static final int ERROR = 0xFF6060;

    private final Screen parent;
    private PackList list;
    private Button optionsButton;
    private Button reloadButton;

    public PrShaderScreen(Screen parent) {
        super(Component.translatable("pr.shaders.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        list = new PackList(minecraft, width, height - 96, 32, 18);
        list.fill();
        addRenderableWidget(list);
        int y = height - 56;
        optionsButton = addRenderableWidget(Button.builder(Component.translatable("pr.shaders.options"),
                b -> minecraft.setScreen(new PrShaderOptionsScreen(this, ""))).bounds(width / 2 - 155, y, 150, 20).build());
        reloadButton = addRenderableWidget(Button.builder(Component.translatable("pr.shaders.reload"), b -> PrShaders.reload())
                .bounds(width / 2 + 5, y, 150, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("pr.shaders.folder"),
                b -> Util.getPlatform().openFile(PrShaders.packsDir().toFile())).bounds(width / 2 - 155, y + 24, 150, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(width / 2 + 5, y + 24, 150, 20).build());
        updateButtons();
    }

    /** The options of the selected pack. */
    public static Screen optionsScreen(Screen parent) {
        return new PrShaderOptionsScreen(parent, "");
    }

    private void updateButtons() {
        boolean on = PrShaders.enabled();
        optionsButton.active = on && !PrShaders.options().visible().isEmpty();
        reloadButton.active = on;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 12, TEXT);
        List<String> errors = PrShaders.errors();
        Component status;
        if (!PrShaders.enabled()) {
            // A chosen pack that did not load says why: the first error, cut to the screen.
            status = errors.isEmpty() ? Component.translatable("pr.shaders.status.off")
                    : Component.literal(font.plainSubstrByWidth(errors.get(0), width - 40));
        } else {
            status = errors.isEmpty() ? Component.translatable("pr.shaders.status.on", PrShaders.selected())
                    : Component.translatable("pr.shaders.status.errors", errors.size());
        }
        g.drawCenteredString(font, status, width / 2, height - 72, errors.isEmpty() ? DIM : ERROR);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private final class PackList extends ObjectSelectionList<PackList.Entry> {
        PackList(Minecraft minecraft, int width, int height, int y, int itemHeight) {
            super(minecraft, width, height, y, itemHeight);
        }

        void fill() {
            clearEntries();
            addEntry(new Entry(PrShaders.OFF));
            for (String name : PrShaders.available()) {
                addEntry(new Entry(name));
            }
            for (Entry e : children()) {
                if (e.name.equals(PrShaders.selected())) {
                    setSelected(e);
                }
            }
        }

        @Override
        public int getRowWidth() {
            return Math.min(360, width - 40);
        }

        final class Entry extends ObjectSelectionList.Entry<Entry> {
            final String name;

            Entry(String name) {
                this.name = name;
            }

            private Component label() {
                return name.equals(PrShaders.OFF) ? Component.translatable("pr.shaders.off") : Component.literal(name);
            }

            @Override
            public void render(GuiGraphics g, int index, int top, int left, int w, int h, int mouseX, int mouseY, boolean hovered,
                    float partialTick) {
                boolean selected = name.equals(PrShaders.selected());
                g.drawCenteredString(font, label(), left + w / 2, top + (h - font.lineHeight) / 2 + 1, selected ? 0xFFFF80 : TEXT);
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (!name.equals(PrShaders.selected())) {
                    PrShaders.select(name);
                    updateButtons();
                }
                PackList.this.setSelected(this);
                return true;
            }

            @Override
            public Component getNarration() {
                return label();
            }
        }
    }
}
