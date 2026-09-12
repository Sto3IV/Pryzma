package net.pryzma.neoforge;

/*
 * Ranni: FUCK FUCK FUCK. I mean... everything is fine, Celian.
 */

import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.ITransformer.Target;
import cpw.mods.modlauncher.api.ITransformerVotingContext;
import cpw.mods.modlauncher.api.TargetType;
import cpw.mods.modlauncher.api.TransformerVoteResult;

/**
 * Pryzma replaces {@code Gui} wholesale; NeoForge's
 * {@code ClientHooks.initClientHooks} then calls {@code initModdedOverlays()}.
 * Inject the missing field/methods onto the Pryzma class (CLASS phase, after
 * PRE_CLASS replacement).
 */
public class GuiInitModdedOverlaysTransformer implements ITransformer<ClassNode> {
    private static final Logger LOGGER = LoggerFactory.getLogger("Pryzma");
    static final String OWNER = "net/minecraft/client/gui/Gui";
    static final String FIELD = "layerManager";
    static final String FIELD_DESC = "Lnet/neoforged/neoforge/client/gui/GuiLayerManager;";
    static final String MANAGER = "net/neoforged/neoforge/client/gui/GuiLayerManager";
    static final String INIT_NAME = "initModdedOverlays";
    static final String INIT_DESC = "()V";
    static final String COUNT_NAME = "getLayerCount";
    static final String COUNT_DESC = "()I";

    public static boolean inject(ClassNode node) {
        boolean changed = false;
        if (node.fields.stream().noneMatch(f -> FIELD.equals(f.name) && FIELD_DESC.equals(f.desc))) {
            node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, FIELD, FIELD_DESC, null, null));
            changed = true;
        }
        if (node.methods.stream().noneMatch(m -> INIT_NAME.equals(m.name) && INIT_DESC.equals(m.desc))) {
            node.methods.add(buildInitModdedOverlays());
            changed = true;
        }
        if (node.methods.stream().noneMatch(m -> COUNT_NAME.equals(m.name) && COUNT_DESC.equals(m.desc))) {
            node.methods.add(buildGetLayerCount());
            changed = true;
        }
        return changed;
    }

    private static MethodNode buildInitModdedOverlays() {
        MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC, INIT_NAME, INIT_DESC, null, null);
        InsnList ins = mn.instructions;
        LabelNode hasField = new LabelNode();
        ins.add(new VarInsnNode(Opcodes.ALOAD, 0));
        ins.add(new FieldInsnNode(Opcodes.GETFIELD, OWNER, FIELD, FIELD_DESC));
        ins.add(new JumpInsnNode(Opcodes.IFNONNULL, hasField));
        ins.add(new VarInsnNode(Opcodes.ALOAD, 0));
        ins.add(new TypeInsnNode(Opcodes.NEW, MANAGER));
        ins.add(new InsnNode(Opcodes.DUP));
        ins.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, MANAGER, "<init>", "()V", false));
        ins.add(new FieldInsnNode(Opcodes.PUTFIELD, OWNER, FIELD, FIELD_DESC));
        ins.add(hasField);
        ins.add(new VarInsnNode(Opcodes.ALOAD, 0));
        ins.add(new FieldInsnNode(Opcodes.GETFIELD, OWNER, FIELD, FIELD_DESC));
        ins.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, MANAGER, "initModdedLayers", "()V", false));
        ins.add(new InsnNode(Opcodes.RETURN));
        mn.maxStack = 3;
        mn.maxLocals = 1;
        return mn;
    }

    private static MethodNode buildGetLayerCount() {
        MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC, COUNT_NAME, COUNT_DESC, null, null);
        InsnList ins = mn.instructions;
        LabelNode zero = new LabelNode();
        ins.add(new VarInsnNode(Opcodes.ALOAD, 0));
        ins.add(new FieldInsnNode(Opcodes.GETFIELD, OWNER, FIELD, FIELD_DESC));
        ins.add(new JumpInsnNode(Opcodes.IFNULL, zero));
        ins.add(new VarInsnNode(Opcodes.ALOAD, 0));
        ins.add(new FieldInsnNode(Opcodes.GETFIELD, OWNER, FIELD, FIELD_DESC));
        ins.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, MANAGER, COUNT_NAME, COUNT_DESC, false));
        ins.add(new InsnNode(Opcodes.IRETURN));
        ins.add(zero);
        ins.add(new InsnNode(Opcodes.ICONST_0));
        ins.add(new InsnNode(Opcodes.IRETURN));
        mn.maxStack = 1;
        mn.maxLocals = 1;
        return mn;
    }

    @Override
    public ClassNode transform(ClassNode input, ITransformerVotingContext context) {
        if (inject(input)) {
            LOGGER.info("Injected Gui.initModdedOverlays / getLayerCount for NeoForge ClientHooks");
        }
        return input;
    }

    @Override
    public TransformerVoteResult castVote(ITransformerVotingContext context) {
        return TransformerVoteResult.YES;
    }

    @Override
    public Set<Target<ClassNode>> targets() {
        return Set.of(Target.targetClass("net.minecraft.client.gui.Gui"));
    }

    @Override
    public TargetType<ClassNode> getTargetType() {
        return TargetType.CLASS;
    }
}
