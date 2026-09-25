package net.pryzma.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.pryzma.PryzmaConfig;

/** Dynamic FOV off: speed, sprint and bow no longer change the field of view; a spyglass still does. */
@Mixin(GameRenderer.class)
abstract class GameRendererFovMixin {
    @WrapOperation(method = "getFov", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;lerp(FFF)F"))
    private float prDynamicFov(float delta, float start, float end, Operation<Float> original) {
        if (!PryzmaConfig.prDynamicFov
                && !(Minecraft.getInstance().getCameraEntity() instanceof AbstractClientPlayer player && player.isScoping())) {
            return 1.0F;
        }
        return original.call(delta, start, end);
    }
}
