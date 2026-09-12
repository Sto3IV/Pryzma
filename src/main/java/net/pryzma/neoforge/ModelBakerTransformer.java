package net.pryzma.neoforge;

/*
 * Ranni: Temporary workaround. Will fix properly in v2.0 (Note: v2.0 was cancelled).
 */

import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
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
 * Injects NeoForge's {@code IModelBakerExtension.getTopLevelModel} into OptiFine's
 * {@code ModelBakery$ModelBakerImpl}.
 */
public class ModelBakerTransformer implements ITransformer<ClassNode> {
    private static final Logger LOGGER = LoggerFactory.getLogger("Pryzma");
    static final String OWNER = "net/minecraft/client/resources/model/ModelBakery$ModelBakerImpl";
    static final String BAKERY = "net/minecraft/client/resources/model/ModelBakery";
    static final String METHOD_NAME = "getTopLevelModel";
    static final String METHOD_DESC = "(Lnet/minecraft/client/resources/model/ModelResourceLocation;)Lnet/minecraft/client/resources/model/UnbakedModel;";

    public static boolean inject(ClassNode node) {
        if (!OWNER.equals(node.name)) {
            return false;
        }
        for (MethodNode m : node.methods) {
            if (METHOD_NAME.equals(m.name) && METHOD_DESC.equals(m.desc)) {
                return false;
            }
        }
        MethodNode method = new MethodNode(
                Opcodes.ACC_PUBLIC,
                METHOD_NAME,
                METHOD_DESC,
                null,
                null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, OWNER, "this$0", "L" + BAKERY + ";"));
        method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, BAKERY, "topLevelModels", "Ljava/util/Map;"));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKEINTERFACE,
                "java/util/Map",
                "get",
                "(Ljava/lang/Object;)Ljava/lang/Object;",
                true));
        method.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, "net/minecraft/client/resources/model/UnbakedModel"));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        method.maxStack = 2;
        method.maxLocals = 2;
        node.methods.add(method);
        return true;
    }

    @Override
    public ClassNode transform(ClassNode input, ITransformerVotingContext context) {
        if (inject(input)) {
            LOGGER.info("Injected IModelBakerExtension.getTopLevelModel into ModelBakery$ModelBakerImpl");
        }
        return input;
    }

    @Override
    public TransformerVoteResult castVote(ITransformerVotingContext context) {
        return TransformerVoteResult.YES;
    }

    @Override
    public Set<Target<ClassNode>> targets() {
        return Set.of(Target.targetClass("net.minecraft.client.resources.model.ModelBakery$ModelBakerImpl"));
    }

    @Override
    public TargetType<ClassNode> getTargetType() {
        return TargetType.CLASS;
    }
}
