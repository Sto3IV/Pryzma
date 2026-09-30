package net.pryzma.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * DebugScreenOverlayMixin against the NeoForge-patched DebugScreenOverlay on the classpath. The mixin
 * config sets defaultRequire 0, so a renamed lambda or a moved call would only show up as a slower F3.
 */
class DebugScreenOverlayMixinTargetsTest {
    private static final String OVERLAY = "net/minecraft/client/gui/components/DebugScreenOverlay";
    private static final String MIXIN = "net/pryzma/mixin/DebugScreenOverlayMixin";
    private static final String TEXT_LAMBDA = "lambda$render$2";
    private static final String INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;";
    private static final String WRAP_OPERATION = "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;";

    /** render raycasts twice, then hands the text lambda to drawManaged. */
    @Test
    void renderRaycastsThenDrawsThroughTheTextLambda() throws IOException {
        List<String> calls = new ArrayList<>();
        String lambda = null;
        for (AbstractInsnNode insn : method(read(OVERLAY), "render", "(Lnet/minecraft/client/gui/GuiGraphics;)V").instructions) {
            if (insn instanceof MethodInsnNode call) {
                calls.add(call.name);
            } else if (insn instanceof InvokeDynamicInsnNode indy && indy.bsmArgs.length > 1 && indy.bsmArgs[1] instanceof Handle impl) {
                lambda = impl.getName();
            }
        }
        assertEquals(TEXT_LAMBDA, lambda);
        assertEquals(2, calls.stream().filter("pick"::equals).count());
        assertTrue(calls.indexOf("pick") < calls.indexOf("drawManaged"), calls.toString());
        assertFalse(calls.contains("drawGameInformation") || calls.contains("drawSystemInformation"), calls.toString());
    }

    /** The lambda collects both lists, posts DebugText and draws both columns; the charts come after them. */
    @Test
    void textLambdaDrawsBothColumnsBeforeTheCharts() throws IOException {
        List<String> calls = new ArrayList<>();
        for (AbstractInsnNode insn : method(read(OVERLAY), TEXT_LAMBDA, "(Lnet/minecraft/client/gui/GuiGraphics;)V").instructions) {
            if (insn instanceof MethodInsnNode call) {
                calls.add(call.name);
            }
        }
        int game = calls.indexOf("collectGameInformationText");
        int system = calls.indexOf("collectSystemInformationText");
        int post = calls.indexOf("post");
        int right = calls.lastIndexOf("renderLines");
        assertTrue(game >= 0 && game < system && system < post && post < calls.indexOf("renderLines"), calls.toString());
        assertEquals(2, calls.stream().filter("renderLines"::equals).count());
        assertTrue(right < calls.indexOf("drawChart"), "charts must draw after the cached text: " + calls);
    }

    /** Every injector of the mixin names a method of the patched class and, for INVOKE, a call inside it. */
    @Test
    void everyInjectorTargetExists() throws IOException {
        ClassNode overlay = read(OVERLAY);
        int checked = 0;
        int calls = 0;
        for (MethodNode handler : read(MIXIN).methods) {
            List<AnnotationNode> annotations = new ArrayList<>();
            if (handler.visibleAnnotations != null) {
                annotations.addAll(handler.visibleAnnotations);
            }
            if (handler.invisibleAnnotations != null) {
                annotations.addAll(handler.invisibleAnnotations);
            }
            for (AnnotationNode annotation : annotations) {
                if (!annotation.desc.equals(INJECT) && !annotation.desc.equals(WRAP_OPERATION)) {
                    continue;
                }
                List<String> selectors = value(annotation, "method");
                List<AnnotationNode> ats = value(annotation, "at");
                for (String selector : selectors) {
                    int paren = selector.indexOf('(');
                    String name = paren < 0 ? selector : selector.substring(0, paren);
                    String desc = paren < 0 ? null : selector.substring(paren);
                    List<MethodNode> targets = overlay.methods.stream()
                            .filter(m -> m.name.equals(name) && (desc == null || m.desc.equals(desc))).toList();
                    assertFalse(targets.isEmpty(), handler.name + ": no method " + selector);
                    for (AnnotationNode at : ats) {
                        if ("INVOKE".equals(value(at, "value"))) {
                            String target = value(at, "target");
                            assertTrue(targets.stream().anyMatch(m -> invokes(m, target)), handler.name + ": " + selector + " calls no " + target);
                            calls++;
                        }
                    }
                    checked++;
                }
            }
        }
        // prBeginFrame, prPick, 3 lambda wraps + prRenderLines, 5 invalidations, the two PrDebugOverlay hooks.
        assertEquals(13, checked);
        assertEquals(5, calls);
    }

    private static boolean invokes(MethodNode method, String target) {
        int semicolon = target.indexOf(';');
        int paren = target.indexOf('(');
        String owner = target.substring(1, semicolon);
        String name = target.substring(semicolon + 1, paren);
        String desc = target.substring(paren);
        for (AbstractInsnNode insn : method.instructions) {
            if (insn instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(desc)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static <T> T value(AnnotationNode annotation, String key) {
        for (int i = 0; i < annotation.values.size(); i += 2) {
            if (annotation.values.get(i).equals(key)) {
                return (T) annotation.values.get(i + 1);
            }
        }
        return null;
    }

    private static MethodNode method(ClassNode owner, String name, String desc) {
        for (MethodNode m : owner.methods) {
            if (m.name.equals(name) && m.desc.equals(desc)) {
                return m;
            }
        }
        throw new AssertionError("no " + name + desc + " in " + owner.name);
    }

    private static ClassNode read(String internalName) throws IOException {
        try (InputStream in = DebugScreenOverlayMixinTargetsTest.class.getClassLoader().getResourceAsStream(internalName + ".class")) {
            assertNotNull(in, internalName + " is not on the test classpath");
            ClassNode node = new ClassNode();
            new ClassReader(in).accept(node, ClassReader.SKIP_FRAMES);
            return node;
        }
    }
}
