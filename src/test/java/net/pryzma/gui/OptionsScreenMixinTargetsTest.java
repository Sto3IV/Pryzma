package net.pryzma.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

/**
 * OptionsScreenMixin against the NeoForge-patched OptionsScreen on the classpath. The mixin config sets
 * defaultRequire 0, so a moved call would only show up as Telemetry Data and Credits back in the menu.
 */
class OptionsScreenMixinTargetsTest {
    private static final String SCREEN = "net/minecraft/client/gui/screens/options/OptionsScreen";
    private static final String MIXIN = "net/pryzma/mixin/OptionsScreenMixin";
    private static final String COMPONENT = "Lnet/minecraft/network/chat/Component;";
    private static final String ROW_HELPER = "Lnet/minecraft/client/gui/layouts/GridLayout$RowHelper;";
    private static final String LAYOUT_ELEMENT = "Lnet/minecraft/client/gui/layouts/LayoutElement;";
    private static final String ROW_ADD = ROW_HELPER + "addChild(" + LAYOUT_ELEMENT + ")" + LAYOUT_ELEMENT;
    private static final String WRAP_OPERATION = "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;";
    private static final String HANDLER = "(" + ROW_HELPER + LAYOUT_ELEMENT
            + "Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;)" + LAYOUT_ELEMENT;

    /** One injector: init()V, INVOKE RowHelper.addChild, handler (receiver, child, Operation) returning the call's type. */
    @Test
    void injectorWrapsTheRowHelperAddInInit() throws IOException {
        List<String> injectors = new ArrayList<>();
        for (MethodNode handler : read(MIXIN).methods) {
            for (AnnotationNode annotation : annotations(handler)) {
                if (annotation.desc.equals(WRAP_OPERATION)) {
                    List<AnnotationNode> ats = value(annotation, "at");
                    assertEquals(1, ats.size(), handler.name);
                    injectors.add(value(annotation, "method") + " " + value(ats.get(0), "value") + " "
                            + value(ats.get(0), "target") + " " + handler.desc);
                }
            }
        }
        assertEquals(List.of("[init()V] INVOKE " + ROW_ADD + " " + HANDLER), injectors);
    }

    /** Every grid button passes the wrapped call, labelled by the constant loaded last; Telemetry and Credits fill row 4. */
    @Test
    void telemetryAndCreditsAreTheLastRowOfTheTwoColumnGrid() throws IOException {
        ClassNode screen = read(SCREEN);
        Map<String, String> keys = componentKeys(screen);
        List<AbstractInsnNode> init = code(method(screen, "init", "()V"));
        List<String> added = new ArrayList<>();
        String label = null;
        int columns = -1;
        for (int i = 0; i < init.size(); i++) {
            if (init.get(i) instanceof FieldInsnNode f && f.getOpcode() == Opcodes.GETSTATIC && f.owner.equals(SCREEN)
                    && f.desc.equals(COMPONENT)) {
                label = keys.get(f.name);
            } else if (init.get(i) instanceof MethodInsnNode call) {
                if (call.name.equals("createRowHelper")) {
                    columns = init.get(i - 1).getOpcode() - Opcodes.ICONST_0;
                } else if (("L" + call.owner + ";" + call.name + call.desc).equals(ROW_ADD)) {
                    added.add(label);
                    label = null;
                }
            }
        }
        assertEquals(2, columns, "createRowHelper(2)");
        assertEquals(List.of("options.skinCustomisation", "options.sounds", "options.video", "options.controls",
                "options.language", "options.chat", "options.resourcepack", "options.accessibility",
                "options.telemetry", "options.credits_and_attribution"), added);
    }

    /** openScreenButton hands its Component to Button.builder untouched: the message keeps the translation key. */
    @Test
    void openScreenButtonKeepsTheLabelComponent() throws IOException {
        List<AbstractInsnNode> code = code(method(read(SCREEN), "openScreenButton",
                "(" + COMPONENT + "Ljava/util/function/Supplier;)Lnet/minecraft/client/gui/components/Button;"));
        assertTrue(code.get(0) instanceof VarInsnNode load && load.getOpcode() == Opcodes.ALOAD && load.var == 1,
                "the label is the builder's first operand");
        MethodInsnNode first = code.stream().filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast)
                .findFirst().orElseThrow();
        assertEquals("net/minecraft/client/gui/components/Button.builder(" + COMPONENT
                        + "Lnet/minecraft/client/gui/components/Button$OnPress;)Lnet/minecraft/client/gui/components/Button$Builder;",
                first.owner + "." + first.name + first.desc);
    }

    @Test
    void mixinIsRegistered() throws IOException {
        try (InputStream in = resource("pryzma.mixins.json")) {
            JsonArray client = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonArray("client");
            assertTrue(client.contains(new JsonPrimitive("OptionsScreenMixin")), client.toString());
        }
    }

    /** Static Component constants and their keys: ldc key, Component.translatable, putstatic. */
    private static Map<String, String> componentKeys(ClassNode screen) {
        Map<String, String> keys = new HashMap<>();
        List<AbstractInsnNode> clinit = code(method(screen, "<clinit>", "()V"));
        for (int i = 2; i < clinit.size(); i++) {
            if (clinit.get(i) instanceof FieldInsnNode f && f.getOpcode() == Opcodes.PUTSTATIC && f.desc.equals(COMPONENT)
                    && clinit.get(i - 1) instanceof MethodInsnNode m && m.name.equals("translatable")
                    && m.desc.equals("(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;")
                    && clinit.get(i - 2) instanceof LdcInsnNode ldc && ldc.cst instanceof String key) {
                keys.put(f.name, key);
            }
        }
        return keys;
    }

    private static List<AnnotationNode> annotations(MethodNode method) {
        List<AnnotationNode> all = new ArrayList<>();
        if (method.visibleAnnotations != null) {
            all.addAll(method.visibleAnnotations);
        }
        if (method.invisibleAnnotations != null) {
            all.addAll(method.invisibleAnnotations);
        }
        return all;
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

    /** Real instructions only: no labels, line numbers or frames. */
    private static List<AbstractInsnNode> code(MethodNode m) {
        List<AbstractInsnNode> code = new ArrayList<>();
        for (AbstractInsnNode insn : m.instructions) {
            if (insn.getOpcode() >= 0) {
                code.add(insn);
            }
        }
        return code;
    }

    private static MethodNode method(ClassNode owner, String name, String desc) {
        for (MethodNode m : owner.methods) {
            if (m.name.equals(name) && m.desc.equals(desc)) {
                return m;
            }
        }
        throw new AssertionError(owner.name + "." + name + desc + " is gone");
    }

    private static ClassNode read(String internalName) throws IOException {
        try (InputStream in = resource(internalName + ".class")) {
            ClassNode node = new ClassNode();
            new ClassReader(in).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
    }

    private static InputStream resource(String path) {
        InputStream in = OptionsScreenMixinTargetsTest.class.getClassLoader().getResourceAsStream(path);
        assertNotNull(in, path + " is not on the test classpath");
        return in;
    }
}
