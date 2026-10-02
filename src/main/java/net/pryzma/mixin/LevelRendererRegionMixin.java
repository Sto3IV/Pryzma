package net.pryzma.mixin;

import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexBuffer;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.pryzma.PryzmaConfig;
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
     *
     * <p>Phase B4: the buffer also learns whether its section lies beyond the detail distance, in whole
     * sections from the camera's section. PryzmaShaders' shadow pass passes the same camera position, so both passes
     * cut at the same sections. Vanilla reads the same origin right after for the ChunkOffset, so the cost is the arithmetic.
     */
    @ModifyExpressionValue(method = "renderSectionLayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection;getBuffer(Lnet/minecraft/client/renderer/RenderType;)Lcom/mojang/blaze3d/vertex/VertexBuffer;"))
    private VertexBuffer prRegionSectionBuffer(VertexBuffer buffer, @Local SectionRenderDispatcher.RenderSection section,
            @Local(argsOnly = true, ordinal = 0) double camX, @Local(argsOnly = true, ordinal = 1) double camY,
            @Local(argsOnly = true, ordinal = 2) double camZ) {
        IPrVertexBuffer pr = (IPrVertexBuffer) buffer;
        this.pryzma$regionSection = pr.pryzma$isRegionManaged();
        int sections = PryzmaConfig.prDetailDistance >> 4;
        boolean far = false;
        if (sections > 0) {
            BlockPos origin = section.getOrigin();
            int dx = (origin.getX() >> 4) - (Mth.floor(camX) >> 4);
            int dy = (origin.getY() >> 4) - (Mth.floor(camY) >> 4);
            int dz = (origin.getZ() >> 4) - (Mth.floor(camZ) >> 4);
            far = dx * dx + dy * dy + dz * dz > sections * sections;
        }
        pr.pryzma$setFar(far);
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
