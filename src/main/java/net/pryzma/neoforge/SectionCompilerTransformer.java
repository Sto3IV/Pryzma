package net.pryzma.neoforge;

/*
 * Celian: Can we optimize this loop?
 * Ranni: I optimized it by not writing it. We use the cache.
 * I am forced to inject this directly into the bytecode because Celian thought 'it would be fun to support multi-threading'. This is the definition of suffering.
 */

import java.util.HashSet;
import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.ITransformer.Target;
import cpw.mods.modlauncher.api.ITransformerVotingContext;
import cpw.mods.modlauncher.api.TargetType;
import cpw.mods.modlauncher.api.TransformerVoteResult;

/**
 * Rewires Pryzma's Forge-era section meshing onto NeoForge 21.1 contracts; glue in
 * {@code net.pryzma.util.NeoForgeMeshing}.
 * <ul>
 * <li>Model data. NeoForge snapshots {@code ModelDataManager} on the render thread when a region is created
 * and meshes from {@code RenderChunkRegion.getModelData}. Pryzma calls Forge's removed
 * {@code getAt(SectionPos) -> Map<BlockPos, ModelData>} on builder threads (NoSuchMethodError; NeoForge's
 * overload is long-keyed, refreshed only on the render thread and not thread-safe). Restored: capture in
 * {@code RenderRegionCache.createRegion}, hold and serve in {@code RenderChunkRegion}, delegate from
 * {@code ChunkCachePryzma} (the getter models receive), read through it in {@code SectionCompiler.compile}.</li>
 * <li>Ambient occlusion. {@code ModelBlockRenderer.tesselateBlock} gates AO through Forge's
 * {@code BakedModel.useAmbientOcclusion(BlockState, RenderType)}; NeoForge only has the {@code TriState}
 * overload, decided exactly as NeoForge's own tesselateBlock does.</li>
 * </ul>
 */
public class SectionCompilerTransformer implements ITransformer<ClassNode> {
    private static final Logger LOGGER = LoggerFactory.getLogger("Pryzma");
    static final String COMPILER = "net/minecraft/client/renderer/chunk/SectionCompiler";
    static final String REGION_CACHE = "net/minecraft/client/renderer/chunk/RenderRegionCache";
    static final String REGION = "net/minecraft/client/renderer/chunk/RenderChunkRegion";
    static final String PRYZMA_CACHE = "net/pryzma/override/ChunkCachePryzma";
    static final String BLOCK_RENDERER = "net/minecraft/client/renderer/block/ModelBlockRenderer";
    static final String GLUE = "net/pryzma/util/NeoForgeMeshing";

    static final String LEVEL = "Lnet/minecraft/world/level/Level;";
    static final String SECTION_POS = "Lnet/minecraft/core/SectionPos;";
    static final String BLOCK_POS = "Lnet/minecraft/core/BlockPos;";
    static final String GETTER = "Lnet/minecraft/world/level/BlockAndTintGetter;";
    static final String STATE = "Lnet/minecraft/world/level/block/state/BlockState;";
    static final String RENDER_TYPE = "Lnet/minecraft/client/renderer/RenderType;";
    static final String BAKED_MODEL = "net/minecraft/client/resources/model/BakedModel";
    static final String MODEL_DATA = "Lnet/neoforged/neoforge/client/model/data/ModelData;";
    static final String MANAGER = "net/neoforged/neoforge/client/model/data/ModelDataManager";
    static final String SNAPSHOT = "modelDataSnapshot";
    static final String SNAPSHOT_DESC = "Lit/unimi/dsi/fastutil/longs/Long2ObjectFunction;";
    static final String GET_MODEL_DATA = "getModelData";
    static final String GET_MODEL_DATA_DESC = "(" + BLOCK_POS + ")" + MODEL_DATA;
    static final String FORGE_AO_DESC = "(" + STATE + RENDER_TYPE + ")Z";

    static final String CAPTURE_DESC = "(" + LEVEL + SECTION_POS + ")" + SNAPSHOT_DESC;
    static final String LOOKUP_DESC = "(" + SNAPSHOT_DESC + BLOCK_POS + ")" + MODEL_DATA;
    static final String VIEW_DESC = "(" + GETTER + ")Ljava/util/Map;";
    static final String AO_DESC = "(L" + BAKED_MODEL + ";" + STATE + RENDER_TYPE + MODEL_DATA + GETTER + BLOCK_POS + ")Z";

