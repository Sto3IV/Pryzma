package net.pryzma.perf;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;

import com.mojang.blaze3d.platform.GlStateManager;

class PrDebugTrackerTest {
    @Test
    void fpsMinIsTheSlowestFrameOfMinecraftsSecond() {
        // Minecraft replaces fpsString each second; equal text in a new instance is a new second.
        String first = new String("60 fps"), second = new String("60 fps"), third = new String("60 fps");
        long t = 1L << 40;
        PrDebugTracker.tick(t, first); // opens a clean window whatever ran before
        PrDebugTracker.tick(t += 4_000_000L, first);
        PrDebugTracker.tick(t += 50_000_000L, first);
        PrDebugTracker.tick(t += 5_000_000L, first);
        PrDebugTracker.tick(t += 8_000_000L, second); // this frame still belongs to the first second
        assertEquals(20, PrDebugTracker.getFpsMin());
        PrDebugTracker.tick(t += 10_000_000L, second);
        PrDebugTracker.tick(t += 10_000_000L, third);
        assertEquals(100, PrDebugTracker.getFpsMin());
    }

    @Test
    void chunkUpdatesAreCountedPerSecond() {
        String first = new String("fps"), second = new String("fps"), third = new String("fps");
        long t = 1L << 41;
        PrDebugTracker.tick(t, first);
        for (int i = 0; i < 35; i++) {
            PrDebugTracker.onSectionCompiled();
        }
        PrDebugTracker.tick(t + 1_000L, first);
        PrDebugTracker.tick(t + 2_000L, second);
        assertEquals(35, PrDebugTracker.getChunkUpdates());
        PrDebugTracker.tick(t + 3_000L, third);
        assertEquals(0, PrDebugTracker.getChunkUpdates());
    }

    @Test
    void bufferMemoryFollowsTheBoundBuffer() {
        long base = PrDebugTracker.getGpuBufferBytes();
        PrDebugTracker.onBindBuffer(GL15.GL_ARRAY_BUFFER, 900_001);
        PrDebugTracker.onBufferData(GL15.GL_ARRAY_BUFFER, 4096);
        PrDebugTracker.onBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 900_002);
        PrDebugTracker.onBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, 1024);
        assertEquals(base + 5120, PrDebugTracker.getGpuBufferBytes());
        PrDebugTracker.onBufferData(GL15.GL_ARRAY_BUFFER, 1000);
        assertEquals(base + 2024, PrDebugTracker.getGpuBufferBytes(), "new storage replaces the old");
        PrDebugTracker.onBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        PrDebugTracker.onBufferData(GL15.GL_ARRAY_BUFFER, 1 << 20);
        assertEquals(base + 2024, PrDebugTracker.getGpuBufferBytes(), "no buffer bound, nothing allocated");
        PrDebugTracker.onBufferDeleted(900_001);
        PrDebugTracker.onBufferDeleted(900_002);
        PrDebugTracker.onBufferDeleted(900_002);
        assertEquals(base, PrDebugTracker.getGpuBufferBytes());
    }

    @Test
    void textureMemoryCountsEveryLevelUntilDeleted() {
        long base = PrDebugTracker.getGpuTextureBytes();
        for (int level = 0; level <= 2; level++) {
            PrDebugTracker.onTexImage(900_003, level, GL11.GL_RGBA, 1024 >> level, 512 >> level);
        }
        assertEquals(base + 4L * (1024 * 512 + 512 * 256 + 256 * 128), PrDebugTracker.getGpuTextureBytes());
        PrDebugTracker.onTexImage(900_003, 0, GL30.GL_RGBA16F, 16, 16);
        assertEquals(base + 8L * 16 * 16, PrDebugTracker.getGpuTextureBytes(), "level 0 respecifies the texture");
        PrDebugTracker.onTexImage(0, 0, GL11.GL_RGBA, 64, 64);
        PrDebugTracker.onTextureDeleted(900_003);
        assertEquals(base, PrDebugTracker.getGpuTextureBytes());
    }

    @Test
    void texelSizes() {
        assertEquals(1, PrDebugTracker.bytesPerPixel(GL11.GL_RED));
        assertEquals(2, PrDebugTracker.bytesPerPixel(GL30.GL_RG));
        assertEquals(4, PrDebugTracker.bytesPerPixel(GL11.GL_RGBA));
        assertEquals(4, PrDebugTracker.bytesPerPixel(GL30.GL_DEPTH_COMPONENT32F));
        assertEquals(8, PrDebugTracker.bytesPerPixel(GL30.GL_RGBA16F));
        assertEquals(16, PrDebugTracker.bytesPerPixel(GL30.GL_RGBA32F));
    }

    /** GlStateManagerMemoryMixin: injection failures are silent at runtime (defaultRequire 0). */
    @Test
    void hookedGlStateManagerMethodsExist() throws NoSuchMethodException {
        Class<?> gl = GlStateManager.class;
        gl.getDeclaredMethod("_glBindBuffer", int.class, int.class);
        gl.getDeclaredMethod("_glBufferData", int.class, ByteBuffer.class, int.class);
        gl.getDeclaredMethod("_glBufferData", int.class, long.class, int.class);
        gl.getDeclaredMethod("_glDeleteBuffers", int.class);
        gl.getDeclaredMethod("_texImage2D", int.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class,
                IntBuffer.class);
        gl.getDeclaredMethod("_deleteTexture", int.class);
        gl.getDeclaredMethod("_deleteTextures", int[].class);
    }
}
