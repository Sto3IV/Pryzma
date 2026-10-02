package net.pryzma.shader.shaderpack.option.menu;

import net.pryzma.shader.PryzmaShaders;
import net.pryzma.shader.shaderpack.option.OptionSet;
import net.pryzma.shader.shaderpack.option.ProfileSet;
import net.pryzma.shader.shaderpack.option.values.MutableOptionValues;
import net.pryzma.shader.shaderpack.option.values.OptionValues;

public class OptionMenuProfileElement extends OptionMenuElement {
	public final ProfileSet profiles;
	public final OptionSet options;

	private final OptionValues packAppliedValues;

	public OptionMenuProfileElement(ProfileSet profiles, OptionSet options, OptionValues packAppliedValues) {
		this.profiles = profiles;
		this.options = options;
		this.packAppliedValues = packAppliedValues;
	}

	/**
	 * @return an {@link OptionValues} that also contains values currently
	 * pending application.
	 */
	public OptionValues getPendingOptionValues() {
		MutableOptionValues values = packAppliedValues.mutableCopy();
		values.addAll(PryzmaShaders.getShaderPackOptionQueue());

		return values;
	}
}
