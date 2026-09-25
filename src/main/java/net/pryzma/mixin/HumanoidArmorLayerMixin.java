package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.pryzma.item.PrCit;

/** Custom Item Textures for worn armor: {@code type=armor} textures and the armor's enchantment layers. */
@Mixin(HumanoidArmorLayer.class)
abstract class HumanoidArmorLayerMixin {
    private static final String PIECE = "renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/EquipmentSlot;ILnet/minecraft/client/model/HumanoidModel;FFFFFF)V";

    @WrapMethod(method = PIECE)
    private void prCitArmorPiece(PoseStack pose, MultiBufferSource buffers, LivingEntity entity, EquipmentSlot slot,
            int light, HumanoidModel<?> model, float limbSwing, float limbSpeed, float partialTick, float age,
            float headYaw, float headPitch, Operation<Void> original) {
        PrCit.Scope scope = PrCit.beginWorn(entity.getItemBySlot(slot));
        try {
            original.call(pose, buffers, entity, slot, light, model, limbSwing, limbSpeed, partialTick, age, headYaw, headPitch);
        } finally {
            PrCit.end(scope);
        }
    }

    @WrapOperation(method = PIECE, at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/client/ClientHooks;getArmorTexture(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ArmorMaterial$Layer;ZLnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/resources/ResourceLocation;"))
    private ResourceLocation prCitArmorTexture(Entity entity, ItemStack stack, ArmorMaterial.Layer layer, boolean inner,
            EquipmentSlot slot, Operation<ResourceLocation> original) {
        return PrCit.armorTexture(stack, original.call(entity, stack, layer, inner, slot));
    }
}
