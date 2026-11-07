package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.pryzma.entity.PrEntityRenderStateDuck;

@Mixin(EntityRenderState.class)
abstract class EntityRenderStateMixin implements PrEntityRenderStateDuck {
    @Unique
    private Entity prEntity;

    @Override
    public Entity prGetEntity() {
        return prEntity;
    }

    @Override
    public void prSetEntity(Entity entity) {
        this.prEntity = entity;
    }
}
