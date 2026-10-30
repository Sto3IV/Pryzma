package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.renderer.texture.SpriteContents;
import net.pryzma.render.PrMipmaps;

/**
 * Pre-fills transparent border pixels with the sprite's average opaque color before mipmaps
 * are generated. Eliminates white dot artifacts and dark fringing on transparent blocks.
 */
@Mixin(SpriteContents.class)
abstract class SpriteContentsMixin {
    @Shadow
    private NativeImage originalImage;

    @Shadow
    NativeImage[] byMipLevel;

    @Inject(method = "increaseMipLevel", at = @At("HEAD"))
    private void prFixTransparentBorders(int mipmapLevels, CallbackInfo ci) {
        PrMipmaps.fixTransparentColor(this.originalImage);
        if (this.byMipLevel != null && this.byMipLevel.length > 0) {
            this.byMipLevel[0] = this.originalImage;
        }
    }
}
