package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.pryzma.color.PryzmaColormaps;
import net.pryzma.render.PrSkyColorMemo;

/** Sky colormap, a per-frame sky colour memo, and keeping the colormap tint caches in step with vanilla's. */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
    @ModifyReturnValue(method = "getSkyColor", at = @At("RETURN"))
    private Vec3 prCustomSkyColor(Vec3 original, @Local(argsOnly = true) Vec3 pos) {
        return PryzmaColormaps.skyColor(original, (ClientLevel) (Object) this, pos.x, pos.y, pos.z);
    }

    @Inject(method = "clearTintCaches", at = @At("TAIL"))
    private void prClearTintCaches(CallbackInfo ci) {
        PryzmaColormaps.clearCaches();
        this.pryzma$skyColors.clear();
    }

    // Sky colour memo: getSkyColor gaussian-samples 216 biome colours (about 650 Vec3 allocations) and runs
    // several times a frame with the same inputs: sky and fog at the camera, the shader pack's skyColor uniforms
    // at the camera entity's feet. Two entries, so the two positions do not evict each other. The result depends
    // on the position, the partial tick, and state that advances once per tick (day time, rain, thunder, flash).
    @Unique private final PrSkyColorMemo pryzma$skyColors = new PrSkyColorMemo();

    @WrapMethod(method = "getSkyColor")
    private Vec3 prSkyColorMemo(Vec3 pos, float partialTick, Operation<Vec3> original) {
        ClientLevel level = (ClientLevel) (Object) this;
        long gameTime = level.getGameTime();
        long dayTime = level.getDayTime();
        Vec3 cached = this.pryzma$skyColors.get(pos.x, pos.y, pos.z, partialTick, gameTime, dayTime);
        if (cached != null) {
            return cached;
        }
        Vec3 color = original.call(pos, partialTick);
        this.pryzma$skyColors.put(pos.x, pos.y, pos.z, partialTick, gameTime, dayTime, color);
        return color;
    }

    @Inject(method = "onChunkLoaded", at = @At("TAIL"))
    private void prChunkLoaded(ChunkPos chunkPos, CallbackInfo ci) {
        PryzmaColormaps.invalidateChunk(chunkPos.x, chunkPos.z);
    }
}
