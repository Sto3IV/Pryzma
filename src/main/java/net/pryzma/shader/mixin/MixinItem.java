package net.pryzma.shader.mixin;

import net.pryzma.shader.api.v0.item.ShaderItemLightProvider;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Item.class)
public class MixinItem implements ShaderItemLightProvider {
}
