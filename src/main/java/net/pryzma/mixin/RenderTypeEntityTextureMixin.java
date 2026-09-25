package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.pryzma.entity.texture.PrEntityTextures;

/**
 * Every entity render type factory maps its texture through {@link PrEntityTextures#remap}, which
 * picks the random variant of the entity being rendered and, in the emissive pass, its emissive
 * texture (OptiFine's {@code RenderType.getCustomTexture}). One-argument overloads that delegate to
 * the two-argument ones are left alone so a texture is mapped exactly once.
 */
@Mixin(RenderType.class)
abstract class RenderTypeEntityTextureMixin {
    @ModifyVariable(method = {
            "armorCutoutNoCull",
            "createArmorDecalCutoutNoCull",
            "entitySolid",
            "entityCutout",
            "entityCutoutNoCull(Lnet/minecraft/resources/ResourceLocation;Z)Lnet/minecraft/client/renderer/RenderType;",
            "entityCutoutNoCullZOffset(Lnet/minecraft/resources/ResourceLocation;Z)Lnet/minecraft/client/renderer/RenderType;",
            "itemEntityTranslucentCull",
            "entityTranslucentCull",
            "entityTranslucent(Lnet/minecraft/resources/ResourceLocation;Z)Lnet/minecraft/client/renderer/RenderType;",
            "entityTranslucentEmissive(Lnet/minecraft/resources/ResourceLocation;Z)Lnet/minecraft/client/renderer/RenderType;",
            "entitySmoothCutout",
            "entityDecal",
            "entityNoOutline",
            "dragonExplosionAlpha",
            "eyes",
            "breezeEyes",
            "breezeWind",
            "energySwirl"
    }, at = @At("HEAD"), argsOnly = true)
    private static ResourceLocation prEntityTexture(ResourceLocation texture) {
        return PrEntityTextures.remap(texture);
    }

    /** OptiFine draws the emissive layer of a solid entity with cutout, so transparent pixels stay empty. */
    @Inject(method = "entitySolid", at = @At("HEAD"), cancellable = true)
    private static void prEmissiveSolid(ResourceLocation texture, CallbackInfoReturnable<RenderType> cir) {
        if (PrEntityTextures.isEmissivePass()) {
            cir.setReturnValue(RenderType.entityCutout(texture));
        }
    }
}
