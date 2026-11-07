package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.renderer.texture.SpriteContents;

@Mixin(targets = "net.minecraft.client.renderer.texture.SpriteContents$AnimationState")
public interface SpriteContentsAnimationStateAccessor {
    @Accessor("animationInfo")
    SpriteContents.AnimatedTexture prGetAnimationInfo();
}
