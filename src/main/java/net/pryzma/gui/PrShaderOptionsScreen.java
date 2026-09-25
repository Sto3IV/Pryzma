package net.pryzma.gui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.pryzma.shader.PrShaderLang;
import net.pryzma.shader.PrShaderOption;
import net.pryzma.shader.PrShaderOptions;
import net.pryzma.shader.PrShaderProperties;
import net.pryzma.shader.PrShaders;

/**
 * A shader pack's options, laid out by its {@code screen} definitions as in OptiFine: option names,
 * {@code [SUB]} links to sub-screens, {@code <profile>}, {@code <empty>} and {@code *} for every
 * option no screen lists. Options named in {@code sliders} are sliders. Changes apply when the
 * top screen closes.
 */
final class PrShaderOptionsScreen extends Screen {
    private static final int TEXT = 0xFFFFFF;
    private static boolean changed;

    private final Screen parent;
    private final String screen;
    private int page;

    PrShaderOptionsScreen(Screen parent, String screen) {
        super(Component.literal(screen.isEmpty() ? "Shader Options"
                : PrShaderLang.get(PrShaders.pack(), "screen." + screen, screen)));
        this.parent = parent;
        this.screen = screen;
        if (screen.isEmpty()) {
            changed = false;
        }
    }

    private sealed interface Entry permits Opt, Sub, Profile, Empty {
    }

    private record Opt(PrShaderOption option) implements Entry {
    }

    private record Sub(String name) implements Entry {
    }

    private record Profile() implements Entry {
    }

    private record Empty() implements Entry {
    }

    private List<Entry> layout() {
        PrShaderOptions options = PrShaders.options();
        Map<String, String> screens = PrShaders.properties().screens();
        String definition = screens.get(screen);
        List<Entry> out = new ArrayList<>();
        if (definition == null) {
            options.visible().forEach(o -> out.add(new Opt(o)));
            return out;
        }
        Set<String> listed = new HashSet<>();
        for (String def : screens.values()) {
            for (String token : def.trim().split("\\s+")) {
                listed.add(token);
            }
        }
        for (String token : definition.trim().split("\\s+")) {
            if (token.isEmpty()) {
                continue;
            }
            switch (token) {
                case "<empty>" -> out.add(new Empty());
                case "<profile>" -> out.add(new Profile());
                case "*" -> options.visible().stream().filter(o -> !listed.contains(o.name())).forEach(o -> out.add(new Opt(o)));
                default -> {
                    if (token.startsWith("[") && token.endsWith("]")) {
                        out.add(new Sub(token.substring(1, token.length() - 1)));
                    } else {
                        PrShaderOption o = options.get(token);
                        if (o != null && o.isVisible()) {
                            out.add(new Opt(o));
                        }
                    }
                }
            }
        }
        return out;
    }

