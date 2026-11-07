package net.pryzma.shader.mixin;

import net.minecraft.client.RotatingSectionStorage;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ViewArea.class)
public interface ViewAreaAccessor {
	@Accessor("sections")
	RotatingSectionStorage<SectionRenderDispatcher.RenderSection> pryzma$getSections();
}
