package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.texture.AbstractTexture;
import net.pryzma.render.PrTextureFilter;

/** Mipmap Type replaces vanilla's GL_NEAREST_MIPMAP_LINEAR; anisotropy follows every mipmapped filter set. */
@Mixin(AbstractTexture.class)
abstract class AbstractTextureFilterMixin {
    @ModifyConstant(method = "setFilter", constant = @Constant(intValue = 9986))
    private int prMipmapType(int vanilla) {
        return PrTextureFilter.minFilter();
    }

    @Inject(method = "setFilter", at = @At("TAIL"))
    private void prAnisotropy(boolean blur, boolean mipmap, CallbackInfo ci) {
        if (mipmap) {
            PrTextureFilter.applyAnisotropy();
        }
    }
}
