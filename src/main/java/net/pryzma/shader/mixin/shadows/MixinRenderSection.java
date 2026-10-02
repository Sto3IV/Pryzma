package net.pryzma.shader.mixin.shadows;

import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.pryzma.shader.shadows.ShadowSectionIndex;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps {@link ShadowSectionIndex} in step with each section's published compiled state. */
@Mixin(SectionRenderDispatcher.RenderSection.class)
public class MixinRenderSection {
	@Shadow
	@Final
	public int index;

	@Inject(method = "setCompiled", at = @At("TAIL"))
	private void iris$indexCompiled(SectionRenderDispatcher.CompiledSection compiled, CallbackInfo ci) {
		ShadowSectionIndex.update(index, compiled);
	}

	@Inject(method = "reset", at = @At("TAIL"))
	private void iris$indexReset(CallbackInfo ci) {
		ShadowSectionIndex.update(index, SectionRenderDispatcher.CompiledSection.UNCOMPILED);
	}
}
