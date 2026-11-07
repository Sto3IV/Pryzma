package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;

@Mixin(RenderType.class)
public interface RenderTypeAccessor {
    @Accessor("state")
    RenderSetup prGetState();

    @Accessor("name")
    String prGetName();
}
