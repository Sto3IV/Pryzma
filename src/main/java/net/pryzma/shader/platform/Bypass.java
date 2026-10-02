package net.pryzma.shader.platform;

import net.pryzma.shader.vertices.ImmediateState;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.ShaderInstance;

import java.util.function.Supplier;

public class Bypass extends RenderStateShard.ShaderStateShard {
	public Bypass(Supplier<ShaderInstance> original) {
		super(() -> {
			ImmediateState.bypass = true;
			ShaderInstance i = original.get();
			ImmediateState.bypass = false;
			return i;
		});
	}
}