    public static boolean inject(ClassNode node) {
        return switch (node.name) {
            case COMPILER -> patchCompiler(node);
            case REGION_CACHE -> patchRegionCache(node);
            case REGION -> patchRegion(node);
            case PRYZMA_CACHE -> patchPryzmaCache(node);
            case BLOCK_RENDERER -> patchAmbientOcclusion(node);
            default -> false;
        };
    }

    /** {@code Minecraft.getInstance().level.getModelDataManager().getAt(sectionPos)} -> {@code modelDataView(chunkCache)}. */
    static boolean patchCompiler(ClassNode node) {
        boolean changed = false;
        for (MethodNode m : node.methods) {
            int cache = paramSlot(m, "L" + PRYZMA_CACHE + ";");
            for (AbstractInsnNode insn : m.instructions.toArray()) {
                if (!(insn instanceof MethodInsnNode getAt && MANAGER.equals(getAt.owner) && "getAt".equals(getAt.name)
                        && getAt.desc.startsWith("(" + SECTION_POS + ")"))) {
                    continue;
                }
                AbstractInsnNode pos = prev(getAt);
                AbstractInsnNode manager = prev(pos);
                AbstractInsnNode level = prev(manager);
                AbstractInsnNode minecraft = prev(level);
                if (cache < 0 || !isLoad(pos) || !isCall(manager, null, "getModelDataManager")
                        || !(level instanceof FieldInsnNode f && "net/minecraft/client/Minecraft".equals(f.owner) && "level".equals(f.name))
                        || !isCall(minecraft, "net/minecraft/client/Minecraft", "getInstance") || !jumpFree(m, minecraft, getAt)) {
                    LOGGER.warn("{}.{}: unrecognised ModelDataManager.getAt(SectionPos) call left in place", node.name, m.name);
                    continue;
                }
                InsnList view = new InsnList();
                view.add(new VarInsnNode(Opcodes.ALOAD, cache));
                view.add(new MethodInsnNode(Opcodes.INVOKESTATIC, GLUE, "modelDataView", VIEW_DESC, false));
                m.instructions.insertBefore(minecraft, view);
                removeAll(m, minecraft, level, manager, pos, getAt);
                changed = true;
            }
        }
        return changed;
    }

    /** Render thread: attach NeoForge's model-data snapshot to every region {@code createRegion} builds. */
    static boolean patchRegionCache(ClassNode node) {
        boolean changed = false;
        for (MethodNode m : node.methods) {
            if (!"createRegion".equals(m.name) || (m.access & Opcodes.ACC_STATIC) != 0
                    || !m.desc.startsWith("(" + LEVEL + SECTION_POS) || writes(m, 1) || writes(m, 2)
                    || touches(m, Opcodes.PUTFIELD, REGION, SNAPSHOT)) {
                continue;
            }
            for (AbstractInsnNode insn : m.instructions.toArray()) {
                if (insn instanceof MethodInsnNode init && init.getOpcode() == Opcodes.INVOKESPECIAL
                        && REGION.equals(init.owner) && "<init>".equals(init.name) && !init.desc.contains(SNAPSHOT_DESC)) {
                    InsnList attach = new InsnList();
                    attach.add(new InsnNode(Opcodes.DUP));
                    attach.add(new VarInsnNode(Opcodes.ALOAD, 1));
                    attach.add(new VarInsnNode(Opcodes.ALOAD, 2));
                    attach.add(new MethodInsnNode(Opcodes.INVOKESTATIC, GLUE, "captureModelData", CAPTURE_DESC, false));
                    attach.add(new FieldInsnNode(Opcodes.PUTFIELD, REGION, SNAPSHOT, SNAPSHOT_DESC));
                    m.instructions.insert(init, attach);
                    m.maxStack += 3;
                    changed = true;
                }
            }
        }
        return changed;
    }

