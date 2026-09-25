package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.pryzma.item.PrCit;

/**
 * Tells CIT which hand an item is drawn in (rules with {@code hand=}): every held item, first and
 * third person, passes here with the arm it is drawn on.
 */
@Mixin(ItemInHandRenderer.class)
abstract class ItemInHandRendererMixin {
    @WrapMethod(method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V")
    private void prCitHand(LivingEntity entity, ItemStack stack, ItemDisplayContext context, boolean leftHand,
            PoseStack pose, MultiBufferSource buffers, int light, Operation<Void> original) {
        boolean previous = PrCit.setOffHand(leftHand != (entity.getMainArm() == HumanoidArm.LEFT));
        try {
            int dynLight = net.pryzma.light.PrDynamicLights.getItemInHandLight(entity, light);
            original.call(entity, stack, context, leftHand, pose, buffers, dynLight);
        } finally {
            PrCit.setOffHand(previous);
        }
    }
}
