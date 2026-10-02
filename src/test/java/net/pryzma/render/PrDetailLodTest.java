package net.pryzma.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import net.minecraft.core.BlockPos;
import net.pryzma.detail.PrDeferredDetailQueue;

/**
 * Phase B4 decorator LOD. Plain JUnit cannot load RenderType or Blocks, so the classifier and the
 * replay are gated in game (runSelftest); these tests pin the pure parts and the bytecode contracts
 * whose silent drift broke B4 before: the replay point, the captured locals and the PryzmaShaders block context.
 */
class PrDetailLodTest {
    private static final String SECTION_COMPILER = "net/minecraft/client/renderer/chunk/SectionCompiler";
    private static final String COMPILER_MIXIN = "net/pryzma/mixin/SectionCompilerRegionMixin";
    private static final String SHADER_COMPILER_MIXIN = "net/pryzma/shader/mixin/vertices/block_rendering/MixinSectionCompiler";
    private static final String BLOCK_CONTEXT = "net/pryzma/shader/vertices/BlockContext";
    private static final String LOCAL = "Lcom/llamalad7/mixinextras/sugar/Local;";

    @Test
    void farRangesDrawTheirCoreOnly() {
        VboRange range = new VboRange();
        range.setSize(400);
        assertEquals(-1, range.getCoreSize(), "a range without a split is all core");
        assertEquals(400, VboRegion.drawSize(range, true));
        range.setCoreSize(240);
        assertEquals(400, VboRegion.drawSize(range, false), "near: core and decorators");
        assertEquals(240, VboRegion.drawSize(range, true), "far: core only");
        range.setCoreSize(0);
        assertEquals(0, VboRegion.drawSize(range, true), "far and decorators only: no draw command");
    }

    @Test
    void queueKeepsOneCorePerDeferredLayerAndForgetsEverything() {
        PrDeferredDetailQueue queue = PrDeferredDetailQueue.get();
        queue.clear();
        queue.add(null, new BlockPos(17, 64, -3), null, null);
        queue.add(null, BlockPos.ZERO, null, null);
        assertEquals(2, queue.size());
        assertEquals(1, queue.layerCount(), "one layer, however many blocks");
        assertEquals(0, queue.core(0), "a layer opened by its decorators has an empty core");
        assertEquals(new BlockPos(17, 64, -3), queue.get(0).pos, "the position is copied, not aliased");
        queue.setCore(0, 96);
        assertEquals(96, queue.core(0));
        queue.clear();
        assertEquals(0, queue.size());
        assertEquals(0, queue.layerCount());
        assertEquals(null, queue.get(0).state);
    }

    /** The replay point: after every block, fluid and additional renderer has written its layer, before any is built. */
    @Test
    void compileReplaysAfterEveryWriterAndBeforeTheFirstBuild() throws IOException {
        MethodNode compile = method(read(SECTION_COMPILER), "compile", "Ljava/util/List;)");
        int lastBlock = -1, additional = -1, entrySet = -1, firstBuild = -1, entrySets = 0, i = 0;
        for (AbstractInsnNode insn : compile.instructions) {
            if (insn instanceof MethodInsnNode call) {
                if (call.name.equals("renderBatched") || call.name.equals("renderLiquid")) {
                    lastBlock = i;
                } else if (call.name.equals("addAdditionalGeometry")) {
                    additional = i;
                } else if (call.owner.equals("java/util/Map") && call.name.equals("entrySet")) {
                    entrySet = i;
                    entrySets++;
                } else if (call.name.equals("build") && firstBuild < 0) {
                    firstBuild = i;
                }
            }
            i++;
        }
        assertEquals(1, entrySets, "the @At(Map.entrySet) injection must match exactly one call");
        assertTrue(lastBlock >= 0 && lastBlock < additional && additional < entrySet && entrySet < firstBuild,
                "block loop < additional renderers < entrySet < build: " + lastBlock + " " + additional + " " + entrySet + " " + firstBuild);
    }

    /** S1: a @Local RandomSource at Map.entrySet fails to resolve, which production's defaultRequire 0 turned into lost decorators. */
    @Test
    void replayCapturesNoLocalButTheLayerMap() throws IOException {
        List<String> captured = new ArrayList<>();
        for (MethodNode m : read(COMPILER_MIXIN).methods) {
            Type[] params = Type.getArgumentTypes(m.desc);
            for (int p = 0; p < params.length; p++) {
                if (annotated(m.visibleParameterAnnotations, p) || annotated(m.invisibleParameterAnnotations, p)) {
                    captured.add(params[p].getInternalName());
                }
            }
        }
        assertEquals(List.of("java/util/Map"), captured);
    }

    /** S2: the replay must carry the same mc_Entity / at_midBlock as PryzmaShaders' in-order path, so both go through one helper. */
    @Test
    void replayAndPryzmaUseOneBlockContext() throws IOException {
        assertTrue(calls(read(COMPILER_MIXIN), null, BLOCK_CONTEXT, "begin"), "the replay begins PryzmaShaders' block context");
        assertTrue(!calls(read(COMPILER_MIXIN), null, "net/pryzma/shader/vertices/BlockSensitiveBufferBuilder", "beginBlock"),
                "the replay must not hand-roll beginBlock");
        assertTrue(calls(read(SHADER_COMPILER_MIXIN), "pryzma$blockContext", BLOCK_CONTEXT, "begin"), "PryzmaShaders' wrapper uses the same helper");
    }

    @Test
    void vertexBufferDrawHasOneDrawElements() throws IOException {
        int count = 0;
        for (AbstractInsnNode insn : method(read("com/mojang/blaze3d/vertex/VertexBuffer"), "draw", "()V").instructions) {
            if (insn instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESTATIC
                    && call.owner.equals("com/mojang/blaze3d/systems/RenderSystem") && call.name.equals("drawElements")) {
                count++;
            }
        }
        assertEquals(1, count, "@ModifyArg(drawElements, index 1) truncates the one draw");
    }

    private static boolean annotated(List<AnnotationNode>[] annotations, int parameter) {
        if (annotations == null || parameter >= annotations.length || annotations[parameter] == null) {
            return false;
        }
        for (AnnotationNode a : annotations[parameter]) {
            if (a.desc.equals(LOCAL)) {
                return true;
            }
        }
        return false;
    }

    private static boolean calls(ClassNode node, String methodName, String owner, String name) {
        for (MethodNode m : node.methods) {
            if (methodName != null && !m.name.equals(methodName)) {
                continue;
            }
            for (AbstractInsnNode insn : m.instructions) {
                if (insn instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static MethodNode method(ClassNode node, String name, String descPart) {
        for (MethodNode m : node.methods) {
            if (m.name.equals(name) && m.desc.contains(descPart)) {
                return m;
            }
        }
        throw new AssertionError(node.name + "." + name + " not found");
    }

    private static ClassNode read(String internalName) throws IOException {
        try (InputStream in = PrDetailLodTest.class.getClassLoader().getResourceAsStream(internalName + ".class")) {
            assertNotNull(in, internalName + " is not on the test classpath");
            ClassNode node = new ClassNode();
            new ClassReader(in).accept(node, ClassReader.SKIP_FRAMES);
            return node;
        }
    }
}
