package net.pryzma.neoforge;

/*
 * Ranni: this code is so bad that my eyes are bleeding.
 */

import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
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
 * Forge added {@code ItemTags.create(String, String)}. NeoForge only has
 * {@code create(ResourceLocation)}. Pryzma's DyeColor uses the two-string
 * form via Reflector; without it {@code DyeColor.getTag()} is null and
 * {@code Tags.Items.DYES_*} / TagConventionLogWarning NPE.
 */
public class ItemTagsCreateTransformer implements ITransformer<ClassNode> {
    private static final Logger LOGGER = LoggerFactory.getLogger("Pryzma");
    static final String OWNER = "net/minecraft/tags/ItemTags";
    static final String NAME = "create";
    static final String DESC = "(Ljava/lang/String;Ljava/lang/String;)Lnet/minecraft/tags/TagKey;";
    static final String SIGNATURE =
            "(Ljava/lang/String;Ljava/lang/String;)Lnet/minecraft/tags/TagKey<Lnet/minecraft/world/item/Item;>;";
    static final String RL_OWNER = "net/minecraft/resources/ResourceLocation";
    static final String RL_FROM = "fromNamespaceAndPath";
    static final String RL_DESC =
            "(Ljava/lang/String;Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;";
    static final String CREATE_RL_DESC =
            "(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/tags/TagKey;";

    /**
     * Adds {@code create(String, String)} if missing. Returns true when a method
     * was inserted.
     */
    public static boolean injectCreate(ClassNode node) {
        for (MethodNode m : node.methods) {
            if (NAME.equals(m.name) && DESC.equals(m.desc)) {
                return false;
            }
        }
        MethodNode mn = new MethodNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, NAME, DESC, SIGNATURE, null);
        InsnList ins = mn.instructions;
        ins.add(new VarInsnNode(Opcodes.ALOAD, 0));
        ins.add(new VarInsnNode(Opcodes.ALOAD, 1));
        ins.add(new MethodInsnNode(Opcodes.INVOKESTATIC, RL_OWNER, RL_FROM, RL_DESC, false));
        ins.add(new MethodInsnNode(Opcodes.INVOKESTATIC, OWNER, NAME, CREATE_RL_DESC, false));
        ins.add(new InsnNode(Opcodes.ARETURN));
        mn.maxStack = 2;
        mn.maxLocals = 2;
        node.methods.add(mn);
        return true;
    }

    @Override
    public ClassNode transform(ClassNode input, ITransformerVotingContext context) {
        if (injectCreate(input)) {
            LOGGER.info("Injected ItemTags.create(String, String) for Pryzma DyeColor tags");
        }
        return input;
    }

    @Override
    public TransformerVoteResult castVote(ITransformerVotingContext context) {
        return TransformerVoteResult.YES;
    }

    @Override
    public Set<Target<ClassNode>> targets() {
        return Set.of(Target.targetClass("net.minecraft.tags.ItemTags"));
    }

    @Override
    public TargetType<ClassNode> getTargetType() {
        return TargetType.CLASS;
    }
}
