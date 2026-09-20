package net.pryzma.neoforge;

/*
 * Ranni: FUCK FUCK FUCK. I mean... everything is fine, Celian.
 */

import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
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
 * Gives chunk meshing its own worker pool. {@code LevelRenderer.allChanged} hands
 * {@code Util.backgroundExecutor()} to {@code new SectionRenderDispatcher(...)}, so meshing
 * shares the pool that also carries chunk IO, structure loading and mod work. Only that
 * argument is redirected to {@code net.pryzma.util.PryzmaChunkExecutor.getExecutor()}; every
 * other background-pool user is left alone.
 *
 * <p>The replacement is descriptor-identical and stack-neutral, so the meshing pipeline
 * (mesher, vertex consumers, baked models) is untouched.
 */
public class LevelRendererTransformer implements ITransformer<ClassNode> {
    private static final Logger LOGGER = LoggerFactory.getLogger("Pryzma");
    private static final String TARGET = "net.minecraft.client.renderer.LevelRenderer";

    static final String UTIL = "net/minecraft/Util";
    static final String BACKGROUND_EXECUTOR = "backgroundExecutor";
    static final String POOL = "net/pryzma/util/PryzmaChunkExecutor";
    static final String GET_EXECUTOR = "getExecutor";
    static final String EXECUTOR_DESC = "()Ljava/util/concurrent/ExecutorService;";
    static final String DISPATCHER = "net/minecraft/client/renderer/chunk/SectionRenderDispatcher";
    static final String EXECUTOR_ARG = "Ljava/util/concurrent/Executor;";

    static final String DISPATCH_TARGET = "net/pryzma/reflect/ReflectorAdapter";
    static final String DISPATCH_METHOD = "dispatchRenderStageS";

    // --- NeoForge public API absent from OptiFine's pre-compiled LevelRenderer ---
    static final String OWNER = "net/minecraft/client/renderer/LevelRenderer";
    static final String OBJECT_ARRAY_LIST = "it/unimi/dsi/fastutil/objects/ObjectArrayList";
    static final String RENDER_SECTION = "net/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection";
    static final String COMPILED_SECTION = "net/minecraft/client/renderer/chunk/SectionRenderDispatcher$CompiledSection";

    static final String VISIBLE_SECTIONS = "visibleSections";
    static final String VISIBLE_SECTIONS_DESC = "Lit/unimi/dsi/fastutil/objects/ObjectArrayList;";
    static final String GLOBAL_BLOCK_ENTITIES = "globalBlockEntities";
    static final String GLOBAL_BLOCK_ENTITIES_DESC = "Ljava/util/Set;";

    static final String ITERATE_BLOCK_ENTITIES = "iterateVisibleBlockEntities";
    static final String ITERATE_BLOCK_ENTITIES_DESC = "(Ljava/util/function/Consumer;)V";
    static final String ITERATE_BLOCK_ENTITIES_SIG =
            "(Ljava/util/function/Consumer<Lnet/minecraft/world/level/block/entity/BlockEntity;>;)V";

    static final String REQUEST_OUTLINE = "requestOutlineEffect";
    static final String REQUEST_OUTLINE_DESC = "()V";
    static final String OUTLINE_REQUESTED = "outlineEffectRequested";
    static final String OUTLINE_REQUESTED_DESC = "Z";

    static final String RENDER_LEVEL = "renderLevel";
    static final String RENDER_LEVEL_DESC = "(Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;"
            + "Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;"
            + "Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V";
    static final String OUTLINE_BUFFER_SOURCE = "net/minecraft/client/renderer/OutlineBufferSource";
    static final String END_OUTLINE_BATCH = "endOutlineBatch";
    static final String ENTITY_EFFECT = "entityEffect";
    static final String SHOULD_SHOW_OUTLINES = "shouldShowEntityOutlines";

