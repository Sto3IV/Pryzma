package net.minecraftforge.client.extensions;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Stub for the Forge elytra-layer extension Pryzma's patched ElytraLayer implements.
 */
public interface IForgeElytraLayer<T> {
    default boolean shouldRender(ItemStack stack, T entity) {
        return stack.is(Items.ELYTRA);
    }

    default ResourceLocation getElytraTexture(ItemStack stack, T entity) {
        return ResourceLocation.withDefaultNamespace("textures/entity/elytra.png");
    }
}
