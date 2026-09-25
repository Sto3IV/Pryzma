package net.pryzma.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer;
import net.pryzma.PryzmaConfig;

/** Autosave: the singleplayer save interval in ticks (45 s to 24 min). */
@Mixin(MinecraftServer.class)
abstract class MinecraftServerAutosaveMixin {
    @ModifyReturnValue(method = "computeNextAutosaveInterval", at = @At("RETURN"))
    private int prAutosaveTicks(int vanilla) {
        return (Object) this instanceof IntegratedServer ? PryzmaConfig.prAutoSaveTicks : vanilla;
    }
}
