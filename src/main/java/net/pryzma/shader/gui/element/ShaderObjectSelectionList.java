package net.pryzma.shader.gui.element;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.narration.NarrationElementOutput;

public class ShaderObjectSelectionList<E extends AbstractSelectionList.Entry<E>> extends AbstractSelectionList<E> {
	public ShaderObjectSelectionList(Minecraft client, int width, int height, int top, int bottom, int left, int right, int itemHeight) {
		super(client, width, height, top, itemHeight);
	}

	@Override
	protected int getScrollbarPosition() {
		// Position the scrollbar at the rightmost edge of the screen.
		// By default, the scrollbar is positioned moderately offset from the center.
		return width - 6;
	}

	public void select(int entry) {
		setSelected(this.getEntry(entry));
	}

	@Override
	public void updateWidgetNarration(NarrationElementOutput p0) {

	}
}
