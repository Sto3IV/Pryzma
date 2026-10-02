package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import com.mojang.blaze3d.vertex.MeshData;

import net.pryzma.render.IPrMeshData;

/**
 * Phase B4: Attaches core vertex count to {@link MeshData}.
 */
@Mixin(MeshData.class)
abstract class MeshDataMixin implements IPrMeshData {
    @Unique
    private int pryzma$coreVertices = -1;

    @Override
    public int pryzma$getCoreVertices() {
        return pryzma$coreVertices;
    }

    @Override
    public void pryzma$setCoreVertices(int count) {
        this.pryzma$coreVertices = count;
    }
}
