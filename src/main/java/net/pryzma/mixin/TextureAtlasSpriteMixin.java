package net.pryzma.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * Calibrates uvShrinkRatio to prevent sub-pixel underflow on modern high-resolution atlases.
 */
@Mixin(TextureAtlasSprite.class)
abstract class TextureAtlasSpriteMixin {
    @Shadow public abstract SpriteContents contents();

    @ModifyReturnValue(method = "uvShrinkRatio", at = @At("RETURN"))
    private float prAdjustUvShrink(float vanillaRatio) {
        SpriteContents c = this.contents();
        int w = c != null ? c.width() : 16;
        return Math.max(vanillaRatio, 0.25F / Math.max(1, w));
    }
}
