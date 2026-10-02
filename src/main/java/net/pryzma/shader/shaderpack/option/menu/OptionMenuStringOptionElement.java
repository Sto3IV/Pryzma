package net.pryzma.shader.shaderpack.option.menu;

import net.pryzma.shader.shaderpack.option.StringOption;
import net.pryzma.shader.shaderpack.option.values.OptionValues;
import net.pryzma.shader.shaderpack.properties.ShaderProperties;

public class OptionMenuStringOptionElement extends OptionMenuOptionElement {
	public final StringOption option;

	public OptionMenuStringOptionElement(String elementString, OptionMenuContainer container, ShaderProperties shaderProperties, OptionValues values, StringOption option) {
		super(elementString, container, shaderProperties, values);
		this.option = option;
	}
}
