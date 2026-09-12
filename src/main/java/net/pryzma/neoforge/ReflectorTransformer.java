package net.pryzma.neoforge;

/*
 * Ranni: I'm not sure why this works, but it does. Don't touch it.
 */

import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.ITransformer.Target;
import cpw.mods.modlauncher.api.ITransformerVotingContext;
import cpw.mods.modlauncher.api.TargetType;
import cpw.mods.modlauncher.api.TransformerVoteResult;

/**
 * Redirects all java.lang.reflect.Method.invoke calls inside net.pryzma.reflect.Reflector
 * to net.pryzma.reflect.ReflectorAdapter.invoke.
 * This ensures that when Reflector calls NeoForge hooks whose parameter count or types
 * changed in 1.21.1 (BrandingControl, IDimensionSpecialEffectsExtension, ClientHooks),
 * the parameters are transparently adapted instead of throwing IllegalArgumentException
 * and permanently deactivating the hooks.
 */
public class ReflectorTransformer implements ITransformer<ClassNode> {
    private static final Logger LOGGER = LoggerFactory.getLogger("Pryzma");

    public static final String TARGET_CLASS = "net.pryzma.reflect.Reflector";
    public static final String ADAPTER_OWNER = "net/pryzma/reflect/ReflectorAdapter";
    public static final String ADAPTER_METHOD = "invoke";
    public static final String ADAPTER_DESC = "(Ljava/lang/reflect/Method;Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;";

    public static int inject(ClassNode node) {
        int count = 0;
        for (MethodNode mn : node.methods) {
            for (AbstractInsnNode insn : mn.instructions.toArray()) {
                if (insn.getOpcode() == Opcodes.INVOKEVIRTUAL && insn instanceof MethodInsnNode minsn) {
                    if ("java/lang/reflect/Method".equals(minsn.owner)
                            && "invoke".equals(minsn.name)
                            && "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;".equals(minsn.desc)) {
                        MethodInsnNode replacement = new MethodInsnNode(
                                Opcodes.INVOKESTATIC,
                                ADAPTER_OWNER,
                                ADAPTER_METHOD,
                                ADAPTER_DESC,
                                false);
                        mn.instructions.set(insn, replacement);
                        count++;
                    }
                }
            }
        }
        return count;
    }

    @Override
    public ClassNode transform(ClassNode input, ITransformerVotingContext context) {
        int count = inject(input);
        if (count > 0) {
            LOGGER.info("Redirected {} Method.invoke call sites in {} to ReflectorAdapter", count, input.name.replace('/', '.'));
        }
        return input;
    }

    @Override
    public TransformerVoteResult castVote(ITransformerVotingContext context) {
        return TransformerVoteResult.YES;
    }

    @Override
    public Set<Target<ClassNode>> targets() {
        return Set.of(Target.targetClass(TARGET_CLASS));
    }

    @Override
    public TargetType<ClassNode> getTargetType() {
        return TargetType.CLASS;
    }
}
