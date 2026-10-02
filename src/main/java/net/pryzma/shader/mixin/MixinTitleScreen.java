package net.pryzma.shader.mixin;

import net.pryzma.shader.PryzmaShaders;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class MixinTitleScreen extends Screen {
	@Unique
	private static boolean iris$hasFirstInit;

	protected MixinTitleScreen(Component arg) {
		super(arg);
	}

	@Inject(method = "init", at = @At("RETURN"))
	public void iris$firstInit(CallbackInfo ci) {
		if (!iris$hasFirstInit) {
			PryzmaShaders.onLoadingComplete();
		}

		iris$hasFirstInit = true;

	}
}
