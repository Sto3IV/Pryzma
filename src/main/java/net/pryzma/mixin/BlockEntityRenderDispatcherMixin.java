package net.pryzma.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.pryzma.entity.PrBlockEntityRenderStateDuck;
import net.pryzma.entity.model.PrCemContext;
import net.pryzma.entity.texture.PrEntityTextures;
import net.pryzma.light.PrDynamicLights;
import net.pryzma.render.PrEmissiveSubmitNodeCollector;

/** Random and emissive textures and CEM animations of block entities in the world (chests, beds, signs ...). */
@Mixin(BlockEntityRenderDispatcher.class)
abstract class BlockEntityRenderDispatcherMixin {
    @Inject(method = "tryExtractRenderState(Lnet/minecraft/world/level/block/entity/BlockEntity;FLnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;Z)Lnet/minecraft/client/renderer/blockentity/state/BlockEntityRenderState;",
            at = @At("RETURN"))
    private <E extends BlockEntity> void prAttachBlockEntity(E be, float partialTick,
            ModelFeatureRenderer.CrumblingOverlay overlay, boolean flag,
            CallbackInfoReturnable<BlockEntityRenderState> cir) {
        BlockEntityRenderState state = cir.getReturnValue();
        if (state != null) {
            ((PrBlockEntityRenderStateDuck) state).prSetBlockEntity(be);
            state.lightCoords = PrDynamicLights.getBlockEntityLight(be, state.lightCoords);
        }
    }

    @SuppressWarnings("unchecked")
    @WrapOperation(method = "submit(Lnet/minecraft/client/renderer/blockentity/state/BlockEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderer;submit(Lnet/minecraft/client/renderer/blockentity/state/BlockEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V"))
    private static <S extends BlockEntityRenderState> void prSubmitBlockEntity(BlockEntityRenderer<?, S> renderer,
            S renderState, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera, Operation<Void> original) {
        BlockEntity blockEntity = renderState instanceof PrBlockEntityRenderStateDuck duck ? duck.prGetBlockEntity() : null;
        PrEntityTextures.Scope textures = blockEntity != null ? PrEntityTextures.begin(blockEntity) : null;
        PrCemContext.Scope cem = blockEntity != null ? PrCemContext.begin(blockEntity, 0.0f) : null;
        try {
            original.call(renderer, renderState, pose, collector, camera);
            if (PrEntityTextures.needsEmissivePass()) {
                SubmitNodeCollector emissive = PrEmissiveSubmitNodeCollector.wrap(collector);
                PrEntityTextures.setEmissivePass(true);
                try {
                    PrCemContext.nextPass();
                    original.call(renderer, renderState, pose, emissive, camera);
                } finally {
                    PrEntityTextures.setEmissivePass(false);
                }
            }
        } finally {
            if (cem != null) PrCemContext.end(cem);
            if (textures != null) PrEntityTextures.end(textures);
        }
    }
}
