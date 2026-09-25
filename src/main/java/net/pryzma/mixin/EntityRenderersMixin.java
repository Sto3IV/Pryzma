package net.pryzma.mixin;

import java.util.Map;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.world.entity.EntityType;
import net.pryzma.entity.model.PrCemModels;

/** Random CEM model variants get their own renderers, built right after the regular ones. */
@Mixin(EntityRenderers.class)
abstract class EntityRenderersMixin {
    @Shadow @Final private static Map<EntityType<?>, EntityRendererProvider<?>> PROVIDERS;

    @Inject(method = "createEntityRenderers", at = @At("RETURN"))
    private static void prCemVariants(EntityRendererProvider.Context context,
            CallbackInfoReturnable<Map<EntityType<?>, EntityRenderer<?>>> cir) {
        PrCemModels.onEntityRenderersCreated(context, PROVIDERS, cir.getReturnValue());
    }
}
