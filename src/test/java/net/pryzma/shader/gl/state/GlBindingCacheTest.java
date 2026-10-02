package net.pryzma.shader.gl.state;

import static net.pryzma.shader.gl.state.GlBindingCache.GL_DRAW_FRAMEBUFFER;
import static net.pryzma.shader.gl.state.GlBindingCache.GL_FRAMEBUFFER;
import static net.pryzma.shader.gl.state.GlBindingCache.GL_READ_FRAMEBUFFER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GlBindingCacheTest {
    @Test
    void firstBindAlwaysReachesGl() {
        GlBindingCache cache = new GlBindingCache();
        assertTrue(cache.bindFramebuffer(GL_FRAMEBUFFER, 0), "the default framebuffer is not known to be bound yet");
        assertTrue(cache.useProgram(0), "no program is known to be in use yet");
    }

    /** PryzmaShaders compared the cached name with the target enum, so this second bind was never skipped. */
    @Test
    void repeatedFramebufferBindIsSkipped() {
        GlBindingCache cache = new GlBindingCache();
        assertTrue(cache.bindFramebuffer(GL_FRAMEBUFFER, 5));
        assertFalse(cache.bindFramebuffer(GL_FRAMEBUFFER, 5));
        assertTrue(cache.bindFramebuffer(GL_FRAMEBUFFER, 7));
        assertFalse(cache.bindFramebuffer(GL_FRAMEBUFFER, 7));
    }

    @Test
    void framebufferTargetSetsBothDrawAndRead() {
        GlBindingCache cache = new GlBindingCache();
        cache.bindFramebuffer(GL_FRAMEBUFFER, 3);
        assertFalse(cache.bindFramebuffer(GL_DRAW_FRAMEBUFFER, 3));
        assertFalse(cache.bindFramebuffer(GL_READ_FRAMEBUFFER, 3));
        assertEquals(3, cache.drawFramebuffer());
        assertEquals(3, cache.readFramebuffer());
    }

    @Test
    void splitDrawAndReadBindingsAreTrackedSeparately() {
        GlBindingCache cache = new GlBindingCache();
        assertTrue(cache.bindFramebuffer(GL_READ_FRAMEBUFFER, 4));
        assertTrue(cache.bindFramebuffer(GL_DRAW_FRAMEBUFFER, 9));
        assertFalse(cache.bindFramebuffer(GL_READ_FRAMEBUFFER, 4));
        // GL_FRAMEBUFFER 9 still changes the read binding.
        assertTrue(cache.bindFramebuffer(GL_FRAMEBUFFER, 9));
        assertFalse(cache.bindFramebuffer(GL_READ_FRAMEBUFFER, 9));
    }

    @Test
    void deletingTheBoundFramebufferRevertsToDefault() {
        GlBindingCache cache = new GlBindingCache();
        cache.bindFramebuffer(GL_FRAMEBUFFER, 6);
        cache.deleteFramebuffer(6);
        assertEquals(0, cache.drawFramebuffer());
        assertFalse(cache.bindFramebuffer(GL_FRAMEBUFFER, 0), "GL already reverted to framebuffer 0");
        // A recycled name is a new framebuffer and must be bound.
        assertTrue(cache.bindFramebuffer(GL_FRAMEBUFFER, 6));
    }

    @Test
    void deletingAnotherFramebufferKeepsTheBinding() {
        GlBindingCache cache = new GlBindingCache();
        cache.bindFramebuffer(GL_FRAMEBUFFER, 6);
        cache.deleteFramebuffer(8);
        assertFalse(cache.bindFramebuffer(GL_FRAMEBUFFER, 6));
    }

    @Test
    void unknownTargetForgetsFramebufferBindings() {
        GlBindingCache cache = new GlBindingCache();
        cache.bindFramebuffer(GL_FRAMEBUFFER, 2);
        assertTrue(cache.bindFramebuffer(0x1234, 2));
        assertTrue(cache.bindFramebuffer(GL_FRAMEBUFFER, 2));
    }

    @Test
    void repeatedProgramBindIsSkipped() {
        GlBindingCache cache = new GlBindingCache();
        assertTrue(cache.useProgram(11));
        assertFalse(cache.useProgram(11));
        assertTrue(cache.useProgram(0));
        assertFalse(cache.useProgram(0));
        assertTrue(cache.useProgram(11));
    }

    @Test
    void deletedProgramNameIsBoundAgain() {
        GlBindingCache cache = new GlBindingCache();
        cache.useProgram(12);
        cache.deleteProgram(12);
        assertEquals(GlBindingCache.UNKNOWN, cache.program());
        assertTrue(cache.useProgram(12), "a recycled program name must reach GL");
    }

    @Test
    void invalidateForgetsEverything() {
        GlBindingCache cache = new GlBindingCache();
        cache.bindFramebuffer(GL_FRAMEBUFFER, 1);
        cache.useProgram(1);
        cache.invalidate();
        assertTrue(cache.bindFramebuffer(GL_FRAMEBUFFER, 1));
        assertTrue(cache.useProgram(1));
    }

    @Test
    void targetConstantsMatchGl() {
        assertEquals(org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER, GL_FRAMEBUFFER);
        assertEquals(org.lwjgl.opengl.GL30C.GL_READ_FRAMEBUFFER, GL_READ_FRAMEBUFFER);
        assertEquals(org.lwjgl.opengl.GL30C.GL_DRAW_FRAMEBUFFER, GL_DRAW_FRAMEBUFFER);
    }
}
