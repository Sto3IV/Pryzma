package net.pryzma.mixin;

import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;

/** The shader pack's shadow pass draws the terrain layers again, from the light. */
@Mixin(LevelRenderer.class)
public interface LevelRendererShadowAccessor {
    @Invoker("renderSectionLayer")
    void prRenderSectionLayer(RenderType type, double camX, double camY, double camZ, Matrix4f modelView, Matrix4f projection);
}
