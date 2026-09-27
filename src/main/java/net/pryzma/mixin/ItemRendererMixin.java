package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.pryzma.item.PrCit;

/** Custom Item Textures: item models around override resolution, and the item a glint belongs to. */
@Mixin(ItemRenderer.class)
abstract class ItemRendererMixin {
    @WrapOperation(method = "getModel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/block/model/ItemOverrides;resolve(Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/world/entity/LivingEntity;I)Lnet/minecraft/client/resources/model/BakedModel;"))
    private BakedModel prCitModel(ItemOverrides overrides, BakedModel model, ItemStack stack, ClientLevel level,
            LivingEntity entity, int seed, Operation<BakedModel> original) {
        if (!PrCit.hasRules(stack)) {
            return original.call(overrides, model, stack, level, entity, seed);
        }
        return PrCit.resolve(stack, overrides, model, (o, m) -> original.call(o, m, stack, level, entity, seed));
    }

    @WrapMethod(method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V")
    private void prCitRender(ItemStack stack, ItemDisplayContext context, boolean leftHand, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay, BakedModel model, Operation<Void> original) {
        PrCit.Scope scope = PrCit.beginItem(stack, context);
        try {
            original.call(stack, context, leftHand, pose, buffers, light, overlay, model);
        } finally {
            PrCit.end(scope);
        }
    }
}
