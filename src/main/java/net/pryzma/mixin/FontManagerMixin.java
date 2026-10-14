package net.pryzma.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.client.gui.font.FontManager;
import net.minecraft.server.packs.resources.Resource;
import net.pryzma.render.PrFontUtils;

@Mixin(FontManager.class)
abstract class FontManagerMixin {
    @ModifyVariable(method = "loadResourceStack", at = @At("HEAD"), argsOnly = true)
    private static List<Resource> pryzma$filterCustomFontResources(List<Resource> resources) {
        return PrFontUtils.filterCustomFontResources(resources);
    }
}
