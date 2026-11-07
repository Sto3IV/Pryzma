package net.pryzma.shader.mixin;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.SubmitNodeStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LevelRenderer.class)
public interface LevelRendererAccessor {
	@Accessor("renderBuffers")
	RenderBuffers getRenderBuffers();

	@Accessor("featureRenderDispatcher")
	FeatureRenderDispatcher getFeatureRenderDispatcher();

	@Accessor("submitNodeStorage")
	SubmitNodeStorage getSubmitNodeStorage();

	@Accessor("viewArea")
	ViewArea getViewArea();

	@Accessor("visibleSections")
	ObjectArrayList<SectionRenderDispatcher.RenderSection> getVisibleSections();
}
