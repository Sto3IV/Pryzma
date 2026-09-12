package net.pryzma.neoforge;

/*
 * Celian: Is this thread-safe?
 * Ranni: Nothing here is safe, but it works.
 * This is a terrible hack, but we're shipping tomorrow.
 */

import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
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
 * Rewires OptiFine's {@link net.minecraft.client.renderer.block.LiquidBlockRenderer} to NeoForge 21.1 contracts:
 * <ul>
 *   <li>Guards line 408 {@code sprites[2]} access against {@link ArrayIndexOutOfBoundsException} when fluid sprites
 *       array only has length 2.</li>
 *   <li>Binds sprite acquisition to {@link net.neoforged.neoforge.client.textures.FluidSpriteCache#getFluidSprites}
 *       instead of the missing Forge {@code ForgeHooksClient.getFluidSprites}.</li>
 *   <li>Hooks {@code setupSprites()} to reload {@code FluidSpriteCache}.</li>
 *   <li>Injects the NeoForge 6-arg overload {@code shouldRenderFace(BlockAndTintGetter, BlockPos, FluidState, BlockState, Direction, BlockState)}.</li>
 *   <li>Injects {@code BlockRenderDispatcher.getLiquidBlockRenderer()}.</li>
 * </ul>
 */
public class LiquidBlockRendererTransformer implements ITransformer<ClassNode> {
    private static final Logger LOGGER = LoggerFactory.getLogger("Pryzma");

    static final String LIQUID_RENDERER = "net/minecraft/client/renderer/block/LiquidBlockRenderer";
    static final String BLOCK_DISPATCHER = "net/minecraft/client/renderer/block/BlockRenderDispatcher";
    static final String GLUE = "net/pryzma/util/NeoForgeMeshing";

    public static boolean inject(ClassNode node) {
        return switch (node.name) {
            case LIQUID_RENDERER -> patchLiquidRenderer(node);
            case BLOCK_DISPATCHER -> patchBlockDispatcher(node);
            default -> false;
        };
    }

    static boolean patchBlockDispatcher(ClassNode node) {
        for (MethodNode m : node.methods) {
            if ("getLiquidBlockRenderer".equals(m.name) && "()Lnet/minecraft/client/renderer/block/LiquidBlockRenderer;".equals(m.desc)) {
                return false;
            }
        }
        MethodNode m = new MethodNode(
                Opcodes.ACC_PUBLIC,
                "getLiquidBlockRenderer",
                "()Lnet/minecraft/client/renderer/block/LiquidBlockRenderer;",
                null,
                null);
        m.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        m.instructions.add(new FieldInsnNode(
                Opcodes.GETFIELD,
                BLOCK_DISPATCHER,
                "liquidBlockRenderer",
                "Lnet/minecraft/client/renderer/block/LiquidBlockRenderer;"));
        m.instructions.add(new InsnNode(Opcodes.ARETURN));
        m.maxStack = 1;
        m.maxLocals = 1;
        node.methods.add(m);
        return true;
    }

    static boolean patchLiquidRenderer(ClassNode node) {
        boolean changed = false;

        // 1. Inject NeoForge overload of shouldRenderFace if missing
        boolean hasNeoShouldRender = node.methods.stream().anyMatch(m ->
                "shouldRenderFace".equals(m.name)
                        && "(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/material/FluidState;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;Lnet/minecraft/world/level/block/state/BlockState;)Z".equals(m.desc));
        if (!hasNeoShouldRender) {
            MethodNode mn = new MethodNode(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                    "shouldRenderFace",
                    "(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/material/FluidState;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;Lnet/minecraft/world/level/block/state/BlockState;)Z",
                    null,
                    null);
            LabelNode falseLabel = new LabelNode();
            LabelNode endLabel = new LabelNode();

            // if (isFaceOccludedBySelf(level, pos, selfState, direction)) return false;
            mn.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0)); // level
            mn.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1)); // pos
            mn.instructions.add(new VarInsnNode(Opcodes.ALOAD, 3)); // selfState
            mn.instructions.add(new VarInsnNode(Opcodes.ALOAD, 4)); // direction
            mn.instructions.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    LIQUID_RENDERER,
                    "isFaceOccludedBySelf",
                    "(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;)Z",
                    false));
            mn.instructions.add(new JumpInsnNode(Opcodes.IFNE, falseLabel));

            // if (otherState.shouldHideAdjacentFluidFace(direction.getOpposite(), fluidState)) return false;
            mn.instructions.add(new VarInsnNode(Opcodes.ALOAD, 5)); // otherState
            mn.instructions.add(new VarInsnNode(Opcodes.ALOAD, 4)); // direction
            mn.instructions.add(new MethodInsnNode(
                    Opcodes.INVOKEVIRTUAL,
                    "net/minecraft/core/Direction",
                    "getOpposite",
                    "()Lnet/minecraft/core/Direction;",
                    false));
            mn.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2)); // fluidState
            mn.instructions.add(new MethodInsnNode(
                    Opcodes.INVOKEVIRTUAL,
                    "net/minecraft/world/level/block/state/BlockState",
                    "shouldHideAdjacentFluidFace",
                    "(Lnet/minecraft/core/Direction;Lnet/minecraft/world/level/material/FluidState;)Z",
                    false));
            mn.instructions.add(new JumpInsnNode(Opcodes.IFNE, falseLabel));

            mn.instructions.add(new InsnNode(Opcodes.ICONST_1));
            mn.instructions.add(new JumpInsnNode(Opcodes.GOTO, endLabel));
            mn.instructions.add(falseLabel);
            mn.instructions.add(new InsnNode(Opcodes.ICONST_0));
            mn.instructions.add(endLabel);
            mn.instructions.add(new InsnNode(Opcodes.IRETURN));
            mn.maxStack = 4;
            mn.maxLocals = 6;
            node.methods.add(mn);
            changed = true;
        }

        for (MethodNode m : node.methods) {
            // 2. Hook setupSprites to reload FluidSpriteCache
            if ("setupSprites".equals(m.name) && "()V".equals(m.desc)) {
                boolean alreadyReloads = false;
                for (AbstractInsnNode insn : m.instructions.toArray()) {
                    if (insn instanceof MethodInsnNode call && GLUE.equals(call.owner) && "reloadFluidSprites".equals(call.name)) {
                        alreadyReloads = true;
                        break;
                    }
                }
                if (!alreadyReloads) {
                    for (AbstractInsnNode insn : m.instructions.toArray()) {
                        if (insn.getOpcode() == Opcodes.RETURN) {
                            m.instructions.insertBefore(insn, new MethodInsnNode(
                                    Opcodes.INVOKESTATIC, GLUE, "reloadFluidSprites", "()V", false));
                            changed = true;
                            break;
                        }
                    }
                }
            }

            // 3. Patch tesselate method
            if ("tesselate".equals(m.name)) {
                // 3a. Replace ForgeHooksClient_getFluidSprites block with NeoForgeMeshing.getFluidSprites
                for (AbstractInsnNode insn : m.instructions.toArray()) {
                    if (insn instanceof FieldInsnNode field && "net/pryzma/reflect/Reflector".equals(field.owner)
                            && "ForgeHooksClient_getFluidSprites".equals(field.name)
                            && field.getOpcode() == Opcodes.GETSTATIC) {
                        JumpInsnNode ifeq = null;
                        for (AbstractInsnNode p = insn.getNext(); p != null; p = p.getNext()) {
                            if (p.getOpcode() == Opcodes.IFEQ && p instanceof JumpInsnNode j) {
                                ifeq = j;
                                break;
                            }
                        }
                        if (ifeq != null && ifeq.label != null) {
                            LabelNode targetLabel = ifeq.label;
                            AbstractInsnNode cur = insn;
                            while (cur != null && cur != targetLabel) {
                                AbstractInsnNode next = cur.getNext();
                                m.instructions.remove(cur);
                                cur = next;
                            }
                            InsnList lookup = new InsnList();
                            lookup.add(new VarInsnNode(Opcodes.ALOAD, 1)); // level
                            lookup.add(new VarInsnNode(Opcodes.ALOAD, 2)); // pos
                            lookup.add(new VarInsnNode(Opcodes.ALOAD, 5)); // fluidState
                            lookup.add(new VarInsnNode(Opcodes.ALOAD, 8)); // fallback sprites
                            lookup.add(new MethodInsnNode(
                                    Opcodes.INVOKESTATIC,
                                    GLUE,
                                    "getFluidSprites",
                                    "(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/material/FluidState;[Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;)[Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;",
                                    false));
                            lookup.add(new VarInsnNode(Opcodes.ASTORE, 8));
                            m.instructions.insertBefore(targetLabel, lookup);
                            changed = true;
                            break;
                        }
                    }
                }

                // 3b. Guard line 408: sprites.length > 2 check before sprites[2]
                for (AbstractInsnNode insn : m.instructions.toArray()) {
                    if (insn instanceof VarInsnNode aload8 && aload8.getOpcode() == Opcodes.ALOAD && aload8.var == 8) {
                        AbstractInsnNode next1 = aload8.getNext();
                        if (next1 != null && next1.getOpcode() == Opcodes.ICONST_2) {
                            AbstractInsnNode next2 = next1.getNext();
                            if (next2 != null && next2.getOpcode() == Opcodes.AALOAD) {
                                AbstractInsnNode next3 = next2.getNext();
                                if (next3 instanceof JumpInsnNode ifnull && ifnull.getOpcode() == Opcodes.IFNULL) {
                                    // Check if guard not already present
                                    AbstractInsnNode prev = aload8.getPrevious();
                                    boolean alreadyGuarded = prev instanceof JumpInsnNode j && j.getOpcode() == Opcodes.IF_ICMPLE;
                                    if (!alreadyGuarded) {
                                        LabelNode falseTarget = ifnull.label;
                                        InsnList guard = new InsnList();
                                        guard.add(new VarInsnNode(Opcodes.ALOAD, 8));
                                        guard.add(new InsnNode(Opcodes.ARRAYLENGTH));
                                        guard.add(new InsnNode(Opcodes.ICONST_2));
                                        guard.add(new JumpInsnNode(Opcodes.IF_ICMPLE, falseTarget));
                                        m.instructions.insertBefore(aload8, guard);
                                        m.maxStack = Math.max(m.maxStack, 3);
                                        changed = true;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return changed;
    }

    @Override
    public ClassNode transform(ClassNode input, ITransformerVotingContext context) {
        if (inject(input)) {
            LOGGER.info("Rewired {} onto NeoForge fluid rendering / sprite contracts", input.name);
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
                Target.targetClass("net.minecraft.client.renderer.block.LiquidBlockRenderer"),
                Target.targetClass("net.minecraft.client.renderer.block.BlockRenderDispatcher"));
    }

    @Override
    public TargetType<ClassNode> getTargetType() {
        return TargetType.CLASS;
    }
}
