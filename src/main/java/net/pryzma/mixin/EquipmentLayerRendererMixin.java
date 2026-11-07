package net.pryzma.mixin;

import java.util.function.Function;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.pryzma.item.PrCit;

@Mixin(EquipmentLayerRenderer.class)
abstract class EquipmentLayerRendererMixin {
    @WrapOperation(method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V",
            at = @At(value = "INVOKE", target = "Ljava/util/function/Function;apply(Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object prCitArmorTexture(Function<?, ?> instance, Object key, Operation<Object> original,
            @Local(argsOnly = true) ItemStack itemStack) {
        Object res = original.call(instance, key);
        if (res instanceof Identifier id && itemStack != null && !itemStack.isEmpty()) {
            return PrCit.armorTexture(itemStack, id);
        }
        return res;
    }
}
