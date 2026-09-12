package net.pryzma.neoforge;

/*
 * Celian: Who the hell wrote this?
 * Ranni: Oh wait, it was me.
 */

import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.ITransformer.Target;
import cpw.mods.modlauncher.api.ITransformerVotingContext;
import cpw.mods.modlauncher.api.TargetType;
import cpw.mods.modlauncher.api.TransformerVoteResult;

/**
 * Pryzma {@code ResUtils} calls {@code Path.toFile()} on
 * {@code PathPackResources.root}. NeoForge userdev packs live on a union/jar
 * filesystem; that throws. Redirect to {@code PathPackScan.collect}.
 */
public class ResUtilsPathTransformer implements ITransformer<ClassNode> {
    private static final Logger LOGGER = LoggerFactory.getLogger("Pryzma");
    static final String OWNER = "net/pryzma/util/ResUtils";
    static final String SCAN = "net/pryzma/util/PathPackScan";
    static final String COLLECT_DESC =
            "(Ljava/nio/file/Path;[Ljava/lang/String;[Ljava/lang/String;)[Ljava/lang/String;";
    static final String PACK_DESC =
            "(Lnet/minecraft/server/packs/PackResources;[Ljava/lang/String;[Ljava/lang/String;[Ljava/lang/String;)[Ljava/lang/String;";

    public static boolean inject(ClassNode node) {
        MethodNode collect = null;
        for (MethodNode m : node.methods) {
            if ("collectFiles".equals(m.name) && PACK_DESC.equals(m.desc)) {
                collect = m;
                break;
            }
        }
        if (collect == null) {
            return false;
        }
        boolean changed = false;
        AbstractInsnNode insn = collect.instructions.getFirst();
        while (insn != null) {
            AbstractInsnNode next = insn.getNext();
            if (insn instanceof FieldInsnNode fi
                    && fi.getOpcode() == Opcodes.GETFIELD
                    && "root".equals(fi.name)
                    && "java/nio/file/Path".equals(stripDesc(fi.desc))) {
                AbstractInsnNode toFile = next;
                if (toFile instanceof MethodInsnNode mi
                        && "toFile".equals(mi.name)
                        && "java/nio/file/Path".equals(mi.owner)) {
                    AbstractInsnNode after = toFile.getNext();
                    collect.instructions.remove(toFile);
                    collect.instructions.insert(fi, new VarInsnNode(Opcodes.ALOAD, 1));
                    collect.instructions.insert(fi.getNext(), new VarInsnNode(Opcodes.ALOAD, 2));
                    collect.instructions.insert(
                            fi.getNext().getNext(),
                            new MethodInsnNode(Opcodes.INVOKESTATIC, SCAN, "collect", COLLECT_DESC, false));
                    collect.instructions.insert(
                            fi.getNext().getNext().getNext(), new InsnNode(Opcodes.ARETURN));
                    if (after != null
                            && after.getOpcode() == Opcodes.ASTORE
                            && after.getNext() != null
                            && after.getNext().getOpcode() == Opcodes.GOTO) {
                        collect.instructions.remove(after.getNext());
                        collect.instructions.remove(after);
                    }
                    changed = true;
                    break;
                }
            }
            insn = next;
        }
        return changed;
    }

    private static String stripDesc(String desc) {
        if (desc != null && desc.startsWith("L") && desc.endsWith(";")) {
            return desc.substring(1, desc.length() - 1);
        }
        return desc;
    }

    @Override
    public ClassNode transform(ClassNode input, ITransformerVotingContext context) {
        if (inject(input)) {
            LOGGER.info("Rewrote ResUtils Path.toFile to PathPackScan.collect");
        }
        return input;
    }

    @Override
    public TransformerVoteResult castVote(ITransformerVotingContext context) {
        return TransformerVoteResult.YES;
    }

    @Override
    public Set<Target<ClassNode>> targets() {
        return Set.of(Target.targetClass("net.pryzma.util.ResUtils"));
    }

    @Override
    public TargetType<ClassNode> getTargetType() {
        return TargetType.CLASS;
    }
}
