package net.pryzma.mixin;

import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;

import net.minecraft.client.CloudStatus;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.phys.Vec3;
import net.pryzma.render.PrCloudRenderer;

/**
 * Routes cloud meshing and fog coordination through {@link PrCloudRenderer} for seamless
 * infinite cloud rendering and artifact-free cloud geometry.
 */
@Mixin(LevelRenderer.class)
abstract class LevelRendererCloudsMixin {
    @Shadow private boolean generateClouds;
    @Shadow private CloudStatus prevCloudsType;

    @Inject(method = "renderClouds", at = @At("HEAD"))
    private void prBeforeRenderClouds(PoseStack poseStack, Matrix4f modelView, Matrix4f projection, float partialTick,
                                      double camX, double camY, double camZ, CallbackInfo ci) {
        if (PrCloudRenderer.consumeDirty()) {
            this.generateClouds = true;
        }
        PrCloudRenderer.beforeRenderClouds();
    }

    @Inject(method = "renderClouds", at = @At("RETURN"))
    private void prAfterRenderClouds(PoseStack poseStack, Matrix4f modelView, Matrix4f projection, float partialTick,
                                     double camX, double camY, double camZ, CallbackInfo ci) {
        PrCloudRenderer.afterRenderClouds();
    }

    @Inject(method = "buildClouds", at = @At("HEAD"), cancellable = true)
    private void prBuildClouds(Tesselator tesselator, double camX, double camY, double camZ, Vec3 cloudColor,
                               CallbackInfoReturnable<MeshData> cir) {
        cir.setReturnValue(PrCloudRenderer.buildClouds(tesselator, camX, camY, camZ, cloudColor, this.prevCloudsType));
    }
}
