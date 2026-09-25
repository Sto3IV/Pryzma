package net.pryzma.mixin;

import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.pryzma.render.IPrVertexBuffer;
import net.pryzma.render.PrRenderRegionManager;

/**
 * Render Regions, draw side: region sections skip the per-section ChunkOffset upload (their draw
 * only queues a range), and after the section loop every region reached is drawn with its own
 * offset and one multi-draw, while the layer's shader is still applied.
 */
@Mixin(LevelRenderer.class)
abstract class LevelRendererRegionMixin {
    @WrapOperation(method = "renderSectionLayer", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/shaders/Uniform;upload()V"))
    private void prRegionSectionOffset(Uniform uniform, Operation<Void> original, @Local VertexBuffer buffer) {
        if (!((IPrVertexBuffer) buffer).pryzma$isRegionManaged()) {
            original.call(uniform);
        }
    }

    @Inject(method = "renderSectionLayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ShaderInstance;clear()V"))
    private void prRegionFlush(RenderType type, double x, double y, double z, Matrix4f modelView, Matrix4f projection, CallbackInfo ci) {
        PrRenderRegionManager.flush(RenderSystem.getShader(), x, y, z);
    }
}
