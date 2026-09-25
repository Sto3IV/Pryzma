package net.pryzma.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.world.level.block.state.BlockBehaviour;
import net.pryzma.render.PrRenderHooks;

/** Smooth Lighting Level: how dark ambient occlusion shades next to full blocks. */
@Mixin(BlockBehaviour.BlockStateBase.class)
abstract class BlockStateShadeMixin {
    @ModifyReturnValue(method = "getShadeBrightness", at = @At("RETURN"))
    private float prAoLevel(float brightness) {
        return PrRenderHooks.shadeBrightness(brightness);
    }
}
