package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.SingleThreadedRandomSource;
import net.pryzma.render.PrBiomeCache;

/**
 * Eliminates 100% of redundant getBiome lookups and per-column RandomSource allocations
 * during rain and snow rendering.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererBiomeMixin {
    @Unique private static final RandomSource PR_RAIN_RANDOM = new SingleThreadedRandomSource(0L);

    @Redirect(method = "renderSnowAndRain", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBiome(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/Holder;"))
    private Holder<Biome> prRainBiome(Level level, BlockPos pos) {
        return PrBiomeCache.biome(level, pos);
    }

    /** Bit-identical to RandomSource.create(seed); setSeed resets the Marsaglia Gaussian. Eliminates 441 objects/frame. */
    @Redirect(method = "renderSnowAndRain", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/RandomSource;create(J)Lnet/minecraft/util/RandomSource;"))
    private RandomSource prRainRandom(long seed) {
        PR_RAIN_RANDOM.setSeed(seed);
        return PR_RAIN_RANDOM;
    }

    @Inject(method = "setLevel", at = @At("HEAD"))
    private void prSetLevel(ClientLevel level, CallbackInfo ci) {
        PrBiomeCache.release();
    }
}
