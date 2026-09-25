package net.pryzma.shader;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import com.mojang.blaze3d.platform.GlStateManager;

/**
 * The shadow map of a shader pack: {@code shadowtex0} (depth of everything the shadow pass
 * draws), {@code shadowtex1} (depth without translucent terrain) and {@code shadowcolor0/1}, all
 * {@code shadowMapResolution} square. With {@code shadowHardwareFiltering} the depth textures
 * compare against a reference, for {@code sampler2DShadow}.
 */
final class PrShadowTargets {
    final int size;
    final int depth0;
    final int depth1;
    final int color0;
    final int color1;
    private final int fbo;
    private final int copyFbo;

    PrShadowTargets(PrShaderConfig config, int colorFormat0, int colorFormat1) {
        size = Math.max(16, Math.min(8192, config.shadowMapResolution));
        depth0 = PrRenderTargets.depthTexture(size, size);
        depth1 = PrRenderTargets.depthTexture(size, size);
        configureDepth(depth0, config.shadowHardwareFiltering[0], config.shadowNearest[0]);
        configureDepth(depth1, config.shadowHardwareFiltering[1], config.shadowNearest[1]);
        color0 = PrRenderTargets.colorTexture(colorFormat0, size, size);
        color1 = PrRenderTargets.colorTexture(colorFormat1, size, size);
        fbo = GlStateManager.glGenFramebuffers();
        copyFbo = GlStateManager.glGenFramebuffers();
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, depth0, 0);
        GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, color0, 0);
        GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT1, GL11.GL_TEXTURE_2D, color1, 0);
    }

    private static void configureDepth(int texture, boolean hardwareFiltering, boolean nearest) {
        GlStateManager._bindTexture(texture);
        int filter = nearest ? GL11.GL_NEAREST : GL11.GL_LINEAR;
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE,
                hardwareFiltering ? GL30.GL_COMPARE_REF_TO_TEXTURE : GL11.GL_NONE);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_FUNC, GL11.GL_LEQUAL);
    }

    /** Binds the shadow map for a shadow program writing {@code drawBuffers} (indices of shadowcolor). */
    void bind(int[] drawBuffers) {
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        int[] ids = new int[drawBuffers.length];
        for (int j = 0; j < drawBuffers.length; j++) {
            ids[j] = drawBuffers[j] == 0 || drawBuffers[j] == 1 ? GL30.GL_COLOR_ATTACHMENT0 + drawBuffers[j] : GL11.GL_NONE;
        }
        GL20.glDrawBuffers(ids);
        GlStateManager._viewport(0, 0, size, size);
    }

    /** Clears the map for a new frame: depth to far, colour to white (unshadowed). */
    void clear() {
        bind(new int[] {0, 1});
        GL30.glClearBufferfv(GL11.GL_COLOR, 0, new float[] {1, 1, 1, 1});
        GL30.glClearBufferfv(GL11.GL_COLOR, 1, new float[] {1, 1, 1, 1});
        GL30.glClearBufferfv(GL11.GL_DEPTH, 0, new float[] {1.0F});
    }

    /** Keeps the depth drawn so far, before translucent terrain, as {@code shadowtex1}. */
    void copyOpaqueDepth() {
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, fbo);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copyFbo);
        GlStateManager._glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, depth1, 0);
        GL30.glBlitFramebuffer(0, 0, size, size, 0, 0, size, size, GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
    }

    void release() {
        for (int t : new int[] {depth0, depth1, color0, color1}) {
            GlStateManager._deleteTexture(t);
        }
        GlStateManager._glDeleteFramebuffers(fbo);
        GlStateManager._glDeleteFramebuffers(copyFbo);
    }
}
