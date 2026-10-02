package net.pryzma.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.pryzma.render.IPrMeshData;
import net.pryzma.render.IPrVertexBuffer;
import net.pryzma.render.PrRenderRegionManager;
import net.pryzma.render.VboRange;
import net.pryzma.render.VboRegion;

/**
 * Render Regions, buffer side: a region-managed section buffer deletes its own VBO, IBO and VAO,
 * uploads into the {@link VboRegion} of its region and queues its draws there. Every other buffer
 * keeps vanilla's path; each handler tests one field for it and stays small enough to inline.
 * Also integrates Phase B4: Decorator LOD Truncation across both region-managed and vanilla buffers.
 */
@Mixin(VertexBuffer.class)
abstract class VertexBufferRegionMixin implements IPrVertexBuffer {
    @Shadow private VertexFormat format;
    @Shadow private RenderSystem.AutoStorageIndexBuffer sequentialIndices;
    @Shadow private VertexFormat.IndexType indexType;
    @Shadow private int indexCount;
    @Shadow private VertexFormat.Mode mode;

    /** Region layer index; negative for a buffer that draws on its own. */
    @Unique private int pryzma$layer = -1;
    @Unique private long pryzma$regionKey;
    @Unique private VboRegion pryzma$region;
    @Unique private VboRange pryzma$range;
    @Unique private boolean pryzma$isFar;
    @Unique private int pryzma$coreVertices = -1;

    @Shadow
    public abstract void close();

    @Override
    public void pryzma$setFar(boolean far) {
        this.pryzma$isFar = far;
    }

    @Override
    public void pryzma$setRegionSlot(int layer, long key) {
        if (pryzma$layer < 0) {
            // Frees the buffer's own GL objects; nothing is drawn from them any more.
            close();
            pryzma$layer = layer;
        }
        if (pryzma$region != null && pryzma$region.getKey() != key) {
            // The section moved to another region: its vertices are stale and its compiled state was reset.
            pryzma$setVboRegion(null);
        }
        pryzma$regionKey = key;
    }

    @Override
    public boolean pryzma$isRegionManaged() {
        return pryzma$layer >= 0;
    }

    @Override
    public void pryzma$setVboRegion(VboRegion region) {
        VboRegion old = pryzma$region;
        if (old == region) {
            return;
        }
        if (old != null && pryzma$range != null) {
            PrRenderRegionManager.release(pryzma$layer, old, pryzma$range);
        }
        pryzma$region = region;
    }

    @Override
    public VboRegion pryzma$getVboRegion() {
        return pryzma$region;
    }

    @Override
    public VboRange pryzma$getVboRange() {
        return pryzma$range;
    }

    @Inject(method = "upload", at = @At("HEAD"), cancellable = true)
    private void prRegionUpload(MeshData meshData, CallbackInfo ci) {
        this.pryzma$coreVertices = ((IPrMeshData) (Object) meshData).pryzma$getCoreVertices();
        if (pryzma$layer >= 0) {
            pryzma$uploadToRegion(meshData);
            ci.cancel();
        }
    }

    /** Covers {@code drawWithShader} too, which draws through this method. */
    @Inject(method = "draw", at = @At("HEAD"), cancellable = true)
    private void prRegionDraw(CallbackInfo ci) {
        if (pryzma$layer >= 0) {
            pryzma$queueDraw();
            ci.cancel();
        } else if (this.pryzma$isFar && this.pryzma$coreVertices == 0) {
            ci.cancel();
        }
    }

    /**
     * Phase B4 without regions: a far section draws only its core, the first indices of a sequential index
     * buffer. Sorted (translucent) meshes never carry a core, so their sorted indices are never cut.
     */
    @ModifyArg(method = "draw", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;drawElements(III)V"), index = 1)
    private int prVanillaTruncateDraw(int count) {
        if (this.pryzma$isFar && this.pryzma$coreVertices > 0) {
            return mode.indexCount(this.pryzma$coreVertices);
        }
        return count;
    }

    /** No VAO of its own to bind. */
    @Inject(method = "bind", at = @At("HEAD"), cancellable = true)
    private void prRegionBind(CallbackInfo ci) {
        if (pryzma$layer >= 0) {
            ci.cancel();
        }
    }

    /** Its deleted GL objects do not make a region-managed buffer invalid: uploads must still reach it. */
    @Inject(method = "isInvalid", at = @At("HEAD"), cancellable = true)
    private void prRegionValid(CallbackInfoReturnable<Boolean> cir) {
        if (pryzma$layer >= 0) {
            cir.setReturnValue(false);
        }
    }

    /** Sorted index buffers belong to the translucent layer, which never uses regions. */
    @Inject(method = "uploadIndexBuffer(Lcom/mojang/blaze3d/vertex/ByteBufferBuilder$Result;)V", at = @At("HEAD"), cancellable = true)
    private void prRegionIndices(ByteBufferBuilder.Result result, CallbackInfo ci) {
        if (pryzma$layer >= 0) {
            result.close();
            ci.cancel();
        }
    }

    /** Releases the range; the buffer is invalid afterwards, as a closed vanilla buffer is. */
    @Inject(method = "close", at = @At("HEAD"))
    private void prRegionClose(CallbackInfo ci) {
        if (pryzma$layer >= 0) {
            pryzma$setVboRegion(null);
            pryzma$layer = -1;
            pryzma$range = null;
        }
        this.pryzma$coreVertices = -1;
        this.pryzma$isFar = false;
    }

    @Unique
    private void pryzma$uploadToRegion(MeshData meshData) {
        try {
            RenderSystem.assertOnRenderThread();
            MeshData.DrawState state = meshData.drawState();
            format = state.format();
            mode = state.mode();
            indexType = state.indexType();
            indexCount = state.indexCount();
            sequentialIndices = null;
            VboRegion region = pryzma$region;
            if (region == null || region.isDeleted() || region.getKey() != pryzma$regionKey || region.getFormat() != format) {
                region = PrRenderRegionManager.acquire(pryzma$layer, pryzma$regionKey, format);
                pryzma$setVboRegion(region);
            }
            if (pryzma$range == null) {
                pryzma$range = new VboRange();
            }
            pryzma$range.setCoreSize(this.pryzma$coreVertices);
            region.bufferData(meshData.vertexBuffer(), pryzma$range);
        } finally {
            meshData.close();
        }
    }

    @Unique
    private void pryzma$queueDraw() {
        VboRegion region = pryzma$region;
        VboRange range = pryzma$range;
        if (region != null && range != null && range.getPosition() >= 0 && !region.isDeleted()) {
            PrRenderRegionManager.queue(region, mode, range, pryzma$isFar);
        }
    }
}
