package net.pryzma.shader.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;

/**
 * Class serving as abstraction and
 * centralization for common GUI
 * rendering/other code calls.
 * <p>
 * Helps allow for easier portability
 * to Minecraft 1.17 by abstracting
 * some code that will be changed.
 */
public final class GuiUtil {
	public static final ResourceLocation WIDGETS_TEX = ResourceLocation.fromNamespaceAndPath("pryzma", "textures/gui/widgets.png");
	public static final ResourceLocation IRIS_WIDGETS_TEX = WIDGETS_TEX;
	private static final Component ELLIPSIS = Component.literal("...");

	private GuiUtil() {
	}

	private static Minecraft client() {
		return Minecraft.getInstance();
	}

	/**
	 * Binds PryzmaShaders's widgets texture to be
	 * used for succeeding draw calls.
	 */
	public static void bindWidgetsTexture() {
		RenderSystem.setShaderTexture(0, WIDGETS_TEX);
	}

	@Deprecated
	public static void bindIrisWidgetsTexture() {
		bindWidgetsTexture();
	}

	private static final WidgetSprites BUTTON_SPRITES = new WidgetSprites(
		ResourceLocation.withDefaultNamespace("widget/button"),
		ResourceLocation.withDefaultNamespace("widget/button_disabled"),
		ResourceLocation.withDefaultNamespace("widget/button_highlighted")
	);

	/**
	 * Draws a button using vanilla Minecraft button sprites.
	 *
	 * @param x        X position of the left of the button
	 * @param y        Y position of the top of the button
	 * @param width    Width of the button
	 * @param height   Height of the button
	 * @param hovered  Whether the button is being hovered over with the mouse
	 * @param disabled Whether the button should use the "disabled" texture
	 */
	public static void drawButton(GuiGraphics guiGraphics, int x, int y, int width, int height, boolean hovered, boolean disabled) {
		RenderSystem.enableBlend();
		RenderSystem.enableDepthTest();
		guiGraphics.blitSprite(BUTTON_SPRITES.get(!disabled, hovered), x, y, width, height);
	}

	/**
	 * Draws a translucent black panel
	 * with a light border.
	 *
	 * @param x      The x position of the panel
	 * @param y      The y position of the panel
	 * @param width  The width of the panel
	 * @param height The height of the panel
	 */
	public static void drawPanel(GuiGraphics guiGraphics, int x, int y, int width, int height) {
		int borderColor = 0xDEDEDEDE;
		int innerColor = 0xDE000000;

		// Top border section
		guiGraphics.fill(RenderType.guiOverlay(), x, y, x + width, y + 1, borderColor);
		// Bottom border section
		guiGraphics.fill(RenderType.guiOverlay(), x, (y + height) - 1, x + width, y + height, borderColor);
		// Left border section
		guiGraphics.fill(RenderType.guiOverlay(), x, y + 1, x + 1, (y + height) - 1, borderColor);
		// Right border section
		guiGraphics.fill(RenderType.guiOverlay(), (x + width) - 1, y + 1, x + width, (y + height) - 1, borderColor);
		// Inner section
		guiGraphics.fill(RenderType.guiOverlay(), x + 1, y + 1, (x + width) - 1, (y + height) - 1, innerColor);
	}

	/**
	 * Draws a text with a panel behind it.
	 *
	 * @param text The text component to draw
	 * @param x    The x position of the panel
	 * @param y    The y position of the panel
	 */
	public static void drawTextPanel(Font font, GuiGraphics guiGraphics, Component text, int x, int y) {
		drawPanel(guiGraphics, x, y, font.width(text) + 8, 16);
		guiGraphics.drawString(font, text, x + 4, y + 4, 0xFFFFFF);
	}

	/**
	 * Shorten a text to a specific length, adding an ellipsis (...)
	 * to the end if shortened.
	 * <p>
	 * Text may lose formatting.
	 *
	 * @param font  Font to use for determining the width of text
	 * @param text  Text to shorten
	 * @param width Width to shorten text to
	 * @return a shortened text
	 */
	public static MutableComponent shortenText(Font font, MutableComponent text, int width) {
		if (font.width(text) > width) {
			return Component.literal(font.plainSubstrByWidth(text.getString(), width - font.width(ELLIPSIS))).append(ELLIPSIS).setStyle(text.getStyle());
		}
		return text;
	}

	/**
	 * Creates a new translated text, if a translation
	 * is present. If not, will return the default text
	 * component passed.
	 *
	 * @param defaultText     Default text to use if no translation is found
	 * @param translationDesc Translation key to try and use
	 * @param format          Formatting arguments for the translated text, if created
	 * @return the translated text if found, otherwise the default provided
	 */
	public static MutableComponent translateOrDefault(MutableComponent defaultText, String translationDesc, Object... format) {
		if (I18n.exists(translationDesc)) {
			return Component.translatable(translationDesc, format);
		}
		return defaultText;
	}

	/**
	 * Plays the {@code UI_BUTTON_CLICK} sound event as a
	 * master sound effect.
	 * <p>
	 * Used in non-{@code ButtonWidget} UI elements upon click
	 * or other action.
	 */
	public static void playButtonClickSound() {
		client().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1));
	}

	/**
	 * A class representing a section of a
	 * texture, to be easily drawn in GUIs.
	 */
	public static class Icon {
		public static final Icon SEARCH = new Icon(0, 0, 7, 8);
		public static final Icon CLOSE = new Icon(7, 0, 5, 6);
		public static final Icon REFRESH = new Icon(12, 0, 10, 10);
		public static final Icon EXPORT = new Icon(22, 0, 7, 8);
		public static final Icon EXPORT_COLORED = new Icon(29, 0, 7, 8);
		public static final Icon IMPORT = new Icon(22, 8, 7, 8);
		public static final Icon IMPORT_COLORED = new Icon(29, 8, 7, 8);

		private final int u;
		private final int v;
		private final int width;
		private final int height;

		public Icon(int u, int v, int width, int height) {
			this.u = u;
			this.v = v;
			this.width = width;
			this.height = height;
		}

		/**
		 * Draws this icon to the screen at the specified coordinates.
		 *
		 * @param x The x position to draw the icon at (left)
		 * @param y The y position to draw the icon at (top)
		 */
		public void draw(GuiGraphics guiGraphics, int x, int y) {
			// Sets RenderSystem to use solid white as the tint color for blend mode (1.16), and enables blend mode
			RenderSystem.enableBlend();

			// Draw the texture to the screen
			guiGraphics.blit(IRIS_WIDGETS_TEX, x, y, u, v, width, height, 256, 256);
		}

		public int getWidth() {
			return width;
		}

		public int getHeight() {
			return height;
		}
	}
}
