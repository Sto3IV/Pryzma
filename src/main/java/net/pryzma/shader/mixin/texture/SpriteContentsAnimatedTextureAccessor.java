package net.pryzma.shader.mixin.texture;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(targets = "net.minecraft.client.renderer.texture.SpriteContents$AnimatedTexture")
public interface SpriteContentsAnimatedTextureAccessor {
	@Accessor("frames")
	List<?> getFrames();
}
