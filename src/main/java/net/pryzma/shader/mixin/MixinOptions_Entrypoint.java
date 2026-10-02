package net.pryzma.shader.mixin;

import net.pryzma.shader.PryzmaShaders;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Options.class, priority = 990)
public class MixinOptions_Entrypoint {
	@Unique
	private static boolean pryzma$initialized;

	@Inject(method = "load()V", at = @At("HEAD"))
	private void pryzma$beforeLoadOptions(CallbackInfo ci) {
		if (pryzma$initialized) {
			return;
		}

		pryzma$initialized = true;
		new PryzmaShaders().onEarlyInitialize();
	}
}
