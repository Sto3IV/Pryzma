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
import org.objectweb.asm.tree.LdcInsnNode;
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
 * Bridges OptiFine's pre-compiled {@code ClientLevel} to NeoForge 21.1 contracts:
 * <ul>
 *   <li>Level daytime methods ({@code setDayTimeFraction}, {@code getDayTimeFraction}, etc.).</li>
 *   <li>{@code getModelData(BlockPos)} routing to {@code modelDataManager.getAt(pos)}.</li>
 * </ul>
 */
public class ClientLevelTransformer implements ITransformer<ClassNode> {
    private static final Logger LOGGER = LoggerFactory.getLogger("Pryzma");
    private static final String TARGET = "net.minecraft.client.multiplayer.ClientLevel";
    private static final String OWNER = "net/minecraft/client/multiplayer/ClientLevel";

    public static boolean injectDayTime(ClassNode node) {
        for (MethodNode m : node.methods) {
            if ("setDayTimeFraction".equals(m.name) && "(F)V".equals(m.desc)) {
                return false;
            }
        }

        boolean hasFractionField = false;
        boolean hasPerTickField = false;
        for (FieldNode f : node.fields) {
            if ("dayTimeFraction".equals(f.name) && "F".equals(f.desc)) {
                hasFractionField = true;
            }
            if ("dayTimePerTick".equals(f.name) && "F".equals(f.desc)) {
                hasPerTickField = true;
            }
        }
        if (!hasFractionField) {
            node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "dayTimeFraction", "F", null, null));
        }
        if (!hasPerTickField) {
            node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "dayTimePerTick", "F", null, Float.valueOf(-1.0f)));
        }

        for (MethodNode m : node.methods) {
            if ("<init>".equals(m.name)) {
                for (var insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (insn.getOpcode() == Opcodes.RETURN) {
                        InsnList initList = new InsnList();
                        initList.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        initList.add(new LdcInsnNode(Float.valueOf(-1.0f)));
                        initList.add(new FieldInsnNode(Opcodes.PUTFIELD, OWNER, "dayTimePerTick", "F"));
                        m.instructions.insertBefore(insn, initList);
                        m.maxStack = Math.max(m.maxStack, 2);
                        break;
                    }
                }
            }
        }

        // 1. void setDayTimeFraction(float)
        MethodNode m1 = new MethodNode(Opcodes.ACC_PUBLIC, "setDayTimeFraction", "(F)V", null, null);
        m1.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        m1.instructions.add(new VarInsnNode(Opcodes.FLOAD, 1));
        m1.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, OWNER, "dayTimeFraction", "F"));
        m1.instructions.add(new InsnNode(Opcodes.RETURN));
        m1.maxStack = 2;
        m1.maxLocals = 2;
        node.methods.add(m1);

        // 2. float getDayTimeFraction()
        MethodNode m2 = new MethodNode(Opcodes.ACC_PUBLIC, "getDayTimeFraction", "()F", null, null);
        m2.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        m2.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, OWNER, "dayTimeFraction", "F"));
        m2.instructions.add(new InsnNode(Opcodes.FRETURN));
        m2.maxStack = 1;
        m2.maxLocals = 1;
        node.methods.add(m2);

        // 3. float getDayTimePerTick()
        MethodNode m3 = new MethodNode(Opcodes.ACC_PUBLIC, "getDayTimePerTick", "()F", null, null);
        m3.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        m3.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, OWNER, "dayTimePerTick", "F"));
        m3.instructions.add(new InsnNode(Opcodes.FRETURN));
        m3.maxStack = 1;
        m3.maxLocals = 1;
        node.methods.add(m3);

        // 4. void setDayTimePerTick(float)
        MethodNode m4 = new MethodNode(Opcodes.ACC_PUBLIC, "setDayTimePerTick", "(F)V", null, null);
        m4.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        m4.instructions.add(new VarInsnNode(Opcodes.FLOAD, 1));
        m4.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, OWNER, "dayTimePerTick", "F"));
        m4.instructions.add(new InsnNode(Opcodes.RETURN));
        m4.maxStack = 2;
        m4.maxLocals = 2;
        node.methods.add(m4);

        return true;
    }

    public static boolean injectModelData(ClassNode node) {
        boolean hasModelData = node.methods.stream().anyMatch(m ->
                "getModelData".equals(m.name)
                        && "(Lnet/minecraft/core/BlockPos;)Lnet/neoforged/neoforge/client/model/data/ModelData;".equals(m.desc));
        if (hasModelData) {
            return false;
        }

        MethodNode m = new MethodNode(
                Opcodes.ACC_PUBLIC,
                "getModelData",
                "(Lnet/minecraft/core/BlockPos;)Lnet/neoforged/neoforge/client/model/data/ModelData;",
                null,
                null);
        LabelNode notNull = new LabelNode();
        m.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        m.instructions.add(new FieldInsnNode(
                Opcodes.GETFIELD,
                OWNER,
                "modelDataManager",
                "Lnet/neoforged/neoforge/client/model/data/ModelDataManager;"));
        m.instructions.add(new InsnNode(Opcodes.DUP));
        m.instructions.add(new JumpInsnNode(Opcodes.IFNONNULL, notNull));
        m.instructions.add(new InsnNode(Opcodes.POP));
        m.instructions.add(new FieldInsnNode(
                Opcodes.GETSTATIC,
                "net/neoforged/neoforge/client/model/data/ModelData",
                "EMPTY",
                "Lnet/neoforged/neoforge/client/model/data/ModelData;"));
        m.instructions.add(new InsnNode(Opcodes.ARETURN));
        m.instructions.add(notNull);
        m.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        m.instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                "net/neoforged/neoforge/client/model/data/ModelDataManager",
                "getAt",
                "(Lnet/minecraft/core/BlockPos;)Lnet/neoforged/neoforge/client/model/data/ModelData;",
                false));
        m.instructions.add(new InsnNode(Opcodes.ARETURN));
        m.maxStack = 2;
        m.maxLocals = 2;
        node.methods.add(m);
        return true;
    }

    public static boolean injectLevelLoadEvent(ClassNode node) {
        boolean patched = false;
        for (MethodNode m : node.methods) {
            if ("<init>".equals(m.name)) {
                boolean hasPost = false;
                for (var insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (insn instanceof MethodInsnNode min
                            && "net/neoforged/bus/api/IEventBus".equals(min.owner)
                            && "post".equals(min.name)) {
                        hasPost = true;
                        break;
                    }
                }
                if (hasPost) {
                    continue;
                }

                for (var insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (insn.getOpcode() == Opcodes.RETURN) {
                        InsnList eventList = new InsnList();
                        eventList.add(new FieldInsnNode(
                                Opcodes.GETSTATIC,
                                "net/neoforged/neoforge/common/NeoForge",
                                "EVENT_BUS",
                                "Lnet/neoforged/bus/api/IEventBus;"));
                        eventList.add(new TypeInsnNode(
                                Opcodes.NEW,
                                "net/neoforged/neoforge/event/level/LevelEvent$Load"));
                        eventList.add(new InsnNode(Opcodes.DUP));
                        eventList.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        eventList.add(new MethodInsnNode(
                                Opcodes.INVOKESPECIAL,
                                "net/neoforged/neoforge/event/level/LevelEvent$Load",
                                "<init>",
                                "(Lnet/minecraft/world/level/LevelAccessor;)V",
                                false));
                        eventList.add(new MethodInsnNode(
                                Opcodes.INVOKEINTERFACE,
                                "net/neoforged/bus/api/IEventBus",
                                "post",
                                "(Lnet/neoforged/bus/api/Event;)Lnet/neoforged/bus/api/Event;",
                                true));
                        eventList.add(new InsnNode(Opcodes.POP));

                        m.instructions.insertBefore(insn, eventList);
                        m.maxStack = Math.max(m.maxStack, 4);
                        patched = true;
                        break;
                    }
                }
            }
        }
        return patched;
    }

    public static boolean inject(ClassNode node) {
        boolean d = injectDayTime(node);
        boolean m = injectModelData(node);
        boolean e = injectLevelLoadEvent(node);
        return d || m || e;
    }

    @Override
    public ClassNode transform(ClassNode input, ITransformerVotingContext context) {
        if (inject(input)) {
            LOGGER.info("Injected NeoForge contract methods into ClientLevel");
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
