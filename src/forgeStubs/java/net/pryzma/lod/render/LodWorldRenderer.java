package net.pryzma.lod.render;

import net.pryzma.lod.config.LodConfig;
import net.pryzma.lod.data.LodRegion;
import net.pryzma.lod.data.LodWorldStorage;
import net.pryzma.lod.exec.PryzmaLodExecutor;
import net.pryzma.lod.intake.AnvilRegionScanner;
import net.pryzma.lod.mesh.LodMeshBuffer;
import net.pryzma.lod.mesh.LodMesher;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Main rendering coordinator for native Pryzma LOD terrain and water.
 * Injected directly into LevelRenderer.renderSectionLayer before shader clear.
 */
public final class LodWorldRenderer {
    private static final ConcurrentHashMap<Long, RegionRenderBuffers> REGION_BUFFERS = new ConcurrentHashMap<>();
    private static final ConcurrentLinkedQueue<PendingUpload> PENDING_UPLOADS = new ConcurrentLinkedQueue<>();

    private record RegionRenderBuffers(LodMeshBuffer opaque, LodMeshBuffer water) {
    }

    private record PendingUpload(long regionKey, LodMesher.MeshResult result) {
    }

    public static void renderSectionLayer(LevelRenderer levelRenderer, RenderType renderType,
                                          double camX, double camY, double camZ,
                                          Matrix4f modelView, Matrix4f projection) {
        if (!LodConfig.isEnabled()) {
            return;
        }

        boolean isSolid = (renderType == RenderType.solid());
        boolean isWater = (renderType == RenderType.translucent());
        if (!isSolid && !isWater) {
            return;
        }
        if (isWater && !LodConfig.isRenderWater()) {
            return;
        }

        // Safety check for shader shadow pass
        try {
            Class<?> shadersClass = Class.forName("net.pryzma.shaders.Shaders");
            java.lang.reflect.Field shadowField = shadersClass.getField("isShadowPass");
            if (shadowField.getBoolean(null)) {
                return;
            }
        } catch (Throwable ignored) {
            try {
                Class<?> shadersClass = Class.forName("net.optifine.shaders.Shaders");
                java.lang.reflect.Field shadowField = shadersClass.getField("isShadowPass");
                if (shadowField.getBoolean(null)) {
                    return;
                }
            } catch (Throwable ignored2) {
            }
        }

        // Trigger background Anvil region scan to load all distant chunks from disk
        AnvilRegionScanner.triggerScan(camX, camZ);

        LodWorldStorage storage = LodWorldStorage.get();
        if (storage.getRegionCount() == 0) {
            return;
        }

        // Process pending mesh uploads on the OpenGL render thread with budget limit per frame
        int uploadBudget = 8;
        PendingUpload pending;
        while (uploadBudget > 0 && (pending = PENDING_UPLOADS.poll()) != null) {
            RegionRenderBuffers b = REGION_BUFFERS.computeIfAbsent(pending.regionKey(),
                    k -> new RegionRenderBuffers(new LodMeshBuffer(), new LodMeshBuffer()));
            b.opaque().upload(pending.result().opaqueMesh().getByteBuffer(), pending.result().opaqueMesh().getVertexCount());
            b.water().upload(pending.result().waterMesh().getByteBuffer(), pending.result().waterMesh().getVertexCount());
            uploadBudget--;
        }

        int maxDistBlocks = LodConfig.getLodDistanceChunks() * 16;
        double maxDistSq = (double) maxDistBlocks * maxDistBlocks;

        ShaderInstance shader = RenderSystem.getShader();
        Uniform chunkOffsetUniform = (shader != null) ? shader.CHUNK_OFFSET : null;

        Object ucoObj = null;
        java.lang.reflect.Method setValMethod = null;
        try {
            Class<?> shadersClass = Class.forName("net.pryzma.shaders.Shaders");
            java.lang.reflect.Field ucoField = shadersClass.getField("uniform_chunkOffset");
            ucoObj = ucoField.get(null);
            if (ucoObj != null) {
                setValMethod = ucoObj.getClass().getMethod("setValue", float.class, float.class, float.class);
            }
        } catch (Throwable ignored) {
            try {
                Class<?> shadersClass = Class.forName("net.optifine.shaders.Shaders");
                java.lang.reflect.Field ucoField = shadersClass.getField("uniform_chunkOffset");
                ucoObj = ucoField.get(null);
                if (ucoObj != null) {
                    setValMethod = ucoObj.getClass().getMethod("setValue", float.class, float.class, float.class);
                }
            } catch (Throwable ignored2) {
            }
        }

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(1.0f, 1.0f);

        if (isWater) {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        }

        try {
            for (LodRegion region : storage.getAllRegions()) {
                int rCenterX = region.getMinBlockX() + 128;
                int rCenterZ = region.getMinBlockZ() + 128;
                double dx = rCenterX - camX;
                double dz = rCenterZ - camZ;
                double distSq = dx * dx + dz * dz;

                // Distance culling
                if (distSq > maxDistSq) {
                    continue;
                }

                long key = LodRegion.getRegionKey(region.regionX, region.regionZ);
                RegionRenderBuffers buffers = REGION_BUFFERS.computeIfAbsent(key,
                        k -> new RegionRenderBuffers(new LodMeshBuffer(), new LodMeshBuffer()));

                // Asynchronous remeshing if dirty
                if (region.isMeshDirty() && region.tryStartMeshing()) {
                    region.setMeshDirty(false);
                    PryzmaLodExecutor.execute(() -> {
                        try {
                            LodMesher.MeshResult result = LodMesher.buildRegionMesh(region);
                            PENDING_UPLOADS.offer(new PendingUpload(key, result));
                        } finally {
                            region.finishMeshing();
                        }
                    });
                }

                // Check if buffer is uploaded before setting uniforms
                boolean hasMesh = isSolid ? buffers.opaque().isUploaded() : buffers.water().isUploaded();
                if (!hasMesh) {
                    continue;
                }

                float offX = (float) (region.getMinBlockX() - camX);
                float offY = (float) (-camY);
                float offZ = (float) (region.getMinBlockZ() - camZ);

                if (chunkOffsetUniform != null) {
                    chunkOffsetUniform.set(offX, offY, offZ);
                    chunkOffsetUniform.upload();
                }

                if (ucoObj != null && setValMethod != null) {
                    try {
                        setValMethod.invoke(ucoObj, offX, offY, offZ);
                    } catch (Throwable ignored) {
                    }
                }

                if (isSolid) {
                    buffers.opaque().render();
                } else {
                    buffers.water().render();
                }
            }
        } finally {
            if (chunkOffsetUniform != null) {
                chunkOffsetUniform.set(0.0f, 0.0f, 0.0f);
                chunkOffsetUniform.upload();
            }
            if (ucoObj != null && setValMethod != null) {
                try {
                    setValMethod.invoke(ucoObj, 0.0f, 0.0f, 0.0f);
                } catch (Throwable ignored) {
                }
            }
            GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
            GL11.glPolygonOffset(0.0f, 0.0f);
            if (isWater) {
                GL11.glDisable(GL11.GL_BLEND);
            }
        }
    }

    public static void reset() {
        PENDING_UPLOADS.clear();
        for (RegionRenderBuffers b : REGION_BUFFERS.values()) {
            b.opaque().delete();
            b.water().delete();
        }
        REGION_BUFFERS.clear();
        LodWorldStorage.get().clear();
    }
}
