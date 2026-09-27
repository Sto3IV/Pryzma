package net.pryzma.mixin;

import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
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
    /** Whether the section being drawn in renderSectionLayer's loop lives in a region. */
    @Unique
    private boolean pryzma$regionSection;

    /**
     * The section's buffer is fetched right before its ChunkOffset upload. Recording its region state here and
     * gating the upload below allocates nothing; a wrapped upload capturing the buffer as a local allocated a
     * MixinExtras LocalRef for every section drawn, in the main and the shadow pass.
     */
    @ModifyExpressionValue(method = "renderSectionLayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection;getBuffer(Lnet/minecraft/client/renderer/RenderType;)Lcom/mojang/blaze3d/vertex/VertexBuffer;"))
    private VertexBuffer prRegionSectionBuffer(VertexBuffer buffer) {
        this.pryzma$regionSection = ((IPrVertexBuffer) buffer).pryzma$isRegionManaged();
        return buffer;
    }

    @WrapWithCondition(method = "renderSectionLayer", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/shaders/Uniform;upload()V"))
    private boolean prRegionSectionOffset(Uniform uniform) {
        return !this.pryzma$regionSection;
    }

    @Inject(method = "renderSectionLayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ShaderInstance;clear()V"))
    private void prRegionFlush(RenderType type, double x, double y, double z, Matrix4f modelView, Matrix4f projection, CallbackInfo ci) {
        PrRenderRegionManager.flush(RenderSystem.getShader(), x, y, z);
    }
}
