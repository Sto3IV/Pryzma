package net.pryzma.shader.gui.element.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.pryzma.shader.gl.uniform.FloatSupplier;
import net.pryzma.shader.gui.GuiUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

public class ShaderButton extends Button {
	private final FloatSupplier alphaSupplier;

	public ShaderButton(int pButton0, int pInt1, int pInt2, int pInt3, Component pComponent4, OnPress pButton$OnPress5, CreateNarration pButton$CreateNarration6, FloatSupplier alpha) {
		super(pButton0, pInt1, pInt2, pInt3, pComponent4, pButton$OnPress5, pButton$CreateNarration6);
		this.alphaSupplier = alpha;
	}

	public static ShaderButton.Builder pryzma$builder(Component pComponent0, Button.OnPress pButton$OnPress1, FloatSupplier alpha) {
		return new ShaderButton.Builder(pComponent0, pButton$OnPress1, alpha);
	}

	@Override
	protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		Minecraft mc = Minecraft.getInstance();
		float alpha = this.alphaSupplier.getAsFloat();
		if (alpha < 0.01f) {
			return;
		}
		guiGraphics.setColor(1.0F, 1.0F, 1.0F, alpha);
		RenderSystem.enableBlend();
		RenderSystem.enableDepthTest();
		guiGraphics.blitSprite(SPRITES.get(this.active, this.isHoveredOrFocused()), this.getX(), this.getY(), this.getWidth(), this.getHeight());
		guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
		int fgColor = this.getFGColor();
		this.renderString(guiGraphics, mc.font, fgColor | Mth.ceil(alpha * 255.0F) << 24);
	}

	public static class Builder {
		private final Component message;
		private final Button.OnPress onPress;
		private final FloatSupplier alpha;
		@Nullable
		private Tooltip tooltip;
		private int x;
		private int y;
		private int width = 150;
		private int height = 20;
		private Button.CreateNarration createNarration = Button.DEFAULT_NARRATION;

		public Builder(Component pButton$Builder0, Button.OnPress pButton$OnPress1, FloatSupplier alpha) {
			this.message = pButton$Builder0;
			this.onPress = pButton$OnPress1;
			this.alpha = alpha;
		}

		public ShaderButton.Builder pos(int pButton$Builder0, int pInt1) {
			this.x = pButton$Builder0;
			this.y = pInt1;
			return this;
		}

		public ShaderButton.Builder width(int pButton$Builder0) {
			this.width = pButton$Builder0;
			return this;
		}

		public ShaderButton.Builder size(int pButton$Builder0, int pInt1) {
			this.width = pButton$Builder0;
			this.height = pInt1;
			return this;
		}

		public ShaderButton.Builder bounds(int pButton$Builder0, int pInt1, int pInt2, int pInt3) {
			return this.pos(pButton$Builder0, pInt1).size(pInt2, pInt3);
		}

		public ShaderButton.Builder tooltip(@Nullable Tooltip pButton$Builder0) {
			this.tooltip = pButton$Builder0;
			return this;
		}

		public ShaderButton.Builder createNarration(Button.CreateNarration pButton$Builder0) {
			this.createNarration = pButton$Builder0;
			return this;
		}

		public ShaderButton build() {
			ShaderButton lvButton1 = new ShaderButton(this.x, this.y, this.width, this.height, this.message, this.onPress, this.createNarration, this.alpha);
			lvButton1.setTooltip(this.tooltip);
			return lvButton1;
		}
	}
}
