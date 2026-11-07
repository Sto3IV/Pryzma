package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.multiplayer.ClientLevel;
import net.pryzma.color.PryzmaColormaps;
import net.pryzma.render.PrBiomeCache;

/** Clear colormap tint caches in step with vanilla. */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
    @Inject(method = "clearTintCaches", at = @At("TAIL"))
    private void prClearTintCaches(CallbackInfo ci) {
        PryzmaColormaps.clearCaches();
        PrBiomeCache.release();
    }
}