    /** Hold the snapshot and serve it like NeoForge's {@code RenderChunkRegion.getModelData}. */
    static boolean patchRegion(ClassNode node) {
        if (hasMethod(node, GET_MODEL_DATA, GET_MODEL_DATA_DESC)) {
            return false;
        }
        if (node.fields.stream().noneMatch(f -> SNAPSHOT.equals(f.name))) {
            node.fields.add(new FieldNode(0, SNAPSHOT, SNAPSHOT_DESC, null, null));
        }
        node.methods.add(modelDataGetter(
                new FieldInsnNode(Opcodes.GETFIELD, REGION, SNAPSHOT, SNAPSHOT_DESC),
                new MethodInsnNode(Opcodes.INVOKESTATIC, GLUE, "modelData", LOOKUP_DESC, false)));
        return true;
    }

    /** ChunkCachePryzma is the getter models receive while meshing: answer from its region. */
    static boolean patchPryzmaCache(ClassNode node) {
        String regionDesc = "L" + REGION + ";";
        if (hasMethod(node, GET_MODEL_DATA, GET_MODEL_DATA_DESC)
                || node.fields.stream().noneMatch(f -> "chunkCache".equals(f.name) && regionDesc.equals(f.desc))) {
            return false;
        }
        node.methods.add(modelDataGetter(
                new FieldInsnNode(Opcodes.GETFIELD, PRYZMA_CACHE, "chunkCache", regionDesc),
                new MethodInsnNode(Opcodes.INVOKEVIRTUAL, REGION, GET_MODEL_DATA, GET_MODEL_DATA_DESC, false)));
        return true;
    }

    /** {@code getLightEmission(level, pos) == 0 && model.useAmbientOcclusion(state, renderType)} -> NeoForge's TriState decision. */
    static boolean patchAmbientOcclusion(ClassNode node) {
        boolean changed = false;
        for (MethodNode m : node.methods) {
            int data = paramSlot(m, MODEL_DATA);
            for (AbstractInsnNode insn : m.instructions.toArray()) {
                if (!(insn instanceof MethodInsnNode ao && BAKED_MODEL.equals(ao.owner)
                        && "useAmbientOcclusion".equals(ao.name) && FORGE_AO_DESC.equals(ao.desc))) {
                    continue;
                }
                AbstractInsnNode type = prev(ao);
                AbstractInsnNode state = prev(type);
                AbstractInsnNode model = prev(state);
                AbstractInsnNode gate = prev(model);
                AbstractInsnNode emission = prev(gate);
                AbstractInsnNode pos = prev(emission);
                AbstractInsnNode level = prev(pos);
                AbstractInsnNode litState = prev(level);
                if (data < 0 || !isLoad(type) || !isLoad(state) || !isLoad(model) || gate == null
                        || gate.getOpcode() != Opcodes.IFNE || !isCall(emission, null, "getLightEmission")
                        || !isLoad(pos) || !isLoad(level) || !isLoad(litState) || !jumpFree(m, litState, ao)) {
                    LOGGER.warn("{}.{}: unrecognised Forge ambient-occlusion gate left in place", node.name, m.name);
                    continue;
                }
                InsnList args = new InsnList();
                args.add(new VarInsnNode(Opcodes.ALOAD, data));
                args.add(new VarInsnNode(Opcodes.ALOAD, ((VarInsnNode) level).var));
                args.add(new VarInsnNode(Opcodes.ALOAD, ((VarInsnNode) pos).var));
                m.instructions.insertBefore(ao, args);
                m.instructions.set(ao, new MethodInsnNode(Opcodes.INVOKESTATIC, GLUE, "useAmbientOcclusion", AO_DESC, false));
                removeAll(m, litState, level, pos, emission, gate);
                m.maxStack += 3;
                changed = true;
            }
        }
        return changed;
    }

    private static MethodNode modelDataGetter(FieldInsnNode source, MethodInsnNode lookup) {
        MethodNode get = new MethodNode(Opcodes.ACC_PUBLIC, GET_MODEL_DATA, GET_MODEL_DATA_DESC, null, null);
        get.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        get.instructions.add(source);
        get.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        get.instructions.add(lookup);
        get.instructions.add(new InsnNode(Opcodes.ARETURN));
        get.maxStack = 2;
        get.maxLocals = 2;
        return get;
    }