    public static boolean inject(ClassNode node) {
        boolean changed = false;
        int dispatchRedirects = 0;
        for (MethodNode m : node.methods) {
            for (AbstractInsnNode insn : m.instructions.toArray()) {
                if (isBackgroundExecutor(insn)) {
                    if (!allocatedBefore(insn) || !constructedAfter(insn)) {
                        LOGGER.warn("{}.{}: Util.backgroundExecutor() outside SectionRenderDispatcher left in place",
                                node.name, m.name);
                        continue;
                    }
                    m.instructions.set(insn,
                            new MethodInsnNode(Opcodes.INVOKESTATIC, POOL, GET_EXECUTOR, EXECUTOR_DESC, false));
                    changed = true;
                } else if (isDispatchRenderStageS(insn)) {
                    MethodInsnNode call = (MethodInsnNode) insn;
                    call.owner = DISPATCH_TARGET;
                    dispatchRedirects++;
                    changed = true;
                }
            }
        }
        if (dispatchRedirects > 0) {
            LOGGER.info("Redirected {} dispatchRenderStageS calls in LevelRenderer to {}", dispatchRedirects, DISPATCH_TARGET);
        }
        return changed;
    }

    private static boolean isDispatchRenderStageS(AbstractInsnNode insn) {
        return insn instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESTATIC
                && ("net/optifine/reflect/ReflectorForge".equals(call.owner) || "net/pryzma/reflect/ReflectorForge".equals(call.owner))
                && DISPATCH_METHOD.equals(call.name);
    }

    private static boolean isBackgroundExecutor(AbstractInsnNode insn) {
        return insn instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESTATIC
                && UTIL.equals(call.owner) && BACKGROUND_EXECUTOR.equals(call.name) && EXECUTOR_DESC.equals(call.desc);
    }

    /** A {@code new SectionRenderDispatcher} precedes {@code call} with its {@code <init>} still pending. */
    private static boolean allocatedBefore(AbstractInsnNode call) {
        for (AbstractInsnNode p = call.getPrevious(); p != null; p = p.getPrevious()) {
            if (isDispatcherInit(p)) {
                return false;
            }
            if (isDispatcherAlloc(p)) {
                return true;
            }
        }
        return false;
    }

