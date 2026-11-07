package net.pryzma.shader.mixin;

import net.pryzma.shader.PryzmaShaders;
import net.minecraft.client.Minecraft;
import net.minecraft.util.profiling.Profiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Small hook giving PryzmaShaders a chance to check for keyboard input for its keybindings.
 */
@Mixin(Minecraft.class)
public class MixinMinecraft_Keybinds {
	@Inject(method = "tick()V", at = @At("RETURN"))
	private void pryzma$onTick(CallbackInfo ci) {
		Profiler.get().push("iris_keybinds");

		PryzmaShaders.handleKeybinds((Minecraft) (Object) this);

		Profiler.get().pop();
	}
}
