package net.pryzma.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;

/** The animations of an atlas, for the F3 animation count. */
@Mixin(TextureAtlas.class)
public interface TextureAtlasAccessor {
    @Accessor("animatedTexturesStates")
    List<SpriteContents.AnimationState> prGetAnimatedTextures();
}
