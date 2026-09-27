package net.pryzma.iris.shaderpack.option.menu;

import net.pryzma.iris.shaderpack.option.BooleanOption;
import net.pryzma.iris.shaderpack.option.values.OptionValues;
import net.pryzma.iris.shaderpack.properties.ShaderProperties;

public class OptionMenuBooleanOptionElement extends OptionMenuOptionElement {
	public final BooleanOption option;

	public OptionMenuBooleanOptionElement(String elementString, OptionMenuContainer container, ShaderProperties shaderProperties, OptionValues values, BooleanOption option) {
		super(elementString, container, shaderProperties, values);
		this.option = option;
	}
}
