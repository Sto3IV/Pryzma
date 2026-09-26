package net.pryzma.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/** The animations of an atlas, for the F3 animation count. */
@Mixin(TextureAtlas.class)
public interface TextureAtlasAccessor {
    @Accessor("animatedTextures")
    List<TextureAtlasSprite.Ticker> prGetAnimatedTextures();
}
