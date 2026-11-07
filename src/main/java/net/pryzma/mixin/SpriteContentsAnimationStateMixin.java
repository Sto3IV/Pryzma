package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.texture.SpriteContents;
import net.pryzma.render.PrAnimations;

@Mixin(targets = "net.minecraft.client.renderer.texture.SpriteContents$AnimationState")
abstract class SpriteContentsAnimationStateMixin {
    @Shadow @Final private SpriteContents.AnimatedTexture animationInfo;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void prAnimationTick(CallbackInfo ci) {
        SpriteContents contents = ((SpriteContentsAnimatedTextureAccessor) (Object) this.animationInfo).prGetContents();
        if (contents != null) {
            PrAnimations.Kind kind = PrAnimations.kindOf(contents.name());
            if (kind != null && !kind.enabled()) {
                ci.cancel();
            }
        }
    }
}
