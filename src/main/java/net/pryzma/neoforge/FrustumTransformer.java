package net.pryzma.neoforge;

/*
 * Ranni: I'm not sure why this works, but it does. Don't touch it.
 */

import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.ITransformer.Target;
import cpw.mods.modlauncher.api.ITransformerVotingContext;
import cpw.mods.modlauncher.api.TargetType;
import cpw.mods.modlauncher.api.TransformerVoteResult;

/**
 * Strips missing {@code IBlockEntityExtension.INFINITE_EXTENT_AABB} check from
 * {@code Frustum.isVisible(AABB)}. In Forge this field existed, but NeoForge 21.1
 * removed it, causing NoSuchFieldError when culling entities.
 * NOPing out instructions 0..8 restores vanilla behavior and keeps stack/frames intact.
 */
public class FrustumTransformer implements ITransformer<ClassNode> {
    private static final Logger LOGGER = LoggerFactory.getLogger("Pryzma");
    private static final String TARGET = "net.minecraft.client.renderer.culling.Frustum";

    public static boolean patchIsVisible(ClassNode node) {
        boolean changed = false;
        for (MethodNode m : node.methods) {
            if ("isVisible".equals(m.name) && "(Lnet/minecraft/world/phys/AABB;)Z".equals(m.desc)) {
                for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (insn instanceof FieldInsnNode fin && "INFINITE_EXTENT_AABB".equals(fin.name)) {
                        AbstractInsnNode prev = fin.getPrevious();
                        AbstractInsnNode next = fin.getNext();
                        if (prev != null && prev.getOpcode() == Opcodes.ALOAD &&
                            next instanceof JumpInsnNode jin && jin.getOpcode() == Opcodes.IF_ACMPNE) {
                            AbstractInsnNode iconst1 = jin.getNext();
                            AbstractInsnNode iret = iconst1 != null ? iconst1.getNext() : null;
                            if (iconst1 != null && iconst1.getOpcode() == Opcodes.ICONST_1 &&
                                iret != null && iret.getOpcode() == Opcodes.IRETURN) {
                                m.instructions.set(prev, new InsnNode(Opcodes.NOP));
                                m.instructions.set(fin, new InsnNode(Opcodes.NOP));
                                m.instructions.set(jin, new InsnNode(Opcodes.NOP));
                                m.instructions.set(iconst1, new InsnNode(Opcodes.NOP));
                                m.instructions.set(iret, new InsnNode(Opcodes.NOP));
                                changed = true;
                                break;
                            }
                        }
                    }
                }
            }
        }
        return changed;
    }

    @Override
    public ClassNode transform(ClassNode input, ITransformerVotingContext context) {
        if (patchIsVisible(input)) {
            LOGGER.info("Patched Frustum.isVisible to eliminate missing INFINITE_EXTENT_AABB");
        }
        return input;
    }

    @Override
    public TransformerVoteResult castVote(ITransformerVotingContext context) {
        return TransformerVoteResult.YES;
    }

    @Override
    public Set<Target<ClassNode>> targets() {
        return Set.of(Target.targetClass(TARGET));
    }

    @Override
    public TargetType<ClassNode> getTargetType() {
        return TargetType.CLASS;
    }
}
