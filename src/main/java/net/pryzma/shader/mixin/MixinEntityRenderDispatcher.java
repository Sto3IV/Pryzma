package net.pryzma.shader.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.objects.Object2IntFunction;
import net.pryzma.shader.PryzmaShaders;
import net.pryzma.shader.pipeline.WorldRenderingPipeline;
import net.pryzma.shader.shaderpack.materialmap.NamespacedId;
import net.pryzma.shader.shaderpack.materialmap.WorldRenderingSettings;
import net.pryzma.shader.uniforms.CapturedRenderingState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(EntityRenderDispatcher.class)
public class MixinEntityRenderDispatcher {
	@Unique
	private static final NamespacedId shadowId = new NamespacedId("minecraft", "entity_shadow");

	@Unique
	private static final NamespacedId flameId = new NamespacedId("minecraft", "entity_flame");

	@WrapOperation(
		method = "submit",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitShadow(Lcom/mojang/blaze3d/vertex/PoseStack;FLjava/util/List;)V")
	)
	private void pryzma$maybeSuppressShadow(SubmitNodeCollector instance, PoseStack poseStack, float radius, List pieces, Operation<Void> original) {
		WorldRenderingPipeline pipeline = PryzmaShaders.getPipelineManager().getPipelineNullable();
		if (pipeline != null && pipeline.shouldDisableVanillaEntityShadows()) {
			return;
		}

		Object2IntFunction<NamespacedId> entityIds = WorldRenderingSettings.INSTANCE.getEntityIds();
		int old = CapturedRenderingState.INSTANCE.getCurrentRenderedEntity();
		if (entityIds != null) {
			CapturedRenderingState.INSTANCE.setCurrentEntity(entityIds.getInt(shadowId));
		}
		try {
			original.call(instance, poseStack, radius, pieces);
		} finally {
			CapturedRenderingState.INSTANCE.setCurrentEntity(old);
		}
	}

	@WrapOperation(
		method = "submit",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitFlame(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lorg/joml/Quaternionf;)V")
	)
	private void pryzma$handleFlame(SubmitNodeCollector instance, PoseStack poseStack, EntityRenderState state, Quaternionf rot, Operation<Void> original) {
		Object2IntFunction<NamespacedId> entityIds = WorldRenderingSettings.INSTANCE.getEntityIds();
		int old = CapturedRenderingState.INSTANCE.getCurrentRenderedEntity();
		if (entityIds != null) {
			CapturedRenderingState.INSTANCE.setCurrentEntity(entityIds.getInt(flameId));
		}
		try {
			original.call(instance, poseStack, state, rot);
		} finally {
			CapturedRenderingState.INSTANCE.setCurrentEntity(old);
		}
	}
}
