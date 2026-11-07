package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.pryzma.item.PrCit;

/** Custom Item Textures for worn armor: {@code type=armor} textures and the armor's enchantment layers. */
@Mixin(HumanoidArmorLayer.class)
abstract class HumanoidArmorLayerMixin<S extends HumanoidRenderState, M extends HumanoidModel<S>, A extends HumanoidModel<S>> {
    @WrapMethod(method = "renderArmorPiece(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/EquipmentSlot;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V")
    private void prCitArmorPiece(PoseStack pose, SubmitNodeCollector collector, ItemStack stack, EquipmentSlot slot,
            int light, S state, Operation<Void> original) {
        PrCit.Scope scope = PrCit.beginWorn(stack);
        try {
            original.call(pose, collector, stack, slot, light, state);
        } finally {
            PrCit.end(scope);
        }
    }
}
