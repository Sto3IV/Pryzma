package net.pryzma.shader.mixin;

import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
	@Invoker("shouldRenderBlockOutline")
	boolean shouldRenderBlockOutlineA();
}
