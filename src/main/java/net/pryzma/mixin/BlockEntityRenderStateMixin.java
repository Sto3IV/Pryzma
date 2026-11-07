package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.pryzma.entity.PrBlockEntityRenderStateDuck;

@Mixin(BlockEntityRenderState.class)
abstract class BlockEntityRenderStateMixin implements PrBlockEntityRenderStateDuck {
    @Unique
    private BlockEntity prBlockEntity;

    @Override
    public BlockEntity prGetBlockEntity() {
        return prBlockEntity;
    }

    @Override
    public void prSetBlockEntity(BlockEntity blockEntity) {
        this.prBlockEntity = blockEntity;
    }
}
