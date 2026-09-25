package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;

import net.minecraft.client.Minecraft;
import net.pryzma.shader.PrShaders;

/**
 * Fabulous transparency under shaders, and render loop framerate pacing without Windows scheduler penalty.
 */
@Mixin(Minecraft.class)
abstract class MinecraftShaderMixin {
    @ModifyReturnValue(method = "useShaderTransparency", at = @At("RETURN"))
    private static boolean prNoFabulousUnderShaders(boolean original) {
        return original && !PrShaders.enabled();
    }

    /**
     * Prevents the Windows scheduler quantum penalty (1-2ms sleep/yield) when running uncapped framerates.
     * Only yields when the game window is in the background or minimized.
     */
    @WrapWithCondition(method = "runTick", at = @At(value = "INVOKE", target = "Ljava/lang/Thread;yield()V"))
    private boolean prShouldYield() {
        Minecraft mc = (Minecraft) (Object) this;
        return !mc.isWindowActive();
    }
}