    @Override
    protected void init() {
        PrShaderProperties props = PrShaders.properties();
        List<Entry> entries = layout();
        int columns = props.screens().containsKey(screen) ? props.columns(screen) : 2;
        int gap = 5;
        int columnWidth = Math.min(150, (width - 20) / columns - gap);
        int rows = Math.max(1, (height - 90) / 21);
        int perPage = rows * columns;
        int pages = Math.max(1, (entries.size() + perPage - 1) / perPage);
        page = Math.min(page, pages - 1);
        int left = width / 2 - (columns * (columnWidth + gap) - gap) / 2;
        List<String> sliders = props.sliders();
        for (int i = page * perPage; i < Math.min(entries.size(), (page + 1) * perPage); i++) {
            int slot = i - page * perPage;
            int x = left + slot % columns * (columnWidth + gap);
            int y = 30 + slot / columns * 21;
            AbstractWidget widget = switch (entries.get(i)) {
                case Opt o -> sliders.contains(o.option().name()) ? new OptionSlider(x, y, columnWidth, o.option())
                        : new OptionButton(x, y, columnWidth, o.option());
                case Sub s -> Button.builder(Component.literal(PrShaderLang.get(PrShaders.pack(), "screen." + s.name(), s.name()) + "..."),
                        b -> minecraft.setScreen(new PrShaderOptionsScreen(this, s.name()))).bounds(x, y, columnWidth, 20)
                        .tooltip(comment("screen." + s.name())).build();
                case Profile p -> new ProfileButton(x, y, columnWidth);
                case Empty e -> null;
            };
            if (widget != null) {
                addRenderableWidget(widget);
            }
        }
        int bottom = height - 28;
        if (pages > 1) {
            int total = pages;
            addRenderableWidget(Button.builder(Component.literal("<"), b -> {
                page = (page + total - 1) % total;
                rebuildWidgets();
            }).bounds(width / 2 - 155, bottom - 24, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> {
                page = (page + 1) % total;
                rebuildWidgets();
            }).bounds(width / 2 + 135, bottom - 24, 20, 20).build());
        }
        if (screen.isEmpty()) {
            addRenderableWidget(Button.builder(Component.translatable("controls.reset"), b -> {
                PrShaders.options().resetAll();
                changed = true;
                rebuildWidgets();
            }).bounds(width / 2 - 155, bottom, 150, 20).build());
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(screen.isEmpty() ? width / 2 + 5 : width / 2 - 75, bottom, 150, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 12, TEXT);
    }

    @Override
    public void onClose() {
        if (screen.isEmpty() && changed) {
            PrShaders.saveOptions();
            PrShaders.reload();
        }
        minecraft.setScreen(parent);
    }

    private static Tooltip comment(String key) {
        String text = PrShaderLang.get(PrShaders.pack(), key + ".comment", "");
        return text.isEmpty() ? null : Tooltip.create(Component.literal(text));
    }

    private static Component label(PrShaderOption option) {
        return Component.literal(PrShaderLang.get(PrShaders.pack(), "option." + option.name(), option.name()) + ": "
                + PrShaderLang.value(PrShaders.pack(), option, option.value()));
    }

    /** Cycles an option: left click forward, right click (or Shift) back. */
    private static final class OptionButton extends Button {
        private final PrShaderOption option;

        OptionButton(int x, int y, int w, PrShaderOption option) {
            super(x, y, w, 20, label(option), b -> {
            }, DEFAULT_NARRATION);
            this.option = option;
            setTooltip(comment("option." + option.name()));
        }

        private void step(boolean forward) {
            option.set(option.cycle(forward));
            changed = true;
            setMessage(label(option));
        }

        @Override
        public void onPress() {
            step(!Screen.hasShiftDown());
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 1 && active && visible && isMouseOver(mouseX, mouseY)) {
                playDownSound(net.minecraft.client.Minecraft.getInstance().getSoundManager());
                step(false);
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
    }

    /** An option listed in {@code sliders}: a slider over its values. */
    private static final class OptionSlider extends AbstractSliderButton {
        private final PrShaderOption option;

        OptionSlider(int x, int y, int w, PrShaderOption option) {
            super(x, y, w, 20, label(option), position(option));
            this.option = option;
            setTooltip(comment("option." + option.name()));
        }

        private static double position(PrShaderOption option) {
            int n = option.values().size();
            return n <= 1 ? 0.0 : Math.max(0, option.values().indexOf(option.value())) / (double) (n - 1);
        }

        @Override
        protected void updateMessage() {
            setMessage(label(option));
        }

        @Override
        protected void applyValue() {
            int n = option.values().size();
            String v = option.values().get((int) Math.round(value * (n - 1)));
            if (!v.equals(option.value())) {
                option.set(v);
                changed = true;
            }
        }
    }

    /** The profile the options match, cycling through the pack's profiles. */
    private final class ProfileButton extends Button {
        ProfileButton(int x, int y, int w) {
            super(x, y, w, 20, profileLabel(), b -> {
            }, DEFAULT_NARRATION);
            setTooltip(comment("profile"));
        }

        @Override
        public void onPress() {
            Map<String, String> profiles = PrShaders.properties().profiles();
            if (profiles.isEmpty()) {
                return;
            }
            List<String> names = new ArrayList<>(profiles.keySet());
            String current = PrShaders.options().currentProfile(profiles);
            String next = names.get((names.indexOf(current) + 1) % names.size());
            PrShaders.options().applyProfile(next, profiles, message -> { });
            changed = true;
            rebuildWidgets();
        }
    }

    private static Component profileLabel() {
        Map<String, String> profiles = PrShaders.properties().profiles();
        String current = PrShaders.options().currentProfile(profiles);
        String shown = current == null ? "Custom" : PrShaderLang.get(PrShaders.pack(), "profile." + current, current);
        return Component.literal(PrShaderLang.get(PrShaders.pack(), "profile", "Profile") + ": " + shown);
    }
}
