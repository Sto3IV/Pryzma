package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.server.packs.resources.ResourceManager;
import net.pryzma.entity.model.PrCemModels;

/**
 * Custom Entity Models enter where every entity and block entity model is born: the layer bake.
 * The model set reloads before the renderer dispatchers bake their layers, so the models of the
 * new resource state are known by then.
 */
@Mixin(EntityModelSet.class)
abstract class EntityModelSetMixin {
    @Inject(method = "onResourceManagerReload", at = @At("TAIL"))
    private void prCemReload(ResourceManager manager, CallbackInfo ci) {
        PrCemModels.onModelSetReload(manager);
    }

    @Inject(method = "bakeLayer", at = @At("RETURN"), cancellable = true)
    private void prCemBake(ModelLayerLocation layer, CallbackInfoReturnable<ModelPart> cir) {
        ModelPart vanilla = cir.getReturnValue();
        ModelPart custom = PrCemModels.apply(layer, vanilla);
        if (custom != vanilla) {
            cir.setReturnValue(custom);
        }
    }
}
