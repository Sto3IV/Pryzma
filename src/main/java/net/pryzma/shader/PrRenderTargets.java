package net.pryzma.shader;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;
import java.util.Random;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import com.mojang.blaze3d.platform.GlStateManager;

/**
 * The G-buffers of a shader pack: {@code colortex0..15} as pairs of textures (composite passes
 * read one and write the other, then swap), the depth textures ({@code depthtex0} is the world's
 * depth buffer, {@code depthtex1} a copy before translucent terrain, {@code depthtex2} a copy
 * before the hand) and {@code noisetex}. Buffers cleared every frame start the frame unswapped;
 * a buffer with clearing disabled keeps its state, so temporal effects read the last frame.
 */
final class PrRenderTargets {
    /** Fragment outputs a program may write; GL guarantees 8 draw buffers and no more. */
    static final int MAX_OUTPUTS = 8;

    private final PrShaderConfig config;
    private final boolean[] allocated = new boolean[PrShaderConfig.BUFFERS];
    private final int[][] textures = new int[PrShaderConfig.BUFFERS][2];
    private final boolean[] swapped = new boolean[PrShaderConfig.BUFFERS];
    private final int[] formats = new int[PrShaderConfig.BUFFERS];
    /** Pixel size of each buffer: the screen's unless {@code size.buffer} says otherwise. */
    private final int[] bufferWidth = new int[PrShaderConfig.BUFFERS];
    private final int[] bufferHeight = new int[PrShaderConfig.BUFFERS];
    private int width;
    private int height;
    int depth0;
    int depth1;
    int depth2;
    int noise;
    int gbufferFbo;
    private int passFbo;
    private int copyFbo;
    /** Texture attached at each gbuffer output, to skip redundant attachment calls. */
    private final int[] gbufferAttached = new int[MAX_OUTPUTS];

    PrRenderTargets(PrShaderConfig config, boolean[] used) {
        this.config = config;
        for (int i = 0; i < PrShaderConfig.BUFFERS; i++) {
            // colortex0-7 always exist, as in OptiFine; higher buffers only when a program uses them.
            allocated[i] = i < 8 || used[i];
            formats[i] = config.formats[i] != 0 ? config.formats[i] : PrShaderConfig.RGBA8;
        }
    }

    int width() {
        return width;
    }

    int height() {
        return height;
    }

    boolean isAllocated(int buffer) {
        return allocated[buffer];
    }

    /** The texture a program reads for {@code buffer} now. */
    int read(int buffer) {
        return textures[buffer][swapped[buffer] ? 1 : 0];
    }

    /** The texture a composite pass writes for {@code buffer}. */
    int write(int buffer) {
        return textures[buffer][swapped[buffer] ? 0 : 1];
    }

    void swap(int buffer) {
        swapped[buffer] = !swapped[buffer];
    }

