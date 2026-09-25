package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import net.minecraft.client.Minecraft;
import net.pryzma.shader.PrShaders;

/** Fabulous transparency composes its own targets; a shader pack does that itself, so it is off meanwhile. */
@Mixin(Minecraft.class)
abstract class MinecraftShaderMixin {
    @ModifyReturnValue(method = "useShaderTransparency", at = @At("RETURN"))
    private static boolean prNoFabulousUnderShaders(boolean original) {
        return original && !PrShaders.enabled();
    }
}
