package net.pryzma.shader.uniforms;

import com.mojang.renderpearl.backend.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.pryzma.shader.gl.uniform.DynamicUniformHolder;
import org.joml.Vector2f;

public class VanillaUniforms {
	public static void addVanillaUniforms(DynamicUniformHolder uniforms) {
		Vector2f cachedScreenSize = new Vector2f();
		// listener -> {} dictates we want this to run on every shader update, not just on a new frame. These are dynamic.
		uniforms.uniform1f("iris_LineWidth", () -> 1.0F, listener -> {
		});
		uniforms.uniform2f("iris_ScreenSize", () -> cachedScreenSize.set(net.minecraft.client.Minecraft.getInstance().getWindow().getWidth(), net.minecraft.client.Minecraft.getInstance().getWindow().getHeight()), listener -> {
		});
	}
}
