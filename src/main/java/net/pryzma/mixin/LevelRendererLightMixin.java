package net.pryzma.mixin;

import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.pryzma.light.PrDynamicLights;

/**
 * Dynamic light integration into LevelRenderer: updates active light sources per frame and
 * injects dynamic light into block/world queries.
 */
@Mixin(LevelRenderer.class)
abstract class LevelRendererLightMixin {
    @Shadow private ClientLevel level;

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void prDynamicLightsUpdate(DeltaTracker delta, boolean outline, Camera camera, GameRenderer gameRenderer,
            LightTexture light, Matrix4f modelView, Matrix4f projection, CallbackInfo ci) {
        PrDynamicLights.update((LevelRenderer) (Object) this, this.level);
    }

    @Inject(method = "getLightColor(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;)I",
            at = @At("RETURN"), cancellable = true)
    private static void prDynamicLightBlock(BlockAndTintGetter level, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        if (PrDynamicLights.isEnabled()) {
            cir.setReturnValue(PrDynamicLights.getCombinedLight(pos, cir.getReturnValue()));
        }
    }

    @Inject(method = "getLightColor(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I",
            at = @At("RETURN"), cancellable = true)
    private static void prDynamicLightBlockState(BlockAndTintGetter level, BlockState state, BlockPos pos,
            CallbackInfoReturnable<Integer> cir) {
        if (PrDynamicLights.isEnabled()) {
            cir.setReturnValue(PrDynamicLights.getCombinedLight(pos, cir.getReturnValue()));
        }
    }
}
