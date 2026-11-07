package net.pryzma.shader.mixin;

import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.pryzma.shader.PryzmaShaders;
import net.pryzma.shader.gl.ShaderRenderSystem;
import net.pryzma.shader.pathways.HandRenderer;
import net.pryzma.shader.pipeline.WorldRenderingPhase;
import net.pryzma.shader.pipeline.WorldRenderingPipeline;
import net.pryzma.shader.shadows.frustum.fallback.NonCullingFrustum;
import net.pryzma.shader.uniforms.CapturedRenderingState;
import net.pryzma.shader.uniforms.ShaderTimeUniforms;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL43C;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class MixinLevelRenderer {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Shadow
	@Final
	private LevelRenderState levelRenderState;

	@Shadow
	private @Nullable ClientLevel level;

	@Unique
	private WorldRenderingPipeline pipeline;

	@Unique
	private NonCullingFrustum overrideFrustum;

	@Unique
	private CameraRenderState currentCameraRenderState;

	@Inject(method = "render", at = @At("HEAD"))
	private void pryzma$setupPipeline(GraphicsResourceAllocator allocator, boolean tick,
									CameraRenderState cameraRenderState, GpuBufferSlice bufferSlice,
									Vector4f clearColor, boolean bl, boolean bl2, CallbackInfo ci) {
		this.currentCameraRenderState = cameraRenderState;
		ShaderTimeUniforms.updateTime();
		CapturedRenderingState.INSTANCE.setGbufferModelView(cameraRenderState.viewRotationMatrix);
		CapturedRenderingState.INSTANCE.setGbufferProjection(cameraRenderState.projectionMatrix);
		CapturedRenderingState.INSTANCE.setTickDelta(cameraRenderState.cameraEntityPartialTicks);
		float time = (float) (level != null ? level.getGameTime() : 0L) + cameraRenderState.cameraEntityPartialTicks;
		CapturedRenderingState.INSTANCE.setCloudTime(time * 0.03F);

		pipeline = PryzmaShaders.getPipelineManager().preparePipeline(PryzmaShaders.getCurrentDimension());

		if (pipeline.shouldDisableFrustumCulling()) {
			this.overrideFrustum = new NonCullingFrustum();
			this.overrideFrustum.prepare(cameraRenderState.pos.x, cameraRenderState.pos.y, cameraRenderState.pos.z);
			cameraRenderState.cullFrustum = overrideFrustum;
		} else {
			this.overrideFrustum = null;
		}

		ShaderRenderSystem.backupAndDisableCullingState(pipeline.shouldDisableOcclusionCulling());

		if (PryzmaShaders.shouldActivateWireframe() && this.minecraft.isLocalServer()) {
			ShaderRenderSystem.setPolygonMode(GL43C.GL_LINE);
		}

		pipeline.beginLevelRendering();
		pipeline.setPhase(WorldRenderingPhase.NONE);
	}

	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;addSkyPass(Lcom/mojang/blaze3d/framegraph/FrameGraphBuilder;Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;)V"))
	private void pryzma$renderTerrainShadows(GraphicsResourceAllocator allocator, boolean tick,
											CameraRenderState cameraRenderState, GpuBufferSlice bufferSlice,
											Vector4f clearColor, boolean bl, boolean bl2, CallbackInfo ci) {
		Camera camera = this.minecraft.gameRenderer.mainCamera();
		pipeline.renderShadows((LevelRendererAccessor) this, camera);
	}

	@Inject(method = "prepareTranslucents", at = @At("HEAD"))
	private void pryzma$beginTranslucents(CallbackInfo ci) {
		if (pipeline == null) return;
		Camera camera = this.minecraft.gameRenderer.mainCamera();
		CameraRenderState cameraRenderState = this.currentCameraRenderState != null ? this.currentCameraRenderState : this.levelRenderState.cameraRenderState;
		Matrix4f modelMatrix = cameraRenderState != null && cameraRenderState.viewRotationMatrix != null ? cameraRenderState.viewRotationMatrix : new Matrix4f();
		float tickDelta = cameraRenderState != null ? cameraRenderState.cameraEntityPartialTicks : 0.0f;

		pipeline.beginHand();
		HandRenderer.INSTANCE.renderSolid(modelMatrix, tickDelta, camera, this.minecraft.gameRenderer, pipeline);
		net.minecraft.util.profiling.Profiler.get().popPush("pryzma_pre_translucent");
		pipeline.beginTranslucents();
	}

	@Inject(method = "render", at = @At(value = "RETURN"))
	private void pryzma$endLevelRender(GraphicsResourceAllocator allocator, boolean tick,
									CameraRenderState cameraRenderState, GpuBufferSlice bufferSlice,
									Vector4f clearColor, boolean bl, boolean bl2, CallbackInfo ci) {
		Camera camera = this.minecraft.gameRenderer.mainCamera();
		HandRenderer.INSTANCE.renderTranslucent(cameraRenderState.viewRotationMatrix, cameraRenderState.cameraEntityPartialTicks, camera, this.minecraft.gameRenderer, pipeline);
		net.minecraft.util.profiling.Profiler.get().popPush("iris_final");
		pipeline.finalizeLevelRendering();
		pipeline = null;
		this.currentCameraRenderState = null;

		ShaderRenderSystem.restoreCullingState();

		if (PryzmaShaders.shouldActivateWireframe() && this.minecraft.isLocalServer()) {
			ShaderRenderSystem.setPolygonMode(GL43C.GL_FILL);
		}
	}
}
