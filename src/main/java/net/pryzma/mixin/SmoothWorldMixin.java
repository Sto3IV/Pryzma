package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.pryzma.perf.PrSmoothWorld;

/**
 * Smooth World: distant calm mobs on the integrated server think every fourth tick. Only the AI
 * step is paced; {@code Mob.tick()} itself (physics, ageing, effects) always runs.
 */
@Mixin(Mob.class)
abstract class SmoothWorldMixin extends LivingEntity {
    private SmoothWorldMixin(EntityType<? extends LivingEntity> type, Level level) {
        super(type, level);
    }

    @Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
    private void prPaceDistantAi(CallbackInfo ci) {
        if (PrSmoothWorld.skipAi((Mob) (Object) this)) {
            // The first statement of the skipped step: despawn timing stays exactly vanilla's.
            this.noActionTime++;
            ci.cancel();
        }
    }
}
