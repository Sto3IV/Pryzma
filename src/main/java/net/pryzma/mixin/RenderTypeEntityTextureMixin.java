package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.pryzma.entity.texture.PrEntityTextures;

/**
 * Every entity render type factory maps its texture through {@link PrEntityTextures#remap}, which
 * picks the random variant of the entity being rendered and, in the emissive pass, its emissive
 * texture (OptiFine's {@code RenderType.getCustomTexture}). One-argument overloads that delegate to
 * the two-argument ones are left alone so a texture is mapped exactly once.
 */
@Mixin(RenderTypes.class)
abstract class RenderTypeEntityTextureMixin {
    @ModifyVariable(method = {
            "armorCutoutNoCull(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;",
            "armorTrim(Lnet/minecraft/resources/Identifier;Z)Lnet/minecraft/client/renderer/rendertype/RenderType;",
            "wolfArmorCracks",
            "entitySolid(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;",
            "entityCutout(Lnet/minecraft/resources/Identifier;Z)Lnet/minecraft/client/renderer/rendertype/RenderType;",
            "entityCutoutCull",
            "entityCutoutZOffset(Lnet/minecraft/resources/Identifier;Z)Lnet/minecraft/client/renderer/rendertype/RenderType;",
            "entityTranslucent(Lnet/minecraft/resources/Identifier;Z)Lnet/minecraft/client/renderer/rendertype/RenderType;",
            "entityTranslucentCull",
            "entityTranslucentEmissive(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;",
            "entityShadow",
            "eyes",
            "breezeEyes",
            "breezeWind",
            "energySwirl"
    }, at = @At("HEAD"), argsOnly = true)
    private static Identifier prEntityTexture(Identifier texture) {
        return PrEntityTextures.remap(texture);
    }

    /** OptiFine draws the emissive layer of a solid entity with cutout, so transparent pixels stay empty. */
    @Inject(method = "entitySolid(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;", at = @At("HEAD"), cancellable = true)
    private static void prEmissiveSolid(Identifier texture, CallbackInfoReturnable<RenderType> cir) {
        if (PrEntityTextures.isEmissivePass()) {
            cir.setReturnValue(RenderTypes.entityCutout(texture));
        }
    }
}
