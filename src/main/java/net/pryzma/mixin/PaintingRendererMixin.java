package net.pryzma.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.entity.PaintingRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.entity.decoration.Painting;
import net.pryzma.PryzmaConfig;
import net.pryzma.render.PrFastPaintings;

/** Fast Paintings: the whole painting in one lit box. */
@Mixin(PaintingRenderer.class)
abstract class PaintingRendererMixin {
    @Inject(method = "renderPainting", at = @At("HEAD"), cancellable = true)
    private void prFastPaintings(PoseStack poseStack, VertexConsumer consumer, Painting painting, int width, int height,
            TextureAtlasSprite paintingSprite, TextureAtlasSprite backSprite, CallbackInfo ci) {
        if (PryzmaConfig.prFastPaintings) {
            PrFastPaintings.render(poseStack, consumer, painting, width, height, paintingSprite, backSprite);
            ci.cancel();
        }
    }
}
