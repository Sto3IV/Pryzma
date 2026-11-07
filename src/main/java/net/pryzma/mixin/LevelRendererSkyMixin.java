package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.commands.RenderPass;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.world.level.MoonPhase;
import net.pryzma.PryzmaConfig;
import net.pryzma.shader.PryzmaShaders;
import net.pryzma.shader.pipeline.WorldRenderingPipeline;
import net.pryzma.sky.PryzmaSky;

/**
 * Custom sky layers and the Details sky toggles (Sky, Stars, Sun &amp; Moon),
 * unified with shader WorldRenderingPipeline visibility checks.
 */
@Mixin(SkyRenderer.class)
public abstract class LevelRendererSkyMixin {

    @Inject(method = "renderSkyDisc", at = @At("HEAD"), cancellable = true)
    private void prSkyDiscVisible(CallbackInfo ci) {
        if (!PryzmaConfig.prSky || !PryzmaShaders.getPipelineManager().getPipeline().map(WorldRenderingPipeline::shouldRenderSkyDisc).orElse(true)) {
            ci.cancel();
        }
    }

    @Inject(method = "renderDarkDisc", at = @At("HEAD"), cancellable = true)
    private void prDarkDiscVisible(CallbackInfo ci) {
        if (!PryzmaConfig.prSky || !PryzmaShaders.getPipelineManager().getPipeline().map(WorldRenderingPipeline::shouldRenderSkyDisc).orElse(true)) {
            ci.cancel();
        }
    }

    @Inject(method = "renderSunriseAndSunset", at = @At("HEAD"), cancellable = true)
    private void prSunriseVisible(CallbackInfo ci) {
        if (!PryzmaConfig.prSunMoon || !PryzmaShaders.getPipelineManager().getPipeline().map(WorldRenderingPipeline::shouldRenderSkyDisc).orElse(true)) {
            ci.cancel();
        }
    }

    @Inject(method = "renderSun", at = @At("HEAD"), cancellable = true)
    private void prSunVisible(CallbackInfo ci) {
        if (!PryzmaConfig.prSunMoon || !PryzmaShaders.getPipelineManager().getPipeline().map(WorldRenderingPipeline::shouldRenderSun).orElse(true)) {
            ci.cancel();
        }
    }

    @Inject(method = "renderMoon", at = @At("HEAD"), cancellable = true)
    private void prMoonVisible(CallbackInfo ci) {
        if (!PryzmaConfig.prSunMoon || !PryzmaShaders.getPipelineManager().getPipeline().map(WorldRenderingPipeline::shouldRenderMoon).orElse(true)) {
            ci.cancel();
        }
    }

    @Inject(method = "renderStars", at = @At("HEAD"), cancellable = true)
    private void prStarsVisible(CallbackInfo ci) {
        if (!PryzmaSky.starsVisible(Minecraft.getInstance().level)) {
            ci.cancel();
        }
    }

    @Inject(method = "renderEndSky", at = @At("HEAD"), cancellable = true)
    private void prEndSkyVisible(CallbackInfo ci) {
        if (!PryzmaConfig.prSky) {
            ci.cancel();
        }
    }

    @Inject(method = "renderSunMoonAndStars", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;rotateDegrees(Lcom/mojang/math/Axis;F)V", ordinal = 0, shift = At.Shift.AFTER))
    private void prCustomSkyLayers(RenderPass renderPass, PoseStack poseStack, float sunAngle, float moonAngle,
            float starAngle, MoonPhase moonPhase, float rainBrightness, float starBrightness, CallbackInfo ci) {
        PryzmaSky.render(renderPass, poseStack, sunAngle, rainBrightness);
    }
}
