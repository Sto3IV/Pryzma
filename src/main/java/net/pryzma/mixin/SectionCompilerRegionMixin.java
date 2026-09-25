package net.pryzma.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.blaze3d.vertex.VertexSorting;

import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.SectionPos;
import net.pryzma.render.PrRenderRegionManager;

/**
 * Render Regions: the finished meshes of the region layers are moved into region space on the
 * chunk worker, after every block, fluid and NeoForge additional renderer has written them.
 */
@Mixin(SectionCompiler.class)
abstract class SectionCompilerRegionMixin {
    @Inject(method = "compile(Lnet/minecraft/core/SectionPos;Lnet/minecraft/client/renderer/chunk/RenderChunkRegion;Lcom/mojang/blaze3d/vertex/VertexSorting;Lnet/minecraft/client/renderer/SectionBufferBuilderPack;Ljava/util/List;)Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;",
            at = @At("RETURN"))
    private void prRegionSpace(SectionPos pos, RenderChunkRegion region, VertexSorting sorting, SectionBufferBuilderPack buffers,
            List<?> additionalRenderers, CallbackInfoReturnable<SectionCompiler.Results> cir) {
        PrRenderRegionManager.toRegionSpace(pos, cir.getReturnValue().renderedLayers);
    }
}
