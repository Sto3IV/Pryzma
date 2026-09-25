package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import net.minecraft.world.entity.Entity;
import net.pryzma.entity.PrEntityData;

/** Attaches {@link PrEntityData} to every entity, created on first use. */
@Mixin(Entity.class)
abstract class EntityDataMixin implements PrEntityData.Holder {
    @Unique
    private PrEntityData prData;

    @Override
    public PrEntityData prData() {
        PrEntityData data = prData;
        if (data == null) {
            data = new PrEntityData();
            prData = data;
        }
        return data;
    }
}
