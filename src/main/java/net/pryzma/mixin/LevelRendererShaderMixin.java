package net.pryzma.mixin;

import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.pryzma.shader.PrShaderPipeline;
import net.pryzma.shader.PrShaders;

/**
 * The shader pack pipeline inside world rendering: the gbuffers are bound right after the frame's
 * clear, deferred passes run before translucent terrain, and sky, clouds, weather and world border
 * are marked so their draws use the matching gbuffers programs.
 */
@Mixin(LevelRenderer.class)
abstract class LevelRendererShaderMixin {
    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;clear(IZ)V", shift = At.Shift.AFTER))
    private void prShaderBegin(DeltaTracker delta, boolean outline, Camera camera, GameRenderer gameRenderer, LightTexture light,
            Matrix4f modelView, Matrix4f projection, CallbackInfo ci) {
        PrShaders.beginLevel((LevelRenderer) (Object) this, camera, modelView, projection, delta.getGameTimeDeltaPartialTick(false));
    }

    @Inject(method = "renderSectionLayer", at = @At("HEAD"))
    private void prShaderTranslucent(RenderType type, double x, double y, double z, Matrix4f modelView, Matrix4f projection, CallbackInfo ci) {
        if (type == RenderType.translucent()) {
            PrShaders.beforeTranslucent();
        }
    }

    @WrapMethod(method = "renderSky")
    private void prShaderSky(Matrix4f modelView, Matrix4f projection, float partialTick, Camera camera, boolean foggy,
            Runnable fog, Operation<Void> original) {
        PrShaderPipeline.Phase previous = PrShaders.phase();
        PrShaders.phase(PrShaderPipeline.Phase.SKY);
        if (PrShaders.rendering()) {
            // The sky dome draws with whatever shader is current; select the position shader so the pack's sky program applies.
            RenderSystem.setShader(GameRenderer::getPositionShader);
        }
        try {
            original.call(modelView, projection, partialTick, camera, foggy, fog);
        } finally {
            PrShaders.phase(previous);
        }
    }

    @WrapMethod(method = "renderClouds")
    private void prShaderClouds(PoseStack pose, Matrix4f modelView, Matrix4f projection, float partialTick, double x, double y,
            double z, Operation<Void> original) {
        PrShaderPipeline.Phase previous = PrShaders.phase();
        PrShaders.phase(PrShaderPipeline.Phase.CLOUDS);
        try {
            original.call(pose, modelView, projection, partialTick, x, y, z);
        } finally {
            PrShaders.phase(previous);
        }
    }

    @WrapMethod(method = "renderSnowAndRain")
    private void prShaderWeather(LightTexture light, float partialTick, double x, double y, double z, Operation<Void> original) {
        PrShaderPipeline.Phase previous = PrShaders.phase();
        PrShaders.phase(PrShaderPipeline.Phase.WEATHER);
        try {
            original.call(light, partialTick, x, y, z);
        } finally {
            PrShaders.phase(previous);
        }
    }

    @WrapMethod(method = "renderWorldBorder")
    private void prShaderBorder(Camera camera, Operation<Void> original) {
        PrShaderPipeline.Phase previous = PrShaders.phase();
        PrShaders.phase(PrShaderPipeline.Phase.WORLD_BORDER);
        try {
            original.call(camera);
        } finally {
            PrShaders.phase(previous);
        }
    }
}
