package net.pryzma.perf;

import java.util.concurrent.atomic.AtomicInteger;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import net.minecraft.client.Minecraft;

/**
 * Figures of the F3 screen and Quick Info that vanilla does not keep: section builds and the
 * slowest frame of each second, and the GPU memory held by buffers and textures.
 *
 * <p>A second is Minecraft's own FPS window: it closes when {@code Minecraft.fpsString} is
 * replaced, so both figures describe the same frames as the FPS shown beside them. Everything but
 * {@link #onSectionCompiled} runs on the render thread.
 */
public final class PrDebugTracker {
    private static final AtomicInteger CHUNK_UPDATES_COUNTER = new AtomicInteger();
    /** Buffer target to the buffer last bound to it through GlStateManager. */
    private static final Int2IntOpenHashMap BOUND_BUFFERS = new Int2IntOpenHashMap();
    private static final Int2LongOpenHashMap BUFFER_BYTES = new Int2LongOpenHashMap();
    private static final Int2LongOpenHashMap TEXTURE_BYTES = new Int2LongOpenHashMap();

    private static String window;
    private static long lastFrameNs;
    private static long worstFrameNs;
    private static int chunkUpdates;
    private static int fpsMin;
    private static long bufferBytes;
    private static long textureBytes;

    private PrDebugTracker() {
    }

    /** A section finished meshing. Chunk worker threads. */
    public static void onSectionCompiled() {
        CHUNK_UPDATES_COUNTER.incrementAndGet();
    }

    /** Once per frame, before it is drawn. */
    public static void tick() {
        tick(System.nanoTime(), Minecraft.getInstance().fpsString);
    }

    /**
     * {@code fpsWindow} is replaced by Minecraft once per second, so a new instance closes the
     * window. The frame measured here ended before that replacement and belongs to the old window,
     * where Minecraft counted it.
     */
    static void tick(long now, String fpsWindow) {
        if (lastFrameNs != 0L) {
            worstFrameNs = Math.max(worstFrameNs, now - lastFrameNs);
        }
        lastFrameNs = now;
        if (fpsWindow != window) { // identity on purpose: equal texts are still distinct seconds
            window = fpsWindow;
            chunkUpdates = CHUNK_UPDATES_COUNTER.getAndSet(0);
            fpsMin = worstFrameNs > 0L ? (int) (1_000_000_000L / worstFrameNs) : 0;
            worstFrameNs = 0L;
        }
    }

    /** Sections meshed during the last second. */
    public static int getChunkUpdates() {
        return chunkUpdates;
    }

    /** The frame rate of the slowest frame of the last second. */
    public static int getFpsMin() {
        return fpsMin;
    }

    // ---------------------------------------------------------------- GPU memory

    public static void onBindBuffer(int target, int buffer) {
        BOUND_BUFFERS.put(target, buffer);
    }

    /** The buffer bound to {@code target} now has {@code bytes} of storage. */
    public static void onBufferData(int target, long bytes) {
        int buffer = BOUND_BUFFERS.get(target);
        if (buffer > 0) {
            bufferBytes += bytes - BUFFER_BYTES.put(buffer, bytes);
        }
    }

    public static void onBufferDeleted(int buffer) {
        bufferBytes -= BUFFER_BYTES.remove(buffer);
    }

    /** Storage for one level of a 2D texture: level 0 respecifies the texture, other levels add to it. */
    public static void onTexImage(int texture, int level, int internalFormat, int width, int height) {
        if (texture <= 0) {
            return;
        }
        long bytes = (long) width * height * bytesPerPixel(internalFormat);
        if (level == 0) {
            textureBytes += bytes - TEXTURE_BYTES.put(texture, bytes);
        } else {
            TEXTURE_BYTES.addTo(texture, bytes);
            textureBytes += bytes;
        }
    }

    public static void onTextureDeleted(int texture) {
        textureBytes -= TEXTURE_BYTES.remove(texture);
    }

    public static long getGpuBufferBytes() {
        return bufferBytes;
    }

    public static long getGpuTextureBytes() {
        return textureBytes;
    }

    /** Bytes per texel of a GL internal format, three-component formats padded as drivers store them. */
    static int bytesPerPixel(int internalFormat) {
        return switch (internalFormat) {
            case GL11.GL_RED, GL30.GL_R8 -> 1;
            case GL30.GL_RG, GL30.GL_RG8, GL30.GL_R16, GL30.GL_R16F -> 2;
            case GL11.GL_RGB16, GL11.GL_RGBA16, GL30.GL_RGB16F, GL30.GL_RGBA16F, GL30.GL_RG32F, GL30.GL_DEPTH32F_STENCIL8 -> 8;
            case GL30.GL_RGB32F, GL30.GL_RGBA32F, GL30.GL_RGBA32UI, GL30.GL_RGBA32I -> 16;
            default -> 4;
        };
    }
}
