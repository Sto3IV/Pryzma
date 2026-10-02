package net.pryzma.shader.shaderpack.option.menu;

import net.pryzma.shader.shaderpack.option.ShaderPackOptions;
import net.pryzma.shader.shaderpack.properties.ShaderProperties;

import java.util.List;
import java.util.Optional;

public class OptionMenuMainElementScreen extends OptionMenuElementScreen {
	public OptionMenuMainElementScreen(OptionMenuContainer container, ShaderProperties shaderProperties, ShaderPackOptions shaderPackOptions, List<String> elementStrings, Optional<Integer> columnCount) {
		super(container, shaderProperties, shaderPackOptions, elementStrings, columnCount);
	}
}
