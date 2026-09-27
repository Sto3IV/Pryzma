package net.pryzma.iris.shaderpack.option.menu;

import net.pryzma.iris.Iris;
import net.pryzma.iris.shaderpack.option.values.MutableOptionValues;
import net.pryzma.iris.shaderpack.option.values.OptionValues;
import net.pryzma.iris.shaderpack.properties.ShaderProperties;

public abstract class OptionMenuOptionElement extends OptionMenuElement {
	public final boolean slider;
	public final OptionMenuContainer container;
	public final String optionId;

	private final OptionValues packAppliedValues;

	public OptionMenuOptionElement(String elementString, OptionMenuContainer container, ShaderProperties shaderProperties, OptionValues packAppliedValues) {
		this.slider = shaderProperties.getSliderOptions().contains(elementString);
		this.container = container;
		this.optionId = elementString;
		this.packAppliedValues = packAppliedValues;
	}

	/**
	 * @return the {@link OptionValues} currently in use by the shader pack
	 */
	public OptionValues getAppliedOptionValues() {
		return packAppliedValues;
	}

	/**
	 * @return an {@link OptionValues} that also contains values currently
	 * pending application.
	 */
	public OptionValues getPendingOptionValues() {
		MutableOptionValues values = getAppliedOptionValues().mutableCopy();
		values.addAll(Iris.getShaderPackOptionQueue());

		return values;
	}
}
