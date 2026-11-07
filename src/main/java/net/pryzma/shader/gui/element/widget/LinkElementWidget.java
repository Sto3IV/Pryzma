package net.pryzma.shader.gui.element.widget;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.pryzma.shader.gui.GuiUtil;
import net.pryzma.shader.gui.NavigationController;
import net.pryzma.shader.gui.screen.ShaderPackScreen;
import net.pryzma.shader.shaderpack.option.menu.OptionMenuLinkElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenAxis;
import net.minecraft.client.gui.navigation.ScreenDirection;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Optional;

public class LinkElementWidget extends CommentedElementWidget<OptionMenuLinkElement> {
	private static final Component ARROW = Component.literal(">");

	private final String targetScreenId;
	private final MutableComponent label;

	private NavigationController navigation;
	private MutableComponent trimmedLabel = null;
	private boolean isLabelTrimmed = false;

	public LinkElementWidget(OptionMenuLinkElement element) {
		super(element);

		this.targetScreenId = element.targetScreenId;
		this.label = GuiUtil.translateOrDefault(Component.literal(element.targetScreenId), "screen." + element.targetScreenId);
	}

	@Override
	public void init(ShaderPackScreen screen, NavigationController navigation) {
		this.navigation = navigation;
	}

	@Override
	public void render(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float tickDelta, boolean hovered) {
		GuiUtil.bindWidgetsTexture();
		GuiUtil.drawButton(guiGraphics, bounds.position().x(), bounds.position().y(), bounds.width(), bounds.height(), hovered || isFocused(), false);

		Font font = Minecraft.getInstance().font;

		int maxLabelWidth = bounds.width() - 9;

		if (font.width(this.label) > maxLabelWidth) {
			this.isLabelTrimmed = true;
		}

		if (this.trimmedLabel == null) {
			this.trimmedLabel = GuiUtil.shortenText(font, this.label, maxLabelWidth);
		}

		int labelWidth = font.width(this.trimmedLabel);

		guiGraphics.text(font, this.trimmedLabel, bounds.getCenterInAxis(ScreenAxis.HORIZONTAL) - (int) (labelWidth * 0.5) - (int) (0.5 * Math.max(labelWidth - (bounds.width() - 18), 0)), bounds.position().y() + 7, 0xFFFFFF);
		guiGraphics.text(font, ARROW, bounds.getBoundInDirection(ScreenDirection.RIGHT) - 9, bounds.position().y() + 7, 0xFFFFFF);

		if (hovered && this.isLabelTrimmed) {
			// To prevent other elements from being drawn on top of the tooltip
			ShaderPackScreen.TOP_LAYER_RENDER_QUEUE.add(() -> GuiUtil.drawTextPanel(font, guiGraphics, this.label, mouseX + 2, mouseY - 16));
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			this.navigation.open(targetScreenId);
			GuiUtil.playButtonClickSound();

			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == InputConstants.KEY_RETURN) {
			this.navigation.open(targetScreenId);
			GuiUtil.playButtonClickSound();

			return true;
		}

		return super.keyPressed(event);
	}

	@Override
	public Optional<Component> getCommentTitle() {
		return Optional.of(this.label);
	}

	@Override
	public Optional<Component> getCommentBody() {
		String translation = "screen." + this.targetScreenId + ".comment";
		return Optional.ofNullable(net.minecraft.locale.Language.getInstance().has(translation) ? Component.translatable(translation) : null);
	}
}
