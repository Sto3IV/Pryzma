package net.pryzma.shader.config;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.pryzma.shader.PryzmaShaders;
import net.pryzma.shader.gui.option.ShaderVideoSettings;
import net.pryzma.shader.pathways.colorspace.ColorSpace;
import net.pryzma.shader.platform.ShaderPlatformHelpers;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

/**
 * A class dedicated to storing the config values of shaderpacks. Right now it only stores the path to the current shaderpack
 */
public class ShaderConfig {
	/** Pryzma's shader settings file in the config directory. */
	public static final String FILE_NAME = "pryzma-shaders.properties";
	private static final String COMMENT =
		"This file stores configuration options for Pryzma shaders (Pryzma Shaders), such as the currently active shaderpack";
	private final Path propertiesPath;
	private final Path excludedPath;
	/**
	 * The path to the current shaderpack. Null if the internal shaderpack is being used.
	 */
	private String shaderPackName;
	/**
	 * Whether or not shaders are used for rendering. False to disable all shader-based rendering, true to enable it.
	 */
	private boolean enableShaders;
	/**
	 * Whether or not to allow core shaders to draw to the main color texture.
	 */
	private boolean allowUnknownShaders;
	/**
	 * If debug features should be enabled. Gives much more detailed OpenGL error outputs at the cost of performance.
	 */
	private boolean enableDebugOptions;
	/**
	 * What shaders should be nuked.
	 */
	private List<ResourceLocation> shadersToSkip = new ArrayList<>();
	/**
	 * If the update notification should be disabled or not.
	 */
	private boolean disableUpdateMessage;

	public ShaderConfig(Path propertiesPath, Path excluded) {
		shaderPackName = null;
		enableShaders = true;
		allowUnknownShaders = false;
		enableDebugOptions = false;
		disableUpdateMessage = false;
		this.propertiesPath = propertiesPath;
		this.excludedPath = excluded;
	}

	/**
	 * Initializes the configuration, loading it if it is present and creating a default config otherwise.
	 *
	 * @throws IOException file exceptions
	 */
	public void initialize() throws IOException {
		if (!Files.exists(propertiesPath)) {
			importLegacySelection();
		}
		load();
		if (!Files.exists(propertiesPath)) {
			save();
		}
	}

	/**
	 * First start without a settings file: the selection made for Pryzma ({@code config/pryzma-shaders.properties}) or for
	 * OptiFine and Pryzma's previous engine ({@code optionsshaders.txt}, {@code OFF} meaning none) carries over.
	 */
	private void importLegacySelection() {
		Path legacyConfig = propertiesPath.resolveSibling("iris.properties");
		Path optifine = ShaderPlatformHelpers.getInstance().getGameDir().resolve("optionsshaders.txt");
		try {
			if (Files.exists(legacyConfig)) {
				Files.copy(legacyConfig, propertiesPath);
				PryzmaShaders.logger.info("Imported shader settings from " + legacyConfig.getFileName());
			} else if (Files.exists(optifine)) {
				Properties legacy = new Properties();
				try (InputStream is = Files.newInputStream(optifine)) {
					legacy.load(is);
				}
				Optional<String> pack = legacyPackSelection(legacy);
				if (pack.isPresent()) {
					shaderPackName = pack.get();
					save();
					PryzmaShaders.logger.info("Imported shader pack selection " + pack.get() + " from " + optifine);
				}
			}
		} catch (IOException e) {
			PryzmaShaders.logger.warn("Could not import the previous shader settings", e);
		}
	}

	/** The {@code shaderPack} entry of {@code optionsshaders.txt}; empty for none ({@code OFF}, {@code (internal)}, blank). */
	static Optional<String> legacyPackSelection(Properties optionsShaders) {
		String pack = optionsShaders.getProperty("shaderPack", "OFF").trim();
		return pack.isEmpty() || pack.equals("OFF") || pack.equals("(internal)") ? Optional.empty() : Optional.of(pack);
	}

	/**
	 * returns whether or not the current shaderpack is internal
	 *
	 * @return if the shaderpack is internal
	 */
	public boolean isInternal() {
		return false;
	}

	/**
	 * Returns the name of the current shaderpack
	 *
	 * @return Returns the current shaderpack name - if internal shaders are being used it returns "(internal)"
	 */
	public Optional<String> getShaderPackName() {
		return Optional.ofNullable(shaderPackName);
	}

	/**
	 * Sets the name of the current shaderpack
	 */
	public void setShaderPackName(String name) {
		if (name == null || name.equals("(internal)") || name.isEmpty()) {
			this.shaderPackName = null;
		} else {
			this.shaderPackName = name;
		}
	}

