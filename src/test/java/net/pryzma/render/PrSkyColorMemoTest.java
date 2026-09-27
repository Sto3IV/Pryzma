package net.pryzma.render;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import net.minecraft.world.phys.Vec3;

class PrSkyColorMemoTest {
    private static final Vec3 A = new Vec3(0.1, 0.2, 0.3);
    private static final Vec3 B = new Vec3(0.4, 0.5, 0.6);
    private static final Vec3 C = new Vec3(0.7, 0.8, 0.9);

    @Test
    void emptyMemoMisses() {
        assertNull(new PrSkyColorMemo().get(0, 0, 0, 0.5F, 10, 10));
    }

    @Test
    void everyInputIsPartOfTheKey() {
        PrSkyColorMemo memo = new PrSkyColorMemo();
        memo.put(1, 2, 3, 0.5F, 100, 200, A);
        assertSame(A, memo.get(1, 2, 3, 0.5F, 100, 200));
        assertNull(memo.get(1.5, 2, 3, 0.5F, 100, 200));
        assertNull(memo.get(1, 2.5, 3, 0.5F, 100, 200));
        assertNull(memo.get(1, 2, 3.5, 0.5F, 100, 200));
        assertNull(memo.get(1, 2, 3, 0.25F, 100, 200), "partial tick");
        assertNull(memo.get(1, 2, 3, 0.5F, 101, 200), "game time: rain, thunder and lightning flash advance per tick");
        assertNull(memo.get(1, 2, 3, 0.5F, 100, 201), "day time");
    }

    /** Sky and fog sample at the camera, the shader pack uniforms at the entity's feet: both must stay cached. */
    @Test
    void twoAlternatingPositionsBothHit() {
        PrSkyColorMemo memo = new PrSkyColorMemo();
        memo.put(0, 65.62, 0, 0.5F, 7, 7, A);
        memo.put(0, 64.0, 0, 0.5F, 7, 7, B);
        for (int i = 0; i < 3; i++) {
            assertSame(A, memo.get(0, 65.62, 0, 0.5F, 7, 7));
            assertSame(B, memo.get(0, 64.0, 0, 0.5F, 7, 7));
        }
    }

    @Test
    void leastRecentlyUsedEntryIsEvicted() {
        PrSkyColorMemo memo = new PrSkyColorMemo();
        memo.put(1, 0, 0, 0F, 0, 0, A);
        memo.put(2, 0, 0, 0F, 0, 0, B);
        memo.get(1, 0, 0, 0F, 0, 0);
        memo.put(3, 0, 0, 0F, 0, 0, C);
        assertNull(memo.get(2, 0, 0, 0F, 0, 0));
        assertSame(A, memo.get(1, 0, 0, 0F, 0, 0));
        assertSame(C, memo.get(3, 0, 0, 0F, 0, 0));
    }

    @Test
    void clearForgetsBothEntries() {
        PrSkyColorMemo memo = new PrSkyColorMemo();
        memo.put(1, 0, 0, 0F, 0, 0, A);
        memo.put(2, 0, 0, 0F, 0, 0, B);
        memo.clear();
        assertNull(memo.get(1, 0, 0, 0F, 0, 0));
        assertNull(memo.get(2, 0, 0, 0F, 0, 0));
    }
}
