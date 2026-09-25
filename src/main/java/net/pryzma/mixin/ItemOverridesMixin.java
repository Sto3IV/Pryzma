package net.pryzma.mixin;

import java.util.function.Function;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.block.model.ItemOverride;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.UnbakedModel;
import net.pryzma.item.PrCitOverrides;

/** Remembers the location of every override model, which CIT sub-models are named after. */
@Mixin(ItemOverrides.class)
abstract class ItemOverridesMixin {
    @Inject(method = "bakeModel", at = @At("RETURN"))
    private void prRecordOverride(ModelBaker baker, UnbakedModel model, ItemOverride override,
            Function<Material, TextureAtlasSprite> sprites, CallbackInfoReturnable<BakedModel> cir) {
        PrCitOverrides.record(cir.getReturnValue(), override.getModel());
    }
}
