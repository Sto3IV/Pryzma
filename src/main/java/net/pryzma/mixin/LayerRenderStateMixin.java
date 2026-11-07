package net.pryzma.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.world.item.ItemDisplayContext;
import net.pryzma.PryzmaConfig;
import net.pryzma.item.PrFlatItem;

/**
 * Dropped Items: strips extruded 1-pixel thickness so 2D dropped items on the ground render as a single flat sprite quad.
 */
@Mixin(ItemStackRenderState.LayerRenderState.class)
abstract class LayerRenderStateMixin {

    @WrapOperation(
            method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitItem(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;III[ILnet/minecraft/client/resources/model/geometry/ItemQuads;Lnet/minecraft/client/renderer/item/ItemStackRenderState$FoilType;)V")
    )
    private void prFlattenItemQuads(SubmitNodeCollector collector, PoseStack poseStack, ItemDisplayContext displayContext,
            int lightCoords, int overlayCoords, int outlineColor, int[] tints, ItemQuads quads,
            ItemStackRenderState.FoilType foilType, Operation<Void> original) {
        if (displayContext == ItemDisplayContext.GROUND && !PryzmaConfig.isDroppedItemsFancy()) {
            quads = PrFlatItem.flatten(quads);
        }
        original.call(collector, poseStack, displayContext, lightCoords, overlayCoords, outlineColor, tints, quads, foilType);
    }
}