    /** (Re)creates everything for the window size; true when textures were recreated. */
    boolean ensure(int w, int h) {
        if (w == width && h == height && gbufferFbo != 0) {
            return false;
        }
        release();
        width = w;
        height = h;
        for (int i = 0; i < PrShaderConfig.BUFFERS; i++) {
            if (allocated[i]) {
                int[] size = config.bufferSize(i, w, h);
                bufferWidth[i] = size[0];
                bufferHeight[i] = size[1];
                textures[i][0] = colorTexture(formats[i], size[0], size[1]);
                textures[i][1] = colorTexture(formats[i], size[0], size[1]);
            }
        }
        depth0 = depthTexture(w, h);
        depth1 = depthTexture(w, h);
        depth2 = depthTexture(w, h);
        noise = noiseTexture(Math.max(1, config.noiseTextureResolution));
        gbufferFbo = GlStateManager.glGenFramebuffers();
        passFbo = GlStateManager.glGenFramebuffers();
        copyFbo = GlStateManager.glGenFramebuffers();
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, gbufferFbo);
        GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, depth0, 0);
        Arrays.fill(gbufferAttached, -1);
        return true;
    }

    /** Starts a frame: cleared buffers go back to their first texture and are cleared, then the depth. */
    void beginFrame(float[] fogColor) {
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, passFbo);
        for (int j = 1; j < MAX_OUTPUTS; j++) {
            GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0 + j, GL11.GL_TEXTURE_2D, 0, 0);
        }
        GL20.glDrawBuffers(new int[] {GL30.GL_COLOR_ATTACHMENT0});
        for (int i = 0; i < PrShaderConfig.BUFFERS; i++) {
            if (!allocated[i] || !config.clear[i]) {
                continue;
            }
            swapped[i] = false;
            float[] c = config.clearColors[i];
            if (c == null) {
                c = i == 0 ? fogColor : i == 1 ? new float[] {1, 1, 1, 1} : new float[] {0, 0, 0, 0};
            }
            GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, read(i), 0);
            GL30.glClearBufferfv(GL11.GL_COLOR, 0, c);
        }
        GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, 0, 0);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, gbufferFbo);
        GL30.glClearBufferfv(GL11.GL_DEPTH, 0, new float[] {1.0F});
    }

    /**
     * Binds the gbuffers for a program writing {@code drawBuffers}: fragment output j goes to
     * attachment j, which holds the current texture of buffer drawBuffers[j]. Only the buffers a
     * program writes are attached, so no GPU needs more than 8 attachments. Buffers with a size of
     * their own receive nothing from the world, as in OptiFine.
     */
    void bindGbuffers(int[] drawBuffers) {
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, gbufferFbo);
        int[] ids = new int[drawBuffers.length];
        for (int j = 0; j < MAX_OUTPUTS; j++) {
            int buffer = j < drawBuffers.length ? drawBuffers[j] : -1;
            int texture = buffer >= 0 && allocated[buffer] && config.sizes[buffer] == null ? read(buffer) : 0;
            if (gbufferAttached[j] != texture) {
                gbufferAttached[j] = texture;
                GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0 + j, GL11.GL_TEXTURE_2D, texture, 0);
            }
            if (j < ids.length) {
                ids[j] = texture == 0 ? GL11.GL_NONE : GL30.GL_COLOR_ATTACHMENT0 + j;
            }
        }
        GL20.glDrawBuffers(ids);
        GlStateManager._viewport(0, 0, width, height);
    }

    /**
     * Binds the pass framebuffer writing each listed buffer's other texture at output j, with the
     * viewport of the buffers' size. A pass mixing sizes draws at screen size without its sized
     * buffers, as in OptiFine.
     */
    void bindPass(int[] buffers) {
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, passFbo);
        boolean mixed = config.mixesSizes(buffers);
        int viewWidth = width;
        int viewHeight = height;
        int[] ids = new int[buffers.length];
        for (int j = 0; j < MAX_OUTPUTS; j++) {
            int buffer = j < buffers.length ? buffers[j] : -1;
            boolean drawn = buffer >= 0 && allocated[buffer] && !(mixed && config.sizes[buffer] != null);
            int texture = drawn ? write(buffer) : 0;
            if (drawn && !mixed) {
                viewWidth = bufferWidth[buffer];
                viewHeight = bufferHeight[buffer];
            }
            GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0 + j, GL11.GL_TEXTURE_2D, texture, 0);
            if (j < ids.length) {
                ids[j] = texture == 0 ? GL11.GL_NONE : GL30.GL_COLOR_ATTACHMENT0 + j;
            }
        }
        GL20.glDrawBuffers(ids);
        GlStateManager._viewport(0, 0, viewWidth, viewHeight);
    }

    /**
     * Builds the mipmaps of the buffers in {@code mask} (a bit per buffer) from what they hold
     * now, for a pass that samples them at lower levels. The texture keeps its mipmapped filter;
     * integer buffers cannot be filtered and are skipped.
     */
    void generateMipmaps(int mask) {
        if (mask == 0) {
            return;
        }
        // Unit 0: every pass binds its own samplers afterwards.
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        for (int i = 0; i < PrShaderConfig.BUFFERS; i++) {
            int format = pixelFormat(formats[i])[0];
            boolean integer = format == GL30.GL_RGBA_INTEGER || format == GL30.GL_RGB_INTEGER || format == GL30.GL_RG_INTEGER
                    || format == GL30.GL_RED_INTEGER;
            if ((mask & 1 << i) != 0 && allocated[i] && !integer) {
                GlStateManager._bindTexture(read(i));
                GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
                GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            }
        }
        GlStateManager._bindTexture(0);
    }

    /** Copies the world depth into {@code depthtex1} or {@code depthtex2}. */
    void copyDepth(int target) {
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, gbufferFbo);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copyFbo);
        GlStateManager._glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, target, 0);
        GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, gbufferFbo);
    }

    /** Copies {@code colortex0} into a framebuffer (the game's main target) when the pack has no final pass. */
    void blitColor0(int targetFbo, int targetWidth, int targetHeight) {
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, copyFbo);
        GlStateManager._glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, read(0), 0);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, targetFbo);
        GL30.glBlitFramebuffer(0, 0, bufferWidth[0], bufferHeight[0], 0, 0, targetWidth, targetHeight, GL11.GL_COLOR_BUFFER_BIT,
                GL11.GL_NEAREST);
        GlStateManager._glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, 0, 0);
    }

    void release() {
        for (int[] pair : textures) {
            for (int k = 0; k < 2; k++) {
                if (pair[k] != 0) {
                    GlStateManager._deleteTexture(pair[k]);
                    pair[k] = 0;
                }
            }
        }
        for (int t : new int[] {depth0, depth1, depth2, noise}) {
            if (t != 0) {
                GlStateManager._deleteTexture(t);
            }
        }
        depth0 = depth1 = depth2 = noise = 0;
        for (int fbo : new int[] {gbufferFbo, passFbo, copyFbo}) {
            if (fbo != 0) {
                GlStateManager._glDeleteFramebuffers(fbo);
            }
        }
        gbufferFbo = passFbo = copyFbo = 0;
        width = height = 0;
    }

    // ------------------------------------------------------------------ textures

    static int colorTexture(int internalFormat, int w, int h) {
        int id = GlStateManager._genTexture();
        GlStateManager._bindTexture(id);
        int[] ft = pixelFormat(internalFormat);
        GlStateManager._texImage2D(GL11.GL_TEXTURE_2D, 0, internalFormat, w, h, 0, ft[0], ft[1], null);
        boolean integer = ft[0] == GL30.GL_RGBA_INTEGER || ft[0] == GL30.GL_RGB_INTEGER || ft[0] == GL30.GL_RG_INTEGER || ft[0] == GL30.GL_RED_INTEGER;
        int filter = integer ? GL11.GL_NEAREST : GL11.GL_LINEAR;
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        return id;
    }

    static int depthTexture(int w, int h) {
        int id = GlStateManager._genTexture();
        GlStateManager._bindTexture(id);
        GlStateManager._texImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_DEPTH_COMPONENT32F, w, h, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, null);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE, GL11.GL_NONE);
        return id;
    }

    /** OptiFine's default noise texture: random RGB bytes, repeating. */
    static int noiseTexture(int size) {
        int id = GlStateManager._genTexture();
        GlStateManager._bindTexture(id);
        ByteBuffer data = MemoryUtil.memAlloc(size * size * 3);
        try {
            Random random = new Random(0);
            for (int i = 0; i < size * size * 3; i++) {
                data.put((byte) random.nextInt(256));
            }
            data.flip();
            GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 1);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB8, size, size, 0, GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, data);
            GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 4);
        } finally {
            MemoryUtil.memFree(data);
        }
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
        return id;
    }

    /** A pixel format and type compatible with {@code internalFormat}, for allocating storage. */
    static int[] pixelFormat(int internalFormat) {
        String name = PrShaderConfig.FORMATS.entrySet().stream().filter(e -> e.getValue() == internalFormat)
                .map(Map.Entry::getKey).findFirst().orElse("RGBA8");
        int channels = name.startsWith("RGBA") || name.equals("RGB10_A2") || name.equals("RGB5_A1") || name.equals("RGB10_A2UI")
                ? 4 : name.startsWith("RGB") || name.startsWith("R11F") || name.startsWith("R3_") ? 3 : name.startsWith("RG") ? 2 : 1;
        boolean integer = name.endsWith("I") && !name.endsWith("_SNORM") && !name.endsWith("F");
        boolean floating = name.contains("F") && !name.contains("_SNORM");
        int format = switch (channels) {
            case 4 -> integer ? GL30.GL_RGBA_INTEGER : GL11.GL_RGBA;
            case 3 -> integer ? GL30.GL_RGB_INTEGER : GL11.GL_RGB;
            case 2 -> integer ? GL30.GL_RG_INTEGER : GL30.GL_RG;
            default -> integer ? GL30.GL_RED_INTEGER : GL11.GL_RED;
        };
        int type = integer ? (name.contains("UI") ? GL11.GL_UNSIGNED_INT : GL11.GL_INT) : floating ? GL11.GL_FLOAT : GL11.GL_UNSIGNED_BYTE;
        return new int[] {format, type};
    }
}