    /** Slot of the first parameter with {@code desc}, or -1 if absent or reassigned in the body. */
    private static int paramSlot(MethodNode m, String desc) {
        int slot = (m.access & Opcodes.ACC_STATIC) != 0 ? 0 : 1;
        for (Type arg : Type.getArgumentTypes(m.desc)) {
            if (desc.equals(arg.getDescriptor())) {
                return writes(m, slot) ? -1 : slot;
            }
            slot += arg.getSize();
        }
        return -1;
    }

    private static boolean writes(MethodNode m, int slot) {
        for (AbstractInsnNode insn : m.instructions) {
            if (insn instanceof VarInsnNode v && v.var == slot
                    && v.getOpcode() >= Opcodes.ISTORE && v.getOpcode() <= Opcodes.ASTORE) {
                return true;
            }
        }
        return false;
    }

    private static boolean touches(MethodNode m, int opcode, String owner, String field) {
        for (AbstractInsnNode insn : m.instructions) {
            if (insn instanceof FieldInsnNode f && f.getOpcode() == opcode && owner.equals(f.owner) && field.equals(f.name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasMethod(ClassNode node, String name, String desc) {
        return node.methods.stream().anyMatch(m -> name.equals(m.name) && desc.equals(m.desc));
    }

    private static boolean isLoad(AbstractInsnNode insn) {
        return insn != null && insn.getOpcode() == Opcodes.ALOAD;
    }

    private static boolean isCall(AbstractInsnNode insn, String owner, String name) {
        return insn instanceof MethodInsnNode call && name.equals(call.name) && (owner == null || owner.equals(call.owner));
    }

    private static AbstractInsnNode prev(AbstractInsnNode insn) {
        AbstractInsnNode p = insn == null ? null : insn.getPrevious();
        while (p != null && p.getOpcode() < 0) {
            p = p.getPrevious();
        }
        return p;
    }

    /** No branch, switch or handler lands strictly between {@code from} and {@code to}. */
    private static boolean jumpFree(MethodNode m, AbstractInsnNode from, AbstractInsnNode to) {
        Set<LabelNode> targets = new HashSet<>();
        for (AbstractInsnNode insn : m.instructions) {
            if (insn instanceof JumpInsnNode jump) {
                targets.add(jump.label);
            } else if (insn instanceof TableSwitchInsnNode table) {
                targets.add(table.dflt);
                targets.addAll(table.labels);
            } else if (insn instanceof LookupSwitchInsnNode lookup) {
                targets.add(lookup.dflt);
                targets.addAll(lookup.labels);
            }
        }
        for (TryCatchBlockNode block : m.tryCatchBlocks) {
            targets.add(block.start);
            targets.add(block.end);
            targets.add(block.handler);
        }
        for (AbstractInsnNode p = from.getNext(); p != null && p != to; p = p.getNext()) {
            if (targets.contains(p)) {
                return false;
            }
        }
        return true;
    }

    private static void removeAll(MethodNode m, AbstractInsnNode... dead) {
        for (AbstractInsnNode insn : dead) {
            m.instructions.remove(insn);
        }
    }

    @Override
    public ClassNode transform(ClassNode input, ITransformerVotingContext context) {
        if (inject(input)) {
            LOGGER.info("Rewired {} onto NeoForge model data / ambient occlusion", input.name);
        }
        return input;
    }

    @Override
    public TransformerVoteResult castVote(ITransformerVotingContext context) {
        return TransformerVoteResult.YES;
    }

    @Override
    public Set<Target<ClassNode>> targets() {
        return Set.of(
                Target.targetClass("net.minecraft.client.renderer.chunk.SectionCompiler"),
                Target.targetClass("net.minecraft.client.renderer.chunk.RenderRegionCache"),
                Target.targetClass("net.minecraft.client.renderer.chunk.RenderChunkRegion"),
                Target.targetClass("net.pryzma.override.ChunkCachePryzma"),
                Target.targetClass("net.minecraft.client.renderer.block.ModelBlockRenderer"));
    }

    @Override
    public TargetType<ClassNode> getTargetType() {
        return TargetType.CLASS;
    }
}
