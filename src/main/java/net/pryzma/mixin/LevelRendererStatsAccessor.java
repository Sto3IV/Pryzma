package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.renderer.LevelRenderer;

/** Entities drawn last frame, for Quick Info. */
@Mixin(LevelRenderer.class)
public interface LevelRendererStatsAccessor {
    @Accessor("renderedEntities")
    int prGetRenderedEntities();
}
