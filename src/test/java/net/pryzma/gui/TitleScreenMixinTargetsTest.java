package net.pryzma.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * TitleScreenMixin against the NeoForge-patched TitleScreen on the classpath. The mixin config sets
 * defaultRequire 0, so a moved target would otherwise only show up as an unchanged main menu.
 */
class TitleScreenMixinTargetsTest {
    private static final String TITLE_SCREEN = "net/minecraft/client/gui/screens/TitleScreen";
    private static final String ADD_WIDGET = "(Lnet/minecraft/client/gui/components/events/GuiEventListener;)"
            + "Lnet/minecraft/client/gui/components/events/GuiEventListener;";
    private static final String NOTIFICATIONS = "com/mojang/realmsclient/gui/screens/RealmsNotificationsScreen";
    private static final String MODS_BUTTON = "net/neoforged/neoforge/client/gui/widget/ModsButton";

    /** prFilterRealmsButton: the three main buttons, Realms last, each added through the wrapped call. */
    @Test
    void realmsButtonIsAddedThroughTheWrappedCall() throws IOException {
        List<String> keys = new ArrayList<>();
        int adds = 0;
        for (AbstractInsnNode insn : method("createNormalMenuOptions", "(II)V").instructions) {
            if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof String key) {
                keys.add(key);
            } else if (insn instanceof MethodInsnNode call && call.owner.equals(TITLE_SCREEN)
                    && call.name.equals("addRenderableWidget") && call.desc.equals(ADD_WIDGET)) {
                adds++;
            }
        }
        assertEquals(List.of("menu.singleplayer", "menu.multiplayer", "menu.online"), keys);
        assertEquals(3, adds);
    }

    /** prDisableRealmsNotifications and prSkipRealmsNotifications: the gate, and the one no-arg construction in init. */
    @Test
    void notificationsScreenIsGatedAndBuiltOnlyInInit() throws IOException {
        method("realmsNotificationsEnabled", "()Z");
        int built = 0;
        for (MethodNode m : titleScreen().methods) {
            for (AbstractInsnNode insn : m.instructions) {
                if (insn instanceof MethodInsnNode call && call.owner.equals(NOTIFICATIONS) && call.name.equals("<init>")) {
                    assertEquals("init()V", m.name + m.desc, "notifications screen built outside init");
                    assertEquals("()V", call.desc);
                    built++;
                }
            }
        }
        assertEquals(1, built);
    }

    /** prAdjustTitleScreenLayout: l = height / 4 + 32, Mods at l + 72, l += 22, then the bottom row at l + 72 + 12. */
    @Test
    void neoForgeLayoutMatchesTheCompaction() throws IOException {
        List<AbstractInsnNode> code = code(method("init", "()V"));
        List<IincInsnNode> shifts = new ArrayList<>();
        for (AbstractInsnNode insn : code) {
            if (insn instanceof IincInsnNode iinc && iinc.incr == 22) {
                shifts.add(iinc);
            }
        }
        assertEquals(1, shifts.size(), "NeoForge's l += 22");
        int l = shifts.get(0).var;
        int shiftAt = code.indexOf(shifts.get(0));

        assertEquals(1, find(code, field("height"), op(Opcodes.ICONST_4), op(Opcodes.IDIV), push(32), op(Opcodes.IADD),
                var(Opcodes.ISTORE, l)).size(), "l = height / 4 + 32");
        assertEquals(1, find(code, newObject(MODS_BUTTON)).size(), "NeoForge Mods button");
        List<Integer> mods = find(code, var(Opcodes.ILOAD, l), push(72), op(Opcodes.IADD), call("pos"));
        assertEquals(1, mods.size(), "Mods button at l + 72");
        assertTrue(mods.get(0) < shiftAt, "Mods button placed before the shift");
        List<Integer> bottom = find(code, var(Opcodes.ILOAD, l), push(72), op(Opcodes.IADD), push(12), op(Opcodes.IADD));
        assertEquals(4, bottom.size(), "Language, Options, Quit and Accessibility at l + 72 + 12");
        assertTrue(bottom.get(0) > shiftAt, "bottom row placed after the shift");
    }

    private static ClassNode titleScreen() throws IOException {
        ClassNode node = new ClassNode();
        try (InputStream in = TitleScreenMixinTargetsTest.class.getClassLoader().getResourceAsStream(TITLE_SCREEN + ".class")) {
            assertNotNull(in, "TitleScreen.class not on the test classpath");
            new ClassReader(in).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        return node;
    }

    private static MethodNode method(String name, String desc) throws IOException {
        for (MethodNode m : titleScreen().methods) {
            if (m.name.equals(name) && m.desc.equals(desc)) {
                return m;
            }
        }
        throw new AssertionError("TitleScreen." + name + desc + " is gone");
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

    /** Start indices of every run of instructions matching {@code pattern}. */
    @SafeVarargs
    private static List<Integer> find(List<AbstractInsnNode> code, Predicate<AbstractInsnNode>... pattern) {
        List<Integer> starts = new ArrayList<>();
        for (int i = 0; i + pattern.length <= code.size(); i++) {
            int j = 0;
            while (j < pattern.length && pattern[j].test(code.get(i + j))) {
                j++;
            }
            if (j == pattern.length) {
                starts.add(i);
            }
        }
        return starts;
    }

    private static Predicate<AbstractInsnNode> op(int opcode) {
        return insn -> insn.getOpcode() == opcode;
    }

    private static Predicate<AbstractInsnNode> push(int value) {
        return insn -> insn instanceof IntInsnNode push && push.getOpcode() == Opcodes.BIPUSH && push.operand == value;
    }

    private static Predicate<AbstractInsnNode> var(int opcode, int slot) {
        return insn -> insn instanceof VarInsnNode v && v.getOpcode() == opcode && v.var == slot;
    }

    private static Predicate<AbstractInsnNode> field(String name) {
        return insn -> insn instanceof FieldInsnNode f && f.getOpcode() == Opcodes.GETFIELD && f.name.equals(name);
    }

    private static Predicate<AbstractInsnNode> call(String name) {
        return insn -> insn instanceof MethodInsnNode m && m.name.equals(name);
    }

    private static Predicate<AbstractInsnNode> newObject(String type) {
        return insn -> insn instanceof TypeInsnNode t && t.getOpcode() == Opcodes.NEW && t.desc.equals(type);
    }
}
