package net.pryzma.shader.mixin;

import net.pryzma.shader.uniforms.CapturedRenderingState;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
public class MixinFogRenderer {
	@Inject(method = "setupFog", at = @At("RETURN"))
	private void pryzma$captureFog(Camera camera, int renderDistanceChunks, DeltaTracker deltaTracker, float farPlane, ClientLevel level, CallbackInfoReturnable<FogData> cir) {
		FogData fogData = cir.getReturnValue();
		if (fogData != null) {
			if (fogData.color != null) {
				CapturedRenderingState.INSTANCE.setFogColor(fogData.color.x, fogData.color.y, fogData.color.z);
			}
			CapturedRenderingState.INSTANCE.setFogStart(Math.min(fogData.environmentalStart, fogData.renderDistanceStart));
			CapturedRenderingState.INSTANCE.setFogEnd(Math.min(fogData.environmentalEnd, fogData.renderDistanceEnd));
		}

		if (camera.getFluidInCamera() == FogType.WATER) {
			Entity entity = camera.entity();
			float density = 0.05F;
			if (entity instanceof LocalPlayer localPlayer) {
				density -= localPlayer.getWaterVision() * localPlayer.getWaterVision() * 0.03F;
				Holder<Biome> biome = localPlayer.level().getBiome(localPlayer.blockPosition());
				if (biome.is(TagKey.create(Registries.BIOME, Identifier.withDefaultNamespace("has_closer_water_fog")))) {
					density += 0.005F;
				}
			}
			CapturedRenderingState.INSTANCE.setFogDensity(density);
		} else {
			CapturedRenderingState.INSTANCE.setFogDensity(-1.0F);
		}
	}
}
