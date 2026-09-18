package net.pryzma.neoforge;

/*
 * Ranni: FUCK FUCK FUCK. I mean... everything is fine, Celian.
 */

import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
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

        for (MethodNode m : node.methods) {
            if ("setLevel".equals(m.name) && "(Lnet/minecraft/client/multiplayer/ClientLevel;)V".equals(m.desc)) {
                if (alreadyCalls(m, "net/pryzma/lod/render/LodWorldRenderer", "reset")) {
                    continue;
                }
                InsnList hook = new InsnList();
                hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "net/pryzma/lod/render/LodWorldRenderer", "reset", "()V", false));
                m.instructions.insert(hook);
                changed = true;
                LOGGER.info("Injected LodWorldRenderer.reset into LevelRenderer.setLevel");
            }
        }

        boolean lodRender = injectLodRender(node);

        return changed || lodRender;
    }

    public static boolean injectLodRender(ClassNode node) {
        boolean changed = false;
        String targetMethod = "renderSectionLayer";
        String targetDesc = "(Lnet/minecraft/client/renderer/RenderType;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V";
        String lodRenderer = "net/pryzma/lod/render/LodWorldRenderer";

        for (MethodNode m : node.methods) {
            if (targetMethod.equals(m.name) && targetDesc.equals(m.desc)) {
                if (alreadyCalls(m, lodRenderer, "renderSectionLayer")) {
                    continue;
                }
                for (AbstractInsnNode insn : m.instructions.toArray()) {
                    if (insn instanceof MethodInsnNode mi
                            && "clear".equals(mi.name)
                            && "net/minecraft/client/renderer/ShaderInstance".equals(mi.owner)) {
                        AbstractInsnNode target = mi.getPrevious();
                        if (target != null) {
                            InsnList hook = new InsnList();
                            hook.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
                            hook.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 1));
                            hook.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.DLOAD, 2));
                            hook.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.DLOAD, 4));
                            hook.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.DLOAD, 6));
                            hook.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 8));
                            hook.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 9));
                            hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, lodRenderer, "renderSectionLayer",
                                    "(Lnet/minecraft/client/renderer/LevelRenderer;Lnet/minecraft/client/renderer/RenderType;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V", false));
                            m.instructions.insertBefore(target, hook);
                            m.maxStack = Math.max(m.maxStack, 10);
                            changed = true;
                            LOGGER.info("Injected LodWorldRenderer.renderSectionLayer into LevelRenderer.renderSectionLayer");
                            break;
                        }
                    }
                }
            }
        }
        return changed;
    }

    private static boolean alreadyCalls(MethodNode method, String owner, String name) {
        for (var insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof MethodInsnNode call && owner.equals(call.owner) && name.equals(call.name)) {
                return true;
            }
        }
        return false;
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

    @Override
    public ClassNode transform(ClassNode input, ITransformerVotingContext context) {
        if (inject(input)) {
            LOGGER.info("Chunk meshing moved off Util.backgroundExecutor onto the Pryzma worker pool");
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
