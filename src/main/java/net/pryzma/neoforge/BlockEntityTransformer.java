package net.pryzma.neoforge;

/*
 * Ranni: I'm not sure why this works, but it does. Don't touch it.
 */

import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
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
 * NeoForge's own {@code BlockEntity} stays loaded ({@link FilteredReplacementTransformer} withholds Pryzma's
 * Forge-era copy). OptiFine's only additions are grafted onto it: the {@code nbtTag}/{@code nbtTagUpdateMs}
 * cache read by {@code net.pryzma.RandomTileEntity}, and {@code setChanged()} dropping that cache.
 */
public class BlockEntityTransformer implements ITransformer<ClassNode> {
    private static final Logger LOGGER = LoggerFactory.getLogger("Pryzma");
    static final String OWNER = "net/minecraft/world/level/block/entity/BlockEntity";
    static final String FORGE_BASE = "net/minecraftforge/common/capabilities/CapabilityProvider";
    static final String TAG = "nbtTag";
    static final String TAG_DESC = "Lnet/minecraft/nbt/CompoundTag;";
    static final String TAG_MS = "nbtTagUpdateMs";
    static final String TAG_MS_DESC = "J";

    public static boolean inject(ClassNode node) {
        boolean changed = addField(node, TAG, TAG_DESC) | addField(node, TAG_MS, TAG_MS_DESC);
        for (MethodNode m : node.methods) {
            if (!"setChanged".equals(m.name) || !"()V".equals(m.desc) || clearsTag(m)) {
                continue;
            }
            for (AbstractInsnNode insn : m.instructions.toArray()) {
                if (insn.getOpcode() == Opcodes.RETURN) {
                    InsnList clear = new InsnList();
                    clear.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    clear.add(new InsnNode(Opcodes.ACONST_NULL));
                    clear.add(new FieldInsnNode(Opcodes.PUTFIELD, OWNER, TAG, TAG_DESC));
                    m.instructions.insertBefore(insn, clear);
                }
            }
            m.maxStack = Math.max(m.maxStack, 2);
            changed = true;
        }
        return changed;
    }

    private static boolean addField(ClassNode node, String name, String desc) {
        if (node.fields.stream().anyMatch(f -> name.equals(f.name))) {
            return false;
        }
        node.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, name, desc, null, null));
        return true;
    }

    private static boolean clearsTag(MethodNode m) {
        for (AbstractInsnNode insn : m.instructions) {
            if (insn instanceof FieldInsnNode f && f.getOpcode() == Opcodes.PUTFIELD
                    && OWNER.equals(f.owner) && TAG.equals(f.name)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ClassNode transform(ClassNode input, ITransformerVotingContext context) {
        if (FORGE_BASE.equals(input.superName)) {
            LOGGER.error("Pryzma's Forge-era BlockEntity is loaded; NeoForge attachments and capabilities will break");
        } else if (inject(input)) {
            LOGGER.info("Grafted OptiFine nbtTag cache onto NeoForge BlockEntity");
        }
        return input;
    }

    @Override
    public TransformerVoteResult castVote(ITransformerVotingContext context) {
        return TransformerVoteResult.YES;
    }

    @Override
    public Set<Target<ClassNode>> targets() {
        return Set.of(Target.targetClass("net.minecraft.world.level.block.entity.BlockEntity"));
    }

    @Override
    public TargetType<ClassNode> getTargetType() {
        return TargetType.CLASS;
    }
}
