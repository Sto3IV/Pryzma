package net.pryzma.mixin;

import com.mojang.blaze3d.vertex.PoseStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.PaintingRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.pryzma.PryzmaConfig;
import net.pryzma.render.PrFastPaintings;

/** Fast Paintings: the whole painting in one lit box. */
@Mixin(PaintingRenderer.class)
abstract class PaintingRendererMixin {
    @Inject(method = "renderPainting", at = @At("HEAD"), cancellable = true)
    private void prFastPaintings(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, RenderType renderType,
            int[] lightCoordsMap, int width, int height, TextureAtlasSprite front, TextureAtlasSprite back, CallbackInfo ci) {
        if (PryzmaConfig.prFastPaintings) {
            int light = (lightCoordsMap != null && lightCoordsMap.length > 0)
                    ? lightCoordsMap[(width / 2) + (height / 2) * width]
                    : 0xF000F0;
            submitNodeCollector.submitCustomGeometry(poseStack, renderType, (pose, consumer) -> {
                PrFastPaintings.render(pose, consumer, width, height, front, back, light);
            });
            ci.cancel();
        }
    }
}
