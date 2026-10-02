package net.pryzma.shader.pipeline;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.pryzma.shader.features.FeatureFlags;
import net.pryzma.shader.gl.texture.TextureType;
import net.pryzma.shader.helpers.Tri;
import net.pryzma.shader.mixin.LevelRendererAccessor;
import net.pryzma.shader.shaderpack.properties.CloudSetting;
import net.pryzma.shader.shaderpack.properties.ParticleRenderingSettings;
import net.pryzma.shader.shaderpack.texture.TextureStage;
import net.pryzma.shader.uniforms.FrameUpdateNotifier;
import net.minecraft.client.Camera;

import java.util.List;
import java.util.OptionalInt;

public interface WorldRenderingPipeline {
	void beginLevelRendering();

	void renderShadows(LevelRendererAccessor worldRenderer, Camera camera);

	void addDebugText(List<String> messages);

	OptionalInt getForcedShadowRenderDistanceChunksForDisplay();

	Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> getTextureMap();

	WorldRenderingPhase getPhase();

	void setPhase(WorldRenderingPhase phase);

	void setOverridePhase(WorldRenderingPhase phase);

	int getCurrentNormalTexture();

	int getCurrentSpecularTexture();

	void onSetShaderTexture(int id);

	void beginHand();

	void beginTranslucents();

	void finalizeLevelRendering();

	void finalizeGameRendering();

	void destroy();

	FrameUpdateNotifier getFrameUpdateNotifier();

	boolean shouldDisableVanillaEntityShadows();

	boolean shouldDisableDirectionalShading();

	boolean shouldDisableFrustumCulling();

	boolean shouldDisableOcclusionCulling();

	CloudSetting getCloudSetting();

	boolean shouldRenderUnderwaterOverlay();

	boolean shouldRenderVignette();

	boolean shouldRenderSun();

	boolean shouldRenderWeather();

	boolean shouldRenderWeatherParticles();

	boolean shouldRenderMoon();

	boolean shouldRenderStars();

	boolean shouldRenderSkyDisc();

	boolean shouldWriteRainAndSnowToDepthBuffer();

	ParticleRenderingSettings getParticleRenderingSettings();

	boolean allowConcurrentCompute();

	boolean hasFeature(FeatureFlags flags);

	float getSunPathRotation();

	void setIsMainBound(boolean mainBound);
}
