package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.pryzma.gui.PrQuickInfoStats;

/** Counts section rebuilds, the Updates figure of Quick Info. Runs on the chunk workers. */
@Mixin(SectionCompiler.class)
abstract class SectionCompilerMixin {
    @Inject(method = "compile(Lnet/minecraft/core/SectionPos;Lnet/minecraft/client/renderer/chunk/RenderChunkRegion;Lcom/mojang/blaze3d/vertex/VertexSorting;Lnet/minecraft/client/renderer/SectionBufferBuilderPack;Ljava/util/List;)Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;",
            at = @At("HEAD"))
    private void prCountCompile(CallbackInfoReturnable<SectionCompiler.Results> cir) {
        PrQuickInfoStats.SECTION_COMPILES.incrementAndGet();
    }
}
