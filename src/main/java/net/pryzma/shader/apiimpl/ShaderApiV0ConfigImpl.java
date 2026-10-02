package net.pryzma.shader.apiimpl;

import net.pryzma.shader.PryzmaShaders;
import net.pryzma.shader.api.v0.ShaderApiConfig;
import net.pryzma.shader.config.ShaderConfig;

import java.io.IOException;

public class ShaderApiV0ConfigImpl implements ShaderApiConfig {
	@Override
	public boolean areShadersEnabled() {
		return PryzmaShaders.getShaderConfig().areShadersEnabled();
	}

	@Override
	public void setShadersEnabledAndApply(boolean enabled) {
		ShaderConfig config = PryzmaShaders.getShaderConfig();

		config.setShadersEnabled(enabled);

		try {
			config.save();
		} catch (IOException e) {
			PryzmaShaders.logger.error("Error saving configuration file!", e);
		}

		try {
			PryzmaShaders.reload();
		} catch (IOException e) {
			PryzmaShaders.logger.error("Error reloading shader pack while applying changes!", e);
		}
	}
}
