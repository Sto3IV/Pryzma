package net.pryzma.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.pryzma.render.PrAnimations;

/**
 * Animations page: a block atlas animation whose switch is off holds its current frame. The
 * switch is read every tick, so toggling needs no reload.
 */
@Mixin(TextureAtlasSprite.class)
abstract class TextureAtlasSpriteMixin {
    @ModifyReturnValue(method = "createTicker", at = @At("RETURN"))
    private TextureAtlasSprite.Ticker prAnimationSwitch(TextureAtlasSprite.Ticker ticker) {
        if (ticker == null) {
            return null;
        }
        PrAnimations.Kind kind = PrAnimations.kindOf((TextureAtlasSprite) (Object) this);
        return kind == PrAnimations.Kind.OTHER ? ticker : new PrAnimations.SwitchedTicker(ticker, kind);
    }
}
