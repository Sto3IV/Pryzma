package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.pryzma.color.PryzmaColormaps;

/** Sky colormap, and keeping the colormap tint caches in step with vanilla's. */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
    @ModifyReturnValue(method = "getSkyColor", at = @At("RETURN"))
    private Vec3 prCustomSkyColor(Vec3 original, @Local(argsOnly = true) Vec3 pos) {
        return PryzmaColormaps.skyColor(original, (ClientLevel) (Object) this, pos.x, pos.y, pos.z);
    }

    @Inject(method = "clearTintCaches", at = @At("TAIL"))
    private void prClearTintCaches(CallbackInfo ci) {
        PryzmaColormaps.clearCaches();
    }

    @Inject(method = "onChunkLoaded", at = @At("TAIL"))
    private void prChunkLoaded(ChunkPos chunkPos, CallbackInfo ci) {
        PryzmaColormaps.invalidateChunk(chunkPos.x, chunkPos.z);
    }
}