	/**
	 * Determines whether or not shaders are used for rendering.
	 *
	 * @return False to disable all shader-based rendering, true to enable shader-based rendering.
	 */
	public boolean areShadersEnabled() {
		return enableShaders;
	}

	public boolean areDebugOptionsEnabled() {
		return enableDebugOptions;
	}

	public boolean shouldDisableUpdateMessage() {
		return disableUpdateMessage;
	}

	public void setDebugEnabled(boolean enabled) {
		enableDebugOptions = enabled;
	}

	/**
	 * Sets whether shaders should be used for rendering.
	 */
	public void setShadersEnabled(boolean enabled) {
		this.enableShaders = enabled;
	}

	private static Gson GSON = new Gson();

	/**
	 * loads the config file and then populates the string, int, and boolean entries with the parsed entries
	 *
	 * @throws IOException if the file cannot be loaded
	 */

	public void load() throws IOException {
		if (Files.exists(excludedPath)) {
			JsonArray json = JsonParser.parseString(Files.readString(excludedPath)).getAsJsonObject().getAsJsonArray("excluded");
			for (int i = 0; i < json.size(); i++) {
				ResourceLocation resource = ResourceLocation.tryParse(json.get(i).getAsString());
				if (resource == null) {
					PryzmaShaders.logger.warn("Unknown shader " + json.get(i).getAsString());
				}

				shadersToSkip.add(resource);
			}
		} else {
			JsonObject defaultV = new JsonObject();
			JsonArray array = new JsonArray();
			array.add("put:valuesHere");
			defaultV.add("excluded", array);
			Files.writeString(excludedPath, GSON.toJson(defaultV));
		}

		if (!Files.exists(propertiesPath)) {
			return;
		}

		Properties properties = new Properties();
		// NB: This uses ISO-8859-1 with unicode escapes as the encoding
		try (InputStream is = Files.newInputStream(propertiesPath)) {
			properties.load(is);
		}

		shaderPackName = properties.getProperty("shaderPack");
		enableShaders = !"false".equals(properties.getProperty("enableShaders"));
		allowUnknownShaders = "true".equals(properties.getProperty("allowUnknownShaders"));
		enableDebugOptions = "true".equals(properties.getProperty("enableDebugOptions"));
		disableUpdateMessage = "true".equals(properties.getProperty("disableUpdateMessage"));
		try {
			ShaderVideoSettings.shadowDistance = Integer.parseInt(properties.getProperty("maxShadowRenderDistance", "32"));
			ShaderVideoSettings.colorSpace = ColorSpace.valueOf(properties.getProperty("colorSpace", "SRGB"));
		} catch (IllegalArgumentException e) {
			PryzmaShaders.logger.error("Shadow distance setting reset; value is invalid.");
			ShaderVideoSettings.shadowDistance = 32;
			ShaderVideoSettings.colorSpace = ColorSpace.SRGB;
			save();
		}

		if (shaderPackName != null) {
			if (shaderPackName.equals("(internal)") || shaderPackName.isEmpty()) {
				shaderPackName = null;
			}
		}
	}

	/**
	 * Serializes the config into a file. Should be called whenever any config values are modified.
	 *
	 * @throws IOException file exceptions
	 */
	public void save() throws IOException {
		Properties properties = new Properties();
		properties.setProperty("shaderPack", getShaderPackName().orElse(""));
		properties.setProperty("enableShaders", enableShaders ? "true" : "false");
		properties.setProperty("allowUnknownShaders", allowUnknownShaders ? "true" : "false");
		properties.setProperty("enableDebugOptions", enableDebugOptions ? "true" : "false");
		properties.setProperty("disableUpdateMessage", disableUpdateMessage ? "true" : "false");
		properties.setProperty("maxShadowRenderDistance", String.valueOf(ShaderVideoSettings.shadowDistance));
		properties.setProperty("colorSpace", ShaderVideoSettings.colorSpace.name());
		// NB: This uses ISO-8859-1 with unicode escapes as the encoding
		try (OutputStream os = Files.newOutputStream(propertiesPath)) {
			properties.store(os, COMMENT);
		}
	}

	public boolean shouldAllowUnknownShaders() {
		return allowUnknownShaders;
	}

	public boolean shouldSkip(ResourceLocation value) {
		return shadersToSkip.contains(value); // TODO
	}

	public void setUnknown(boolean b) throws IOException {
		this.allowUnknownShaders = b;
		save();
	}
}
