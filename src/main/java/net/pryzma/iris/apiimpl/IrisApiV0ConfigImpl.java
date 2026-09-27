package net.pryzma.iris.apiimpl;

import net.pryzma.iris.Iris;
import net.pryzma.iris.api.v0.IrisApiConfig;
import net.pryzma.iris.config.IrisConfig;

import java.io.IOException;

public class IrisApiV0ConfigImpl implements IrisApiConfig {
	@Override
	public boolean areShadersEnabled() {
		return Iris.getIrisConfig().areShadersEnabled();
	}

	@Override
	public void setShadersEnabledAndApply(boolean enabled) {
		IrisConfig config = Iris.getIrisConfig();

		config.setShadersEnabled(enabled);

		try {
			config.save();
		} catch (IOException e) {
			Iris.logger.error("Error saving configuration file!", e);
		}

		try {
			Iris.reload();
		} catch (IOException e) {
			Iris.logger.error("Error reloading shader pack while applying changes!", e);
		}
	}
}
