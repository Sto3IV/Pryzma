package net.pryzma.neoforge;

/*
 * Ranni: Abandon all hope, ye who enter here.
 */

import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
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
 * Injects Smooth World mob tick culling at the head of {@code Mob.tick()}.
 *
 * <p>Since {@code net.minecraft.world.entity.Mob} is withheld by {@link FilteredReplacementTransformer}
 * to keep NeoForge contracts (e.g. {@code isAddedToWorld()}, entity capability attachments, and overrides),
 * this transformer grafts OptiFine's server-side mob optimizations directly onto NeoForge's {@code Mob}.
 */
public class MobTransformer implements ITransformer<ClassNode> {
    private static final Logger LOGGER = LoggerFactory.getLogger("Pryzma");
    private static final String TARGET = "net.minecraft.world.entity.Mob";
    private static final String HELPER = "net/pryzma/util/SmoothWorldHelper";

    public static boolean inject(ClassNode node) {
        for (MethodNode m : node.methods) {
            if ("tick".equals(m.name) && "()V".equals(m.desc)) {
                for (AbstractInsnNode insn : m.instructions.toArray()) {
                    if (insn instanceof MethodInsnNode minsn && HELPER.equals(minsn.owner)) {
                        return false; // already injected
                    }
                }

                LabelNode continueLabel = new LabelNode();
                InsnList list = new InsnList();
                list.add(new VarInsnNode(Opcodes.ALOAD, 0));
                list.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        HELPER,
                        "shouldSkipMobUpdate",
                        "(Lnet/minecraft/world/entity/Mob;)Z",
                        false));
                list.add(new JumpInsnNode(Opcodes.IFEQ, continueLabel));
                list.add(new VarInsnNode(Opcodes.ALOAD, 0));
                list.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        HELPER,
                        "onMobUpdateMinimal",
                        "(Lnet/minecraft/world/entity/Mob;)V",
                        false));
                list.add(new InsnNode(Opcodes.RETURN));
                list.add(continueLabel);

                m.instructions.insert(list);
                m.maxStack = Math.max(m.maxStack, 2);
                return true;
            }
        }
        return false;
    }

    @Override
    public ClassNode transform(ClassNode input, ITransformerVotingContext context) {
        if (inject(input)) {
            LOGGER.info("Injected Smooth World mob tick culling into Mob.tick()");
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
