package net.pryzma.iris.mixin;

import net.pryzma.iris.api.v0.item.IrisItemLightProvider;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Item.class)
public class MixinItem implements IrisItemLightProvider {
}
