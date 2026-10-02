package net.pryzma.mixin;

import java.util.List;
import java.util.Map;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.pryzma.PryzmaConfig;
import net.pryzma.detail.PrDeferredDetailQueue;
import net.pryzma.detail.PrDetailClassifier;
import net.pryzma.shader.vertices.BlockContext;
import net.pryzma.shader.vertices.BlockSensitiveBufferBuilder;
import net.pryzma.perf.PrDebugTracker;
import net.pryzma.render.IPrMeshData;
import net.pryzma.render.PrRenderRegionManager;

/**
 * Render Regions and Phase B4 decorator LOD, on the chunk worker.
 * <ol>
 * <li>With a detail distance set, decorator blocks are not meshed in the block loop but queued.</li>
 * <li>After every block, fluid and NeoForge additional renderer has written its layer, the vertex count of
 * each layer with queued blocks is its core, and the queue is replayed behind it: {@code [core | decorators]}.
 * Only the arguments of the original call are reused; the pose stack and random source are the queue's.</li>
 * <li>The finished meshes carry their core and move into region space; the build is counted for F3 and Quick Info.</li>
 * </ol>
 */
@Mixin(SectionCompiler.class)
abstract class SectionCompilerRegionMixin {
    private static final String COMPILE = "compile(Lnet/minecraft/core/SectionPos;Lnet/minecraft/client/renderer/chunk/RenderChunkRegion;Lcom/mojang/blaze3d/vertex/VertexSorting;Lnet/minecraft/client/renderer/SectionBufferBuilderPack;Ljava/util/List;)Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;";

    @Shadow @Final private BlockRenderDispatcher blockRenderer;

    @Shadow
    private native BufferBuilder getOrBeginLayer(Map<RenderType, BufferBuilder> layers, SectionBufferBuilderPack buffers, RenderType type);

    /** A compile that threw leaves its queue behind; the next one on this thread starts clean. */
    @Inject(method = COMPILE, at = @At("HEAD"))
    private void prResetQueue(SectionPos pos, RenderChunkRegion region, VertexSorting sorting, SectionBufferBuilderPack buffers,
            List<?> additionalRenderers, CallbackInfoReturnable<SectionCompiler.Results> cir) {
        PrDeferredDetailQueue.get().clear();
    }

    /** Sorted layers are never deferred: their index buffer is sorted over all quads, so no prefix of it is the core. */
    @WrapWithCondition(method = COMPILE, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/block/BlockRenderDispatcher;renderBatched(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;Lnet/neoforged/neoforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V"))
    private boolean prDeferDetail(BlockRenderDispatcher dispatcher, BlockState state, BlockPos pos, BlockAndTintGetter level, PoseStack pose,
            VertexConsumer consumer, boolean checkSides, RandomSource random, ModelData modelData, RenderType type) {
        if (PryzmaConfig.prDetailDistance <= 0 || type.sortOnUpload() || !PrDetailClassifier.isDetail(state, level, pos)) {
            return true;
        }
        PrDeferredDetailQueue.get().add(state, pos, modelData, type);
        return false;
    }

    @Inject(method = COMPILE, at = @At(value = "INVOKE", target = "Ljava/util/Map;entrySet()Ljava/util/Set;"))
    private void prReplayDetail(SectionPos pos, RenderChunkRegion region, VertexSorting sorting, SectionBufferBuilderPack buffers,
            List<?> additionalRenderers, CallbackInfoReturnable<SectionCompiler.Results> cir,
            @Local(ordinal = 0) Map<RenderType, BufferBuilder> layers) {
        PrDeferredDetailQueue queue = PrDeferredDetailQueue.get();
        int count = queue.size();
        if (count == 0) {
            return;
        }
        for (int i = 0, n = queue.layerCount(); i < n; i++) {
            BufferBuilder builder = layers.get(queue.layer(i));
            queue.setCore(i, builder == null ? 0 : ((BufferBuilderAccessor) builder).pryzma$getVertices());
        }
        PoseStack pose = queue.poseStack;
        for (int i = 0; i < count; i++) {
            PrDeferredDetailQueue.Entry e = queue.get(i);
            BufferBuilder builder = getOrBeginLayer(layers, buffers, e.renderType);
            pose.pushPose();
            pose.translate(SectionPos.sectionRelative(e.pos.getX()), SectionPos.sectionRelative(e.pos.getY()), SectionPos.sectionRelative(e.pos.getZ()));
            BlockSensitiveBufferBuilder context = BlockContext.begin(builder, e.state, e.pos);
            try {
                blockRenderer.renderBatched(e.state, e.pos, region, pose, builder, true, queue.random, e.modelData, e.renderType);
            } finally {
                if (context != null) {
                    context.endBlock();
                }
            }
            pose.popPose();
        }
    }

    @Inject(method = COMPILE, at = @At("RETURN"))
    private void prRegionSpace(SectionPos pos, RenderChunkRegion region, VertexSorting sorting, SectionBufferBuilderPack buffers,
            List<?> additionalRenderers, CallbackInfoReturnable<SectionCompiler.Results> cir) {
        PrDebugTracker.onSectionCompiled();
        Map<RenderType, MeshData> meshes = cir.getReturnValue().renderedLayers;
        PrDeferredDetailQueue queue = PrDeferredDetailQueue.get();
        for (int i = 0, n = queue.layerCount(); i < n; i++) {
            MeshData mesh = meshes.get(queue.layer(i));
            if (mesh != null) {
                ((IPrMeshData) (Object) mesh).pryzma$setCoreVertices(queue.core(i));
            }
        }
        queue.clear();
        PrRenderRegionManager.toRegionSpace(pos, meshes);
    }
}
