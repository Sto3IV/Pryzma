package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.pryzma.item.PrCit;

@Mixin(ItemModelResolver.class)
abstract class ItemModelResolverMixin {
    @WrapMethod(method = "appendItemLayers")
    private void prCitAppendLayers(ItemStackRenderState state, ItemStack stack, ItemDisplayContext displayContext,
            Level level, ItemOwner owner, int seed, Operation<Void> original) {
        if (PrCit.hasRules(stack)) {
            boolean previousOffHand = PrCit.setOffHand(displayContext.leftHand());
            try {
                ItemModel customModel = PrCit.resolveModel(stack);
                if (customModel != null) {
                    ClientLevel clientLevel = level instanceof ClientLevel cl ? cl : null;
                    customModel.update(state, stack, (ItemModelResolver) (Object) this, displayContext, clientLevel, owner, seed);
                    return;
                }
            } finally {
                PrCit.setOffHand(previousOffHand);
            }
        }
        original.call(state, stack, displayContext, level, owner, seed);
    }
}
