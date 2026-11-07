package net.pryzma.shader.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.server.level.BlockDestructionProgress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.SortedSet;

@Mixin(LevelRenderer.class)
public interface LevelRendererAccessor {
	@Accessor("level")
	ClientLevel getLevel();

	@Accessor("renderBuffers")
	RenderBuffers getRenderBuffers();

	@Accessor("featureRenderDispatcher")
	FeatureRenderDispatcher getFeatureRenderDispatcher();

	@Accessor("submitNodeStorage")
	SubmitNodeStorage getSubmitNodeStorage();

	@Invoker("doesMobEffectBlockSky")
	boolean invokeDoesMobEffectBlockSky(Camera mainCamera);

	@Accessor("destructionProgress")
	Long2ObjectMap<SortedSet<BlockDestructionProgress>> getDestructionProgress();

	@Accessor("viewArea")
	ViewArea getViewArea();

	@Accessor("visibleSections")
	ObjectArrayList<SectionRenderDispatcher.RenderSection> getVisibleSections();
}
