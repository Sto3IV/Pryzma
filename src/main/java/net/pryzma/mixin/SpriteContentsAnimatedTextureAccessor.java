package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.renderer.texture.SpriteContents;

@Mixin(targets = "net.minecraft.client.renderer.texture.SpriteContents$AnimatedTexture")
public interface SpriteContentsAnimatedTextureAccessor {
    @Accessor("this$0")
    SpriteContents prGetContents();
}
