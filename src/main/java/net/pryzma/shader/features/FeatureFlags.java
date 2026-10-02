package net.pryzma.shader.features;

import net.pryzma.shader.gl.ShaderRenderSystem;
import net.minecraft.client.resources.language.I18n;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

public enum FeatureFlags {
	SEPARATE_HARDWARE_SAMPLERS(() -> true, () -> true),
	HIGHER_SHADOWCOLOR(() -> true, () -> true),
	CUSTOM_IMAGES(() -> true, ShaderRenderSystem::supportsImageLoadStore),
	PER_BUFFER_BLENDING(() -> true, ShaderRenderSystem::supportsBufferBlending),
	COMPUTE_SHADERS(() -> true, ShaderRenderSystem::supportsCompute),
	TESSELLATION_SHADERS(() -> true, ShaderRenderSystem::supportsTesselation),
	ENTITY_TRANSLUCENT(() -> true, () -> true),
	REVERSED_CULLING(() -> true, () -> true),
	BLOCK_EMISSION_ATTRIBUTE(() -> true, () -> true),
	CAN_DISABLE_WEATHER(() -> true, () -> true),
	SSBO(() -> true, ShaderRenderSystem::supportsSSBO),
	UNKNOWN(() -> false, () -> false);

	private final BooleanSupplier pipelineRequirement;
	private final BooleanSupplier hardwareRequirement;

	FeatureFlags(BooleanSupplier pipelineRequirement, BooleanSupplier hardwareRequirement) {
		this.pipelineRequirement = pipelineRequirement;
		this.hardwareRequirement = hardwareRequirement;
	}

	public static String getInvalidStatus(List<FeatureFlags> invalidFeatureFlags) {
		boolean unsupportedHardware = false, unsupportedPipeline = false;
		FeatureFlags[] flags = invalidFeatureFlags.toArray(new FeatureFlags[0]);
		for (FeatureFlags flag : flags) {
			unsupportedPipeline |= !flag.pipelineRequirement.getAsBoolean();
			unsupportedHardware |= !flag.hardwareRequirement.getAsBoolean();
		}

		if (unsupportedPipeline) {
			if (unsupportedHardware) {
				return I18n.get("pryzma.unsupported.pipelineorpc");
			}

			return I18n.get("pryzma.unsupported.pipeline");
		} else if (unsupportedHardware) {
			return I18n.get("pryzma.unsupported.pc");
		} else {
			return null;
		}
	}

	public static boolean isInvalid(String name) {
		try {
			return !FeatureFlags.valueOf(name.toUpperCase(Locale.US)).isUsable();
		} catch (IllegalArgumentException e) {
			return true;
		}
	}

	public static FeatureFlags getValue(String value) {
		if (value.equalsIgnoreCase("TESSELATION_SHADERS")) {
			// fix the sins of the past
			value = "TESSELLATION_SHADERS";
		}

		try {
			return FeatureFlags.valueOf(value.toUpperCase(Locale.US));
		} catch (IllegalArgumentException e) {
			return FeatureFlags.UNKNOWN;
		}
	}

	public String getHumanReadableName() {
		return StringUtils.capitalize(name().replace("_", " ").toLowerCase());
	}

	public boolean isUsable() {
		return pipelineRequirement.getAsBoolean() && hardwareRequirement.getAsBoolean();
	}
}
