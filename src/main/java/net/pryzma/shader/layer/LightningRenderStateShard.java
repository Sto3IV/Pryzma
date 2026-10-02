package net.pryzma.shader.layer;

import net.pryzma.shader.shaderpack.materialmap.NamespacedId;
import net.pryzma.shader.shaderpack.materialmap.WorldRenderingSettings;
import net.pryzma.shader.uniforms.CapturedRenderingState;
import net.minecraft.client.renderer.RenderStateShard;

public class LightningRenderStateShard extends RenderStateShard {
	public static final LightningRenderStateShard INSTANCE = new LightningRenderStateShard();
	private static final NamespacedId LIGHT = new NamespacedId("minecraft", "lightning_bolt");
	private static int backupValue = 0;

	public LightningRenderStateShard() {
		super("pryzma:lightning", () -> {
			if (WorldRenderingSettings.INSTANCE.getEntityIds() != null) {
				backupValue = CapturedRenderingState.INSTANCE.getCurrentRenderedEntity();
				CapturedRenderingState.INSTANCE.setCurrentEntity(WorldRenderingSettings.INSTANCE.getEntityIds().applyAsInt(LIGHT));
				GbufferPrograms.runFallbackEntityListener();
			}
		}, () -> {
			if (WorldRenderingSettings.INSTANCE.getEntityIds() != null) {
				CapturedRenderingState.INSTANCE.setCurrentEntity(backupValue);
				backupValue = 0;
				GbufferPrograms.runFallbackEntityListener();
			}
		});
	}
}
