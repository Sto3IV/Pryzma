package net.pryzma.shader.gl.sampler;

import net.pryzma.shader.gl.ShaderRenderSystem;
import net.pryzma.shader.gl.state.ValueUpdateNotifier;
import net.pryzma.shader.gl.texture.TextureType;

import java.util.function.IntSupplier;

public class SamplerBinding {
	private final int textureUnit;
	private final IntSupplier texture;
	private final ValueUpdateNotifier notifier;
	private final TextureType textureType;
	private final int sampler;

	public SamplerBinding(TextureType type, int textureUnit, IntSupplier texture, GlSampler sampler, ValueUpdateNotifier notifier) {
		this.textureType = type;
		this.textureUnit = textureUnit;
		this.texture = texture;
		this.sampler = sampler == null ? 0 : sampler.getId();
		this.notifier = notifier;
	}

	public void update() {
		updateSampler();

		if (notifier != null) {
			notifier.setListener(this::updateSampler);
		}
	}

	private void updateSampler() {
		ShaderRenderSystem.bindSamplerToUnit(textureUnit, sampler);
		ShaderRenderSystem.bindTextureToUnit(textureType.getGlType(), textureUnit, texture.getAsInt());
	}
}
