package net.pryzma.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.pryzma.render.PrAnimations;

/**
 * Animations page: a block atlas animation whose switch is off holds its current frame. The
 * switch is read every tick, so toggling needs no reload.
 * Also calibrates uvShrinkRatio to prevent sub-pixel underflow on modern high-resolution atlases.
 */
@Mixin(TextureAtlasSprite.class)
abstract class TextureAtlasSpriteMixin {
    @Shadow public abstract SpriteContents contents();

    @ModifyReturnValue(method = "createTicker", at = @At("RETURN"))
    private TextureAtlasSprite.Ticker prAnimationSwitch(TextureAtlasSprite.Ticker ticker) {
        if (ticker == null) {
            return null;
        }
        PrAnimations.Kind kind = PrAnimations.kindOf((TextureAtlasSprite) (Object) this);
        return kind == PrAnimations.Kind.OTHER ? ticker : new PrAnimations.SwitchedTicker(ticker, kind);
    }

    @ModifyReturnValue(method = "uvShrinkRatio", at = @At("RETURN"))
    private float prAdjustUvShrink(float vanillaRatio) {
        SpriteContents c = this.contents();
        int w = c != null ? c.width() : 16;
        return Math.max(vanillaRatio, 0.25F / Math.max(1, w));
    }
}

