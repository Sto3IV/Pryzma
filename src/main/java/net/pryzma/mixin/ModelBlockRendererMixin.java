package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.pryzma.PryzmaConfig;

/** Alternate Blocks off: every block takes the first random model variant (seed 0), as in 1.x. */
@Mixin(ModelBlockRenderer.class)
abstract class ModelBlockRendererMixin {
    @ModifyVariable(method = "tesselateBlock(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;JILnet/neoforged/neoforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V",
            at = @At("HEAD"), argsOnly = true)
    private long prAlternateBlocks(long seed) {
        return PryzmaConfig.prAlternateBlocks ? seed : 0L;
    }
}
