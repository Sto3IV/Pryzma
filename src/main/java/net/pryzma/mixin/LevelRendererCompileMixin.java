package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.lighting.LevelLightEngine;

/**
 * compileSections builds a SectionPos for every visible section each frame before it tests isDirty(); the
 * position is only read by the light check behind that test. It is now built there, for dirty sections only.
 * Under a shader pack this was the render thread's largest allocation site.
 */
@Mixin(LevelRenderer.class)
abstract class LevelRendererCompileMixin {
    @Redirect(method = "compileSections", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/core/SectionPos;of(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/SectionPos;"))
    private SectionPos prDeferSectionPos(BlockPos origin) {
        return null;
    }

    @Redirect(method = "compileSections", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/lighting/LevelLightEngine;lightOnInSection(Lnet/minecraft/core/SectionPos;)Z"))
    private boolean prLightOnInSection(LevelLightEngine engine, SectionPos deferred, @Local SectionRenderDispatcher.RenderSection section) {
        return engine.lightOnInSection(SectionPos.of(section.getOrigin()));
    }
}