    /** That pending {@code <init>} is the next dispatcher instruction after {@code call}. */
    private static boolean constructedAfter(AbstractInsnNode call) {
        for (AbstractInsnNode p = call.getNext(); p != null; p = p.getNext()) {
            if (isDispatcherAlloc(p)) {
                return false;
            }
            if (isDispatcherInit(p)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDispatcherAlloc(AbstractInsnNode insn) {
        return insn instanceof TypeInsnNode alloc && alloc.getOpcode() == Opcodes.NEW && DISPATCHER.equals(alloc.desc);
    }

    private static boolean isDispatcherInit(AbstractInsnNode insn) {
        return insn instanceof MethodInsnNode init && init.getOpcode() == Opcodes.INVOKESPECIAL
                && DISPATCHER.equals(init.owner) && "<init>".equals(init.name) && init.desc.contains(EXECUTOR_ARG);
    }

    /**
     * Restores the two public methods NeoForge patches into {@code LevelRenderer} and OptiFine's
     * pre-compiled copy does not carry. Without them any mod compiled against NeoForge's
     * {@code LevelRenderer} dies with {@code NoSuchMethodError} the first time it renders;
     * NeoForge's own {@code BlockEntityRenderBoundsDebugRenderer} is one such caller.
     *
     * <p>Both are rebuilt to NeoForge's semantics rather than stubbed: the iteration walks the same
     * two collections in the same order under the same monitor, and the outline request is honoured
     * by the render loop instead of being written to a dead field.
     */
    public static boolean injectNeoForgeApi(ClassNode node) {
        boolean iterate = addIterateVisibleBlockEntities(node);
        boolean outline = addRequestOutlineEffect(node);
        boolean honored = honorOutlineRequest(node);
        if (iterate) {
            LOGGER.info("Restored LevelRenderer.{}{}", ITERATE_BLOCK_ENTITIES, ITERATE_BLOCK_ENTITIES_DESC);
        }
        if (outline) {
            LOGGER.info("Restored LevelRenderer.{}{}", REQUEST_OUTLINE, REQUEST_OUTLINE_DESC);
        }
        if (honored) {
            LOGGER.info("LevelRenderer.renderLevel now honours {}", OUTLINE_REQUESTED);
        }
        return iterate || outline || honored;
    }

    /**
     * <pre>
     * public void iterateVisibleBlockEntities(Consumer&lt;BlockEntity&gt; consumer) {
     *     for (RenderSection s : this.visibleSections) {
     *         s.getCompiled().getRenderableBlockEntities().forEach(consumer);
     *     }
     *     synchronized (this.globalBlockEntities) {
     *         this.globalBlockEntities.forEach(consumer);
     *     }
     * }
     * </pre>
     */
    static boolean addIterateVisibleBlockEntities(ClassNode node) {
        if (hasMethod(node, ITERATE_BLOCK_ENTITIES, ITERATE_BLOCK_ENTITIES_DESC)) {
            return false;
        }
        if (!hasField(node, VISIBLE_SECTIONS, VISIBLE_SECTIONS_DESC)
                || !hasField(node, GLOBAL_BLOCK_ENTITIES, GLOBAL_BLOCK_ENTITIES_DESC)) {
            LOGGER.warn("{} lacks {}/{}; {} not restored",
                    node.name, VISIBLE_SECTIONS, GLOBAL_BLOCK_ENTITIES, ITERATE_BLOCK_ENTITIES);
            return false;
        }

        MethodNode m = new MethodNode(Opcodes.ACC_PUBLIC, ITERATE_BLOCK_ENTITIES, ITERATE_BLOCK_ENTITIES_DESC,
                ITERATE_BLOCK_ENTITIES_SIG, null);
        LabelNode loop = new LabelNode();
        LabelNode global = new LabelNode();
        LabelNode bodyStart = new LabelNode();
        LabelNode bodyEnd = new LabelNode();
        LabelNode handler = new LabelNode();
        LabelNode handlerEnd = new LabelNode();
        LabelNode end = new LabelNode();
        InsnList in = m.instructions;

        // slot 2: section iterator
        in.add(new VarInsnNode(Opcodes.ALOAD, 0));
        in.add(new FieldInsnNode(Opcodes.GETFIELD, OWNER, VISIBLE_SECTIONS, VISIBLE_SECTIONS_DESC));
        in.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, OBJECT_ARRAY_LIST, "iterator",
                "()Lit/unimi/dsi/fastutil/objects/ObjectListIterator;", false));
        in.add(new VarInsnNode(Opcodes.ASTORE, 2));
        in.add(loop);
        in.add(new VarInsnNode(Opcodes.ALOAD, 2));
        in.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "java/util/Iterator", "hasNext", "()Z", true));
        in.add(new JumpInsnNode(Opcodes.IFEQ, global));
        in.add(new VarInsnNode(Opcodes.ALOAD, 2));
        in.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "java/util/Iterator", "next", "()Ljava/lang/Object;", true));
        in.add(new TypeInsnNode(Opcodes.CHECKCAST, RENDER_SECTION));
        in.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, RENDER_SECTION, "getCompiled",
                "()L" + COMPILED_SECTION + ";", false));
        in.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, COMPILED_SECTION, "getRenderableBlockEntities",
                "()Ljava/util/List;", false));
        in.add(new VarInsnNode(Opcodes.ALOAD, 1));
        in.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "java/util/List", "forEach",
                "(Ljava/util/function/Consumer;)V", true));
        in.add(new JumpInsnNode(Opcodes.GOTO, loop));

        // slot 3: monitor, slot 4: pending throwable
        in.add(global);
        in.add(new VarInsnNode(Opcodes.ALOAD, 0));
        in.add(new FieldInsnNode(Opcodes.GETFIELD, OWNER, GLOBAL_BLOCK_ENTITIES, GLOBAL_BLOCK_ENTITIES_DESC));
        in.add(new InsnNode(Opcodes.DUP));
        in.add(new VarInsnNode(Opcodes.ASTORE, 3));
        in.add(new InsnNode(Opcodes.MONITORENTER));
        in.add(bodyStart);
        in.add(new VarInsnNode(Opcodes.ALOAD, 0));
        in.add(new FieldInsnNode(Opcodes.GETFIELD, OWNER, GLOBAL_BLOCK_ENTITIES, GLOBAL_BLOCK_ENTITIES_DESC));
        in.add(new VarInsnNode(Opcodes.ALOAD, 1));
        in.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "java/util/Set", "forEach",
                "(Ljava/util/function/Consumer;)V", true));
        in.add(new VarInsnNode(Opcodes.ALOAD, 3));
        in.add(new InsnNode(Opcodes.MONITOREXIT));
        in.add(bodyEnd);
        in.add(new JumpInsnNode(Opcodes.GOTO, end));
        in.add(handler);
        in.add(new VarInsnNode(Opcodes.ASTORE, 4));
        in.add(new VarInsnNode(Opcodes.ALOAD, 3));
        in.add(new InsnNode(Opcodes.MONITOREXIT));
        in.add(handlerEnd);
        in.add(new VarInsnNode(Opcodes.ALOAD, 4));
        in.add(new InsnNode(Opcodes.ATHROW));
        in.add(end);
        in.add(new InsnNode(Opcodes.RETURN));

        m.tryCatchBlocks.add(new TryCatchBlockNode(bodyStart, bodyEnd, handler, null));
        m.tryCatchBlocks.add(new TryCatchBlockNode(handler, handlerEnd, handler, null));
        m.maxStack = 2;
        m.maxLocals = 5;
        node.methods.add(m);
        return true;
    }

    /** {@code public void requestOutlineEffect() { this.outlineEffectRequested = true; }} */
    static boolean addRequestOutlineEffect(ClassNode node) {
        boolean changed = false;
        if (!hasField(node, OUTLINE_REQUESTED, OUTLINE_REQUESTED_DESC)) {
            node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, OUTLINE_REQUESTED, OUTLINE_REQUESTED_DESC, null, null));
            changed = true;
        }
        if (hasMethod(node, REQUEST_OUTLINE, REQUEST_OUTLINE_DESC)) {
            return changed;
        }
        MethodNode m = new MethodNode(Opcodes.ACC_PUBLIC, REQUEST_OUTLINE, REQUEST_OUTLINE_DESC, null, null);
        m.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        m.instructions.add(new InsnNode(Opcodes.ICONST_1));
        m.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, OWNER, OUTLINE_REQUESTED, OUTLINE_REQUESTED_DESC));
        m.instructions.add(new InsnNode(Opcodes.RETURN));
        m.maxStack = 2;
        m.maxLocals = 1;
        node.methods.add(m);
        return true;
    }

    /**
     * Makes the flag do something. NeoForge's {@code renderLevel} folds the request into the local
     * that gates the entity-outline post chain:
     * {@code if (outlineEffectRequested) { flag |= shouldShowEntityOutlines(); outlineEffectRequested = false; }}
     *
     * <p>OptiFine keeps the same shape - {@code endOutlineBatch()} then a boolean local guarding
     * {@code this.entityEffect.process(..)} - so the local is located by that pair rather than by a
     * hardcoded slot. If the pattern is gone the request stays inert and the method stays callable;
     * nothing is written blind.
     */
    static boolean honorOutlineRequest(ClassNode node) {
        MethodNode render = findMethod(node, RENDER_LEVEL, RENDER_LEVEL_DESC);
        if (render == null || !hasField(node, OUTLINE_REQUESTED, OUTLINE_REQUESTED_DESC)) {
            return false;
        }
        if (reads(render, OUTLINE_REQUESTED)) {
            return false;
        }
        AbstractInsnNode endBatch = null;
        for (AbstractInsnNode insn : render.instructions) {
            if (insn instanceof MethodInsnNode call && OUTLINE_BUFFER_SOURCE.equals(call.owner)
                    && END_OUTLINE_BATCH.equals(call.name)) {
                endBatch = insn;
                break;
            }
        }
        if (endBatch == null) {
            LOGGER.warn("{}.renderLevel has no {}.{}; {} stays inert",
                    node.name, OUTLINE_BUFFER_SOURCE, END_OUTLINE_BATCH, OUTLINE_REQUESTED);
            return false;
        }
        VarInsnNode flag = outlineFlag(endBatch);
        if (flag == null) {
            LOGGER.warn("{}.renderLevel: entity-outline flag not identifiable after {}; {} stays inert",
                    node.name, END_OUTLINE_BATCH, OUTLINE_REQUESTED);
            return false;
        }

        LabelNode clear = new LabelNode();
        LabelNode skip = new LabelNode();
        InsnList honor = new InsnList();
        honor.add(new VarInsnNode(Opcodes.ALOAD, 0));
        honor.add(new FieldInsnNode(Opcodes.GETFIELD, OWNER, OUTLINE_REQUESTED, OUTLINE_REQUESTED_DESC));
        honor.add(new JumpInsnNode(Opcodes.IFEQ, skip));
        honor.add(new VarInsnNode(Opcodes.ALOAD, 0));
        honor.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, OWNER, SHOULD_SHOW_OUTLINES, "()Z", false));
        honor.add(new JumpInsnNode(Opcodes.IFEQ, clear));
        honor.add(new InsnNode(Opcodes.ICONST_1));
        honor.add(new VarInsnNode(Opcodes.ISTORE, flag.var));
        honor.add(clear);
        honor.add(new VarInsnNode(Opcodes.ALOAD, 0));
        honor.add(new InsnNode(Opcodes.ICONST_0));
        honor.add(new FieldInsnNode(Opcodes.PUTFIELD, OWNER, OUTLINE_REQUESTED, OUTLINE_REQUESTED_DESC));
        honor.add(skip);
        render.instructions.insertBefore(flag, honor);
        render.maxStack = Math.max(render.maxStack, 2);
        return true;
    }

    /** First {@code ILOAD n; IFEQ L} after {@code from} whose not-taken branch runs the entity-outline chain. */
    private static VarInsnNode outlineFlag(AbstractInsnNode from) {
        for (AbstractInsnNode p = from.getNext(); p != null; p = p.getNext()) {
            if (!(p instanceof VarInsnNode load) || load.getOpcode() != Opcodes.ILOAD) {
                continue;
            }
            AbstractInsnNode next = nextReal(load);
            if (next instanceof JumpInsnNode jump && jump.getOpcode() == Opcodes.IFEQ && guardsEntityEffect(jump)) {
                return load;
            }
        }
        return null;
    }

    private static AbstractInsnNode nextReal(AbstractInsnNode insn) {
        AbstractInsnNode p = insn.getNext();
        while (p != null && p.getOpcode() < 0) {
            p = p.getNext();
        }
        return p;
    }

    private static boolean guardsEntityEffect(JumpInsnNode jump) {
        for (AbstractInsnNode p = jump.getNext(); p != null && p != jump.label; p = p.getNext()) {
            if (p instanceof FieldInsnNode f && f.getOpcode() == Opcodes.GETFIELD
                    && OWNER.equals(f.owner) && ENTITY_EFFECT.equals(f.name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean reads(MethodNode m, String field) {
        for (AbstractInsnNode insn : m.instructions) {
            if (insn instanceof FieldInsnNode f && OWNER.equals(f.owner) && field.equals(f.name)) {
                return true;
            }
        }
        return false;
    }

    private static MethodNode findMethod(ClassNode node, String name, String desc) {
        for (MethodNode m : node.methods) {
            if (name.equals(m.name) && desc.equals(m.desc)) {
                return m;
            }
        }
        return null;
    }

    private static boolean hasMethod(ClassNode node, String name, String desc) {
        return findMethod(node, name, desc) != null;
    }

    private static boolean hasField(ClassNode node, String name, String desc) {
        for (FieldNode f : node.fields) {
            if (name.equals(f.name) && desc.equals(f.desc)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ClassNode transform(ClassNode input, ITransformerVotingContext context) {
        if (inject(input)) {
            LOGGER.info("Chunk meshing moved off Util.backgroundExecutor onto the Pryzma worker pool");
        }
        injectNeoForgeApi(input);
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
