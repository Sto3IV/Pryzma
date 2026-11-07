package net.pryzma.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.pryzma.PryzmaConfig;
import net.pryzma.render.PrBetterGrass;
import net.pryzma.render.PrBetterSnow;
import net.pryzma.render.PrNaturalTextures;

/**
 * Alternate Blocks modifier, Better Grass quad replacement, and Better Snow auxiliary tessellation.
 */
@Mixin(ModelBlockRenderer.class)
abstract class ModelBlockRendererMixin {
    @ModifyVariable(
        method = "tesselateBlock(Lnet/minecraft/client/renderer/block/BlockQuadOutput;FFFLnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;J)V",
        at = @At("HEAD"),
        argsOnly = true
    )
    private long prAlternateBlocks(long seed) {
        return PryzmaConfig.prAlternateBlocks ? seed : 0L;
    }

    @Inject(
        method = "tesselateBlock(Lnet/minecraft/client/renderer/block/BlockQuadOutput;FFFLnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;J)V",
        at = @At("HEAD")
    )
    private void prCaptureContext(
        BlockQuadOutput output,
        float x,
        float y,
        float z,
        BlockAndTintGetter level,
        BlockPos pos,
        BlockState state,
        BlockStateModel model,
        long seed,
        CallbackInfo ci
    ) {
        PrBetterGrass.setContext(level, pos, state);
    }

    @Inject(
        method = "tesselateBlock(Lnet/minecraft/client/renderer/block/BlockQuadOutput;FFFLnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;J)V",
        at = @At("RETURN")
    )
    private void prRenderBetterSnowAndClear(
        BlockQuadOutput output,
        float x,
        float y,
        float z,
        BlockAndTintGetter level,
        BlockPos pos,
        BlockState state,
        BlockStateModel model,
        long seed,
        CallbackInfo ci
    ) {
        try {
            if (PryzmaConfig.prBetterSnow && !PrBetterSnow.isRenderingSnow() && PrBetterSnow.shouldRender(level, state, pos)) {
                PrBetterSnow.renderSnow((ModelBlockRenderer) (Object) this, output, x, y, z, level, state, pos, seed);
            }
        } finally {
            PrBetterGrass.clearContext();
        }
    }

    @WrapOperation(
        method = {
            "tesselateAmbientOcclusion",
            "tesselateFlat"
        },
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/dispatch/BlockStateModelPart;getQuads(Lnet/minecraft/core/Direction;)Ljava/util/List;")
    )
    private List<BakedQuad> prBetterGrassQuads(
        BlockStateModelPart part,
        Direction direction,
        Operation<List<BakedQuad>> original
    ) {
        if (PrBetterSnow.isRenderingSnow()) {
            if (direction != null && direction.getAxis().isHorizontal()) {
                BlockAndTintGetter level = PrBetterGrass.getCurrentLevel();
                BlockPos pos = PrBetterGrass.getCurrentPos();
                if (level != null && pos != null) {
                    BlockPos adjPos = pos.relative(direction);
                    BlockState adjState = level.getBlockState(adjPos);
                    if (PrBetterSnow.shouldRender(level, adjState, adjPos)) {
                        return java.util.Collections.emptyList();
                    }
                }
            }
            return original.call(part, direction);
        }
        List<BakedQuad> quads = null;
        if (direction != null && direction.getAxis().isHorizontal() && PryzmaConfig.isBetterGrass()) {
            quads = PrBetterGrass.getFaceQuads(part, direction);
        }
        if (quads == null) {
            quads = original.call(part, direction);
        }
        if (PryzmaConfig.prNaturalTextures) {
            quads = PrNaturalTextures.processQuads(PrBetterGrass.getCurrentState(), PrBetterGrass.getCurrentPos(), direction, quads);
        }
        return quads;
    }
}
