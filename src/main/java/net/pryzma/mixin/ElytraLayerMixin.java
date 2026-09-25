package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.pryzma.item.PrCit;

/** Custom Item Textures for the elytra: {@code type=elytra} textures and its enchantment layers. */
@Mixin(ElytraLayer.class)
abstract class ElytraLayerMixin {
    @WrapMethod(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V")
    private void prCitElytra(PoseStack pose, MultiBufferSource buffers, int light, LivingEntity entity, float limbSwing,
            float limbSpeed, float partialTick, float age, float headYaw, float headPitch, Operation<Void> original) {
        PrCit.Scope scope = PrCit.beginWorn(entity.getItemBySlot(EquipmentSlot.CHEST));
        try {
            original.call(pose, buffers, light, entity, limbSwing, limbSpeed, partialTick, age, headYaw, headPitch);
        } finally {
            PrCit.end(scope);
        }
    }

    @ModifyReturnValue(method = "getElytraTexture", at = @At("RETURN"))
    private ResourceLocation prCitElytraTexture(ResourceLocation original, @Local(argsOnly = true) ItemStack stack) {
        return PrCit.elytraTexture(stack, original);
    }
}
