package net.pryzma.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.pryzma.entity.model.PrCemContext;
import net.pryzma.entity.model.PrCemRender;
import net.pryzma.entity.texture.PrEntityTextures;

/** Random and emissive textures and CEM animations of block entities in the world (chests, beds, signs ...). */
@Mixin(BlockEntityRenderDispatcher.class)
abstract class BlockEntityRenderDispatcherMixin {
    @WrapOperation(method = "setupAndRender", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderer;render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V"))
    private static void prRenderBlockEntity(BlockEntityRenderer<BlockEntity> renderer, BlockEntity blockEntity, float partialTick,
            PoseStack pose, MultiBufferSource buffers, int light, int overlay, Operation<Void> original) {
        PrEntityTextures.Scope textures = PrEntityTextures.begin(blockEntity);
        PrCemContext.Scope cem = PrCemContext.begin(blockEntity, partialTick);
        PrCemRender.Scope cemRender = PrCemRender.begin(buffers);
        net.pryzma.shader.PrShaders.setRenderedBlockEntity(net.pryzma.shader.PrShaders.idMap().getBlockEntityId(blockEntity));
        try {
            int dynLight = net.pryzma.light.PrDynamicLights.getBlockEntityLight(blockEntity, light);
            original.call(renderer, blockEntity, partialTick, pose, buffers, dynLight, overlay);
            if (PrEntityTextures.needsEmissivePass()) {
                MultiBufferSource emissive = PrEntityTextures.beginEmissivePass(buffers);
                PrCemRender.begin(emissive);
                PrCemContext.nextPass();
                original.call(renderer, blockEntity, partialTick, pose, emissive, LightTexture.FULL_BRIGHT, overlay);
            }
        } finally {
            net.pryzma.shader.PrShaders.setRenderedBlockEntity(-1);
            PrCemRender.end(cemRender);
            PrCemContext.end(cem);
            PrEntityTextures.end(textures);
        }
    }
}
