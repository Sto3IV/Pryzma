package net.pryzma.shader.mixin.texture;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Transparency;
import net.minecraft.client.renderer.texture.MipmapStrategy;
import net.minecraft.resources.Identifier;
import net.pryzma.shader.PryzmaShaders;
import net.pryzma.shader.pbr.SpriteContentsExtension;
import net.pryzma.shader.pbr.mipmap.CustomMipmapGenerator;
import net.minecraft.client.renderer.texture.MipmapGenerator;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SpriteContents.class)
public class MixinSpriteContents implements SpriteContentsExtension {
	@Unique
	@Nullable
	private SpriteContents.AnimationState createdTicker;

	@Redirect(method = "increaseMipLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/MipmapGenerator;generateMipLevels(Lnet/minecraft/resources/Identifier;[Lcom/mojang/blaze3d/platform/NativeImage;ILnet/minecraft/client/renderer/texture/MipmapStrategy;FLcom/mojang/blaze3d/platform/Transparency;)[Lcom/mojang/blaze3d/platform/NativeImage;"))
	private NativeImage[] pryzma$redirectMipmapGeneration(Identifier id, NativeImage[] nativeImages, int mipLevel, MipmapStrategy strategy, float cutOff, Transparency transparency) {
		if (this instanceof CustomMipmapGenerator.Provider provider) {
			CustomMipmapGenerator generator = provider.getMipmapGenerator();
			if (generator != null) {
				try {
					return generator.generateMipLevels(nativeImages, mipLevel);
				} catch (Exception e) {
					PryzmaShaders.logger.error("ERROR MIPMAPPING", e);
				}
			}
		}
		return MipmapGenerator.generateMipLevels(id, nativeImages, mipLevel, strategy, cutOff, transparency);
	}

	@Inject(method = "createAnimationState", at = @At("RETURN"))
	private void onReturnCreateAnimationState(CallbackInfoReturnable<SpriteContents.AnimationState> cir) {
		createdTicker = cir.getReturnValue();
	}

	@Override
	@Nullable
	public SpriteContents.AnimationState getCreatedTicker() {
		return createdTicker;
	}
}
