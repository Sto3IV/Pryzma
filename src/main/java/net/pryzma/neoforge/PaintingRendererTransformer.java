package net.pryzma.neoforge;

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
import cpw.mods.modlauncher.api.ITransformerVotingContext;
import cpw.mods.modlauncher.api.TargetType;
import cpw.mods.modlauncher.api.TransformerVoteResult;

/**
 * Injects Fast Paintings consolidated rendering at the head of {@code PaintingRenderer.renderPainting()}.
 */
public class PaintingRendererTransformer implements ITransformer<ClassNode> {
    private static final Logger LOGGER = LoggerFactory.getLogger("Pryzma");
    private static final String TARGET = "net.minecraft.client.renderer.entity.PaintingRenderer";
    private static final String HELPER = "net/pryzma/util/FastPaintingHelper";

    public static boolean inject(ClassNode node) {
        for (MethodNode m : node.methods) {
            if ("renderPainting".equals(m.name)
                    && "(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/entity/decoration/Painting;IILnet/minecraft/client/renderer/texture/TextureAtlasSprite;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V".equals(m.desc)) {
                for (AbstractInsnNode insn : m.instructions.toArray()) {
                    if (insn instanceof MethodInsnNode minsn && HELPER.equals(minsn.owner)) {
                        return false; // already injected
                    }
                }

                LabelNode continueLabel = new LabelNode();
                InsnList list = new InsnList();
                list.add(new VarInsnNode(Opcodes.ALOAD, 1));
                list.add(new VarInsnNode(Opcodes.ALOAD, 2));
                list.add(new VarInsnNode(Opcodes.ALOAD, 3));
                list.add(new VarInsnNode(Opcodes.ILOAD, 4));
                list.add(new VarInsnNode(Opcodes.ILOAD, 5));
                list.add(new VarInsnNode(Opcodes.ALOAD, 6));
                list.add(new VarInsnNode(Opcodes.ALOAD, 7));
                list.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        HELPER,
                        "renderPainting",
                        "(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/entity/decoration/Painting;IILnet/minecraft/client/renderer/texture/TextureAtlasSprite;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;)Z",
                        false));
                list.add(new JumpInsnNode(Opcodes.IFEQ, continueLabel));
                list.add(new InsnNode(Opcodes.RETURN));
                list.add(continueLabel);

                m.instructions.insert(list);
                m.maxStack = Math.max(m.maxStack, 7);
                return true;
            }
        }
        return false;
    }

    @Override
    public ClassNode transform(ClassNode input, ITransformerVotingContext context) {
        if (inject(input)) {
            LOGGER.info("PaintingRendererTransformer: successfully hooked into PaintingRenderer.renderPainting()");
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
