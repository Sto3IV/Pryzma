package net.pryzma.mixin;

import java.io.IOException;
import java.io.InputStream;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.gui.font.providers.BitmapProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.pryzma.render.PrFontUtils;

@Mixin(BitmapProvider.Definition.class)
abstract class BitmapProviderDefinitionMixin {
    @WrapOperation(
        method = "load",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/packs/resources/ResourceManager;open(Lnet/minecraft/resources/ResourceLocation;)Ljava/io/InputStream;"
        )
    )
    private InputStream pryzma$wrapOpenFontImage(
        ResourceManager resourceManager,
        ResourceLocation location,
        Operation<InputStream> original
    ) throws IOException {
        return PrFontUtils.openFontImageStream(resourceManager, location, original);
    }
}
