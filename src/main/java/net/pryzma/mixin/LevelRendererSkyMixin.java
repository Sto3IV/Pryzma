package net.pryzma.mixin;

import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.pryzma.PryzmaConfig;
import net.pryzma.color.PryzmaColormaps;
import net.pryzma.sky.PryzmaSky;

/**
 * Custom sky layers and the Details sky toggles (Sky, Stars, Sun &amp; Moon). Vanilla draws are
 * identified by the buffer or texture they use, not by call order.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererSkyMixin {
    @Shadow private ClientLevel level;
    @Shadow private VertexBuffer skyBuffer;
    @Shadow private VertexBuffer starBuffer;
    @Shadow @Final private static ResourceLocation SUN_LOCATION;
    @Shadow @Final private static ResourceLocation MOON_LOCATION;

    @Unique private ResourceLocation prSkyTexture;

    @Inject(method = "renderSky", at = @At("HEAD"))
    private void prSkyBegin(Matrix4f frustumMatrix, Matrix4f projectionMatrix, float partialTick, Camera camera,
            boolean isFoggy, Runnable skyFogSetup, CallbackInfo ci) {
        this.prSkyTexture = null;
    }

    /** OptiFine draws the layers after the sunrise glow, before the sun and moon are rotated into place. */
    @Inject(method = "renderSky", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;getTimeOfDay(F)F", ordinal = 1))
    private void prCustomSkyLayers(Matrix4f frustumMatrix, Matrix4f projectionMatrix, float partialTick, Camera camera,
            boolean isFoggy, Runnable skyFogSetup, CallbackInfo ci, @Local PoseStack poseStack) {
        PryzmaSky.render(this.level, poseStack, partialTick);
    }

    @WrapWithCondition(method = "renderSky", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/VertexBuffer;drawWithShader(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lnet/minecraft/client/renderer/ShaderInstance;)V"))
    private boolean prSkyBufferVisible(VertexBuffer buffer, Matrix4f modelView, Matrix4f projection, ShaderInstance shader) {
        if (buffer == this.skyBuffer) {
            return PryzmaConfig.prSky;
        }
        if (buffer == this.starBuffer) {
            return PryzmaSky.starsVisible(this.level);
        }
        return true;
    }

    @ModifyExpressionValue(method = "renderSky", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/DimensionSpecialEffects;getSunriseColor(FF)[F"))
    private float[] prSunriseGlow(float[] original) {
        return PryzmaConfig.prSunMoon ? original : null;
    }

    @WrapOperation(method = "renderSky", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderTexture(ILnet/minecraft/resources/ResourceLocation;)V"))
    private void prTrackSkyTexture(int unit, ResourceLocation texture, Operation<Void> original) {
        this.prSkyTexture = texture;
        original.call(unit, texture);
    }

    @WrapOperation(method = "renderSky", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/BufferUploader;drawWithShader(Lcom/mojang/blaze3d/vertex/MeshData;)V"))
    private void prSunMoonDraw(MeshData mesh, Operation<Void> original) {
        if (!PryzmaConfig.prSunMoon && (this.prSkyTexture == SUN_LOCATION || this.prSkyTexture == MOON_LOCATION)) {
            mesh.close();
            return;
        }
        original.call(mesh);
    }

    @Inject(method = "renderEndSky", at = @At("HEAD"), cancellable = true)
    private void prEndSkyVisible(PoseStack poseStack, CallbackInfo ci) {
        if (!PryzmaConfig.prSky) {
            ci.cancel();
        }
    }

    @ModifyConstant(method = "renderEndSky", constant = @Constant(intValue = -14145496))
    private int prEndSkyColor(int original) {
        return PryzmaColormaps.endSkyColor(original);
    }

    @Inject(method = "renderEndSky", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;depthMask(Z)V", ordinal = 1))
    private void prEndCustomSkyLayers(PoseStack poseStack, CallbackInfo ci) {
        PryzmaSky.renderEnd(this.level, poseStack);
    }
}
