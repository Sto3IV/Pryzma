package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.pryzma.item.PrCit;

/** Custom Item Textures for the elytra: {@code type=elytra} textures and its enchantment layers. */
@Mixin(WingsLayer.class)
abstract class ElytraLayerMixin<S extends HumanoidRenderState, M extends EntityModel<S>> {
    @WrapMethod(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V")
    private void prCitElytra(PoseStack pose, SubmitNodeCollector collector, int light, S state, float limbSwing,
            float limbSpeed, Operation<Void> original) {
        PrCit.Scope scope = PrCit.beginWorn(state.chestEquipment);
        try {
            original.call(pose, collector, light, state, limbSwing, limbSpeed);
        } finally {
            PrCit.end(scope);
        }
    }

    @ModifyReturnValue(method = "getPlayerElytraTexture", at = @At("RETURN"))
    private static Identifier prCitElytraTexture(Identifier original, HumanoidRenderState state) {
        return PrCit.elytraTexture(state.chestEquipment, original);
    }
}
