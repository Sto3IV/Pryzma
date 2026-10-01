package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.world.level.ChunkPos;
import net.pryzma.render.PrBiomeCache;

/**
 * Invalidates the render-thread biome cache when a chunk is dropped.
 */
@Mixin(ClientChunkCache.class)
public abstract class ClientChunkCacheMixin {
    @Inject(method = "drop", at = @At("HEAD"))
    private void prChunkDropped(ChunkPos chunkPos, CallbackInfo ci) {
        PrBiomeCache.invalidate();
    }
}
