package net.pryzma.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.pryzma.PryzmaConfig;

/** Show Capes off: players render without capes, and elytras fall back to the default texture. */
@Mixin(AbstractClientPlayer.class)
abstract class AbstractClientPlayerMixin {
    @ModifyReturnValue(method = "getSkin", at = @At("RETURN"))
    private PlayerSkin prShowCapes(PlayerSkin skin) {
        if (PryzmaConfig.prShowCapes || skin.capeTexture() == null && skin.elytraTexture() == null) {
            return skin;
        }
        return new PlayerSkin(skin.texture(), skin.textureUrl(), null, null, skin.model(), skin.secure());
    }
}
