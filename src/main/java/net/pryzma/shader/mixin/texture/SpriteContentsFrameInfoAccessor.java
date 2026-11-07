package net.pryzma.shader.mixin.texture;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.renderer.texture.SpriteContents$FrameInfo")
public interface SpriteContentsFrameInfoAccessor {
	@Accessor("index")
	int getIndex();

	@Accessor("time")
	int getTime();
}
