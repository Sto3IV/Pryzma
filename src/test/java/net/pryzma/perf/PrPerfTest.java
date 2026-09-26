package net.pryzma.perf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.TreeSet;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;

class PrPerfTest {
    /** The SectionRenderDispatcher constructor whose executor argument LevelRendererOptionsMixin replaces. */
    private static final String HOOKED_DISPATCHER_INIT = "(Lnet/minecraft/client/multiplayer/ClientLevel;"
            + "Lnet/minecraft/client/renderer/LevelRenderer;Ljava/util/concurrent/Executor;Lnet/minecraft/client/renderer/RenderBuffers;"
            + "Lnet/minecraft/client/renderer/block/BlockRenderDispatcher;"
            + "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher;)V";

    /** Camera at the origin looking down -Z, 90 degree vertical field of view. */
    private static Frustum frustum() {
        Frustum f = new Frustum(new Matrix4f(), new Matrix4f().perspective((float) Math.toRadians(90.0), 16.0F / 9.0F, 0.05F, 512.0F));
        f.prepare(0.0, 0.0, 0.0);
        return f;
    }

    @Test
    void partiallyInfiniteBoxIsCulledByTheUnpatchedTest() {
        // A column in front of the camera, infinite along Y only. NeoForge's isInfinite() is false for it,
        // and the side planes have a zero Y component: 0 * Infinity = NaN fails every plane comparison.
        AABB column = new AABB(-1.0, Double.NEGATIVE_INFINITY, -6.0, 1.0, Double.POSITIVE_INFINITY, -4.0);
        assertFalse(column.isInfinite());
        assertFalse(frustum().isVisible(column), "vanilla test turns the column into NaN and culls it");
        assertTrue(frustum().isVisible(PrFrustumCulling.clamped(column, 0.0, 0.0, 0.0)), "clamped column is in view");
    }

    @Test
    void clampingKeepsBoxesBehindTheCameraCulled() {
        AABB behind = new AABB(-1.0, Double.NEGATIVE_INFINITY, 4.0, 1.0, Double.POSITIVE_INFINITY, 6.0);
        assertFalse(frustum().isVisible(PrFrustumCulling.clamped(behind, 0.0, 0.0, 0.0)));
        AABB farAside = new AABB(5000.0, Double.NEGATIVE_INFINITY, -6.0, 5001.0, Double.POSITIVE_INFINITY, -4.0);
        assertFalse(frustum().isVisible(PrFrustumCulling.clamped(farAside, 0.0, 0.0, 0.0)));
    }

    @Test
    void finiteAndNaNBoundsAreClassified() {
        AABB finite = new AABB(0, 0, 0, 1, 1, 1);
        assertTrue(PrFrustumCulling.isFinite(finite));
        assertFalse(PrFrustumCulling.hasNaN(finite));
        AABB nan = new AABB(0, Double.NaN, 0, 1, 1, 1);
        assertFalse(PrFrustumCulling.isFinite(nan));
        assertTrue(PrFrustumCulling.hasNaN(nan));
        assertEquals(100.0 + PrFrustumCulling.EXTENT, PrFrustumCulling.clamp(Double.POSITIVE_INFINITY, 100.0));
        assertEquals(-PrFrustumCulling.EXTENT, PrFrustumCulling.clamp(Double.NEGATIVE_INFINITY, 0.0));
        assertEquals(3.5, PrFrustumCulling.clamp(3.5, 0.0));
    }

    @Test
    void smoothWorldPacesOnlyBeyondTheRadius() {
        for (int tick = 0; tick < 16; tick++) {
            assertFalse(PrSmoothWorld.skipsTick(64.0, -64.0, tick, 7), "at the radius the mob thinks every tick");
            assertFalse(PrSmoothWorld.skipsTick(10.0, 3.0, tick, 7));
        }
        int ran = 0;
        for (int tick = 0; tick < 400; tick++) {
            if (!PrSmoothWorld.skipsTick(64.5, 0.0, tick, 7)) {
                ran++;
            }
        }
        assertEquals(400 / PrSmoothWorld.INTERVAL, ran, "one AI step in INTERVAL beyond the radius");
    }

    /** LevelRendererOptionsMixin swaps the executor of every SectionRenderDispatcher LevelRenderer builds. */
    @Test
    void chunkWorkerHookCoversEveryDispatcherConstruction() throws IOException {
        ClassNode levelRenderer = new ClassNode();
        try (InputStream in = LevelRenderer.class.getResourceAsStream("LevelRenderer.class")) {
            new ClassReader(in).accept(levelRenderer, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        Set<String> constructing = new TreeSet<>();
        for (MethodNode method : levelRenderer.methods) {
            for (AbstractInsnNode insn : method.instructions) {
                if (insn instanceof MethodInsnNode call && call.name.equals("<init>")
                        && call.owner.equals("net/minecraft/client/renderer/chunk/SectionRenderDispatcher")) {
                    assertEquals(HOOKED_DISPATCHER_INIT, call.desc, method.name);
                    constructing.add(method.name);
                }
            }
        }
        assertTrue(constructing.contains("allChanged"), "no dispatcher built in allChanged");
        assertTrue(Set.of("allChanged", "setLevel").containsAll(constructing), "unhooked dispatcher construction in " + constructing);
    }

    @Test
    void smoothWorldStaggersMobsById() {
        // Four mobs with consecutive ids never all think on the same tick: the load is spread.
        for (int tick = 0; tick < 8; tick++) {
            int thinking = 0;
            for (int id = 0; id < PrSmoothWorld.INTERVAL; id++) {
                if (!PrSmoothWorld.skipsTick(0.0, 100.0, tick, id)) {
                    thinking++;
                }
            }
            assertEquals(1, thinking);
        }
        assertFalse(PrSmoothWorld.skipsTick(100.0, 0.0, 3, -3), "negative sums stay in range (floorMod)");
    }
}
