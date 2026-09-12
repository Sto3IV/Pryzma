package net.pryzma.neoforge;

/*
 * Celian: I think we missed a null check.
 * Ranni: The universe is a null check. Let it throw.
 * I had to write 40 lines of ASM to bypass a private final field. My dignity is gone, but the render stage is restored.
 */

import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
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
 * Replaces the body of {@code ReflectorForge.dispatchRenderStageS} with a direct delegation to
 * {@code ReflectorAdapter.dispatchRenderStageS}.
 *
 * <p>In legacy Forge/OptiFine, {@code dispatchRenderStageS} looked for {@code Stage.dispatch}, which does not
 * exist in NeoForge 21.1 (moved to {@code ClientHooks.dispatchRenderStage}). This caused all callers,
 * notably {@code AFTER_LEVEL} in {@code GameRenderer} and any external caller, to silently abort at line 9.
 * Rewriting the method at its definition guarantees that all callers dispatch successfully without
 * reflection or boxing overhead.
 */
public class ReflectorForgeTransformer implements ITransformer<ClassNode> {
    private static final Logger LOGGER = LoggerFactory.getLogger("Pryzma");

    static final String TARGET = "net.pryzma.reflect.ReflectorForge";
    static final String METHOD = "dispatchRenderStageS";
    static final String DESC = "(Lnet/pryzma/reflect/ReflectorField;Lnet/minecraft/client/renderer/LevelRenderer;"
            + "Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;ILnet/minecraft/client/Camera;"
            + "Lnet/minecraft/client/renderer/culling/Frustum;)V";

    static final String ADAPTER = "net/pryzma/reflect/ReflectorAdapter";
    static final String ADAPTER_METHOD = "dispatchRenderStageS";

    public static boolean inject(ClassNode node) {
        MethodNode m = node.methods.stream()
                .filter(c -> METHOD.equals(c.name) && DESC.equals(c.desc) && (c.access & Opcodes.ACC_STATIC) != 0)
                .findFirst()
                .orElse(null);
        if (m == null || isBridged(m)) {
            return false;
        }

        m.instructions.clear();
        int slot = 0;
        for (Type arg : Type.getArgumentTypes(DESC)) {
            m.instructions.add(new VarInsnNode(arg.getOpcode(Opcodes.ILOAD), slot));
            slot += arg.getSize();
        }
        m.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, ADAPTER, ADAPTER_METHOD, DESC, false));
        m.instructions.add(new InsnNode(Opcodes.RETURN));
        m.tryCatchBlocks.clear();
        m.localVariables = null;
        m.visibleLocalVariableAnnotations = null;
        m.invisibleLocalVariableAnnotations = null;
        m.maxStack = slot;
        m.maxLocals = slot;
        return true;
    }

    private static boolean isBridged(MethodNode m) {
        for (AbstractInsnNode insn : m.instructions) {
            if (insn instanceof MethodInsnNode call && ADAPTER.equals(call.owner) && ADAPTER_METHOD.equals(call.name)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ClassNode transform(ClassNode input, ITransformerVotingContext context) {
        if (inject(input)) {
            LOGGER.info("ReflectorForge.dispatchRenderStageS bridged to ReflectorAdapter.dispatchRenderStageS");
        } else {
            LOGGER.warn("ReflectorForge.dispatchRenderStageS{} not found or already bridged", DESC);
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
