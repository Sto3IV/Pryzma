package net.pryzma.item;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * An item model whose quads come from a CIT texture model in Minecraft 26.3.
 */
final class PrCitTextureModel implements ItemModel {
    private final ItemModel original;
    private final ItemModel quads;

    PrCitTextureModel(ItemModel original, ItemModel quads) {
        this.original = original;
        this.quads = quads;
    }

    @Override
    public void update(ItemStackRenderState renderState, ItemStack stack, ItemModelResolver resolver,
                       ItemDisplayContext displayContext, ClientLevel level, ItemOwner owner, int seed) {
        if (quads != null) {
            quads.update(renderState, stack, resolver, displayContext, level, owner, seed);
        } else if (original != null) {
            original.update(renderState, stack, resolver, displayContext, level, owner, seed);
        }
    }
}
