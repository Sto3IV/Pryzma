package net.pryzma.neoforge;

/*
 * Ranni: Handing over entity render layers to NeoForge.
 * Celian: What if a mod expects them to be gathered via ForgeEventFactoryClient?
 * Ranni: Then that mod belongs in a museum, not on 1.21.1.
 */

import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
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
 * Two GAME-layer patches on {@code EntityRenderDispatcher}:
 * <ul>
 *   <li>{@code onResourceManagerReload} — dispatch {@code EntityRenderersEvent.AddLayers} in place of
 *       the missing Forge {@code ForgeEventFactoryClient.onGatherLayers}.</li>
 *   <li>{@code render} HEAD — {@code EpicFightOutline.onRenderEntity(entity)} so Epic Fight's
 *       target outline recolor does not depend on Mixin LVT capture inside {@code renderLevel}.</li>
 * </ul>
 * Each site is skipped independently if already present; {@link #inject} is a no-op on a second call.
 */
public class EntityRenderDispatcherTransformer implements ITransformer<ClassNode> {
    private static final Logger LOGGER = LoggerFactory.getLogger("Pryzma");
    public static final String TARGET = "net.minecraft.client.renderer.entity.EntityRenderDispatcher";

    static final String RENDER_DESC =
            "(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V";
    static final String OUTLINE_OWNER = "net/pryzma/compat/EpicFightOutline";
    static final String OUTLINE_NAME = "onRenderEntity";
    static final String OUTLINE_DESC = "(Lnet/minecraft/world/entity/Entity;)V";

    public static boolean inject(ClassNode node) {
        boolean changed = false;
        changed |= injectAddLayers(node);
        changed |= injectOutlineHighlight(node);
        return changed;
    }

    static boolean injectAddLayers(ClassNode node) {
        for (MethodNode m : node.methods) {
            if ("onResourceManagerReload".equals(m.name)
                    && "(Lnet/minecraft/server/packs/resources/ResourceManager;)V".equals(m.desc)) {

                for (AbstractInsnNode insn : m.instructions.toArray()) {
                    if (insn instanceof TypeInsnNode tin
                            && "net/neoforged/neoforge/client/event/EntityRenderersEvent$AddLayers".equals(tin.desc)) {
                        return false;
                    }
                }

                String renderersField = "renderers";
                String playerRenderersField = "playerRenderers";
                for (AbstractInsnNode insn : m.instructions.toArray()) {
                    if (insn.getOpcode() == Opcodes.PUTFIELD && insn instanceof FieldInsnNode fin) {
                        if ("Ljava/util/Map;".equals(fin.desc)) {
                            if ("renderers".equals(fin.name) || fin.name.contains("renderers")) {
                                renderersField = fin.name;
                            } else if ("playerRenderers".equals(fin.name) || fin.name.contains("playerRenderers")) {
                                playerRenderersField = fin.name;
                            }
                        }
                    }
                }

                AbstractInsnNode legacyStart = null;
                for (AbstractInsnNode insn : m.instructions.toArray()) {
                    if (insn instanceof FieldInsnNode fin
                            && fin.getOpcode() == Opcodes.GETSTATIC
                            && "ForgeEventFactoryClient_onGatherLayers".equals(fin.name)) {
                        legacyStart = insn;
                        break;
                    }
                }

                AbstractInsnNode returnInsn = null;
                for (AbstractInsnNode insn : m.instructions.toArray()) {
                    if (insn.getOpcode() == Opcodes.RETURN) {
                        returnInsn = insn;
                        break;
                    }
                }

                if (returnInsn == null) {
                    return false;
                }

                if (legacyStart != null) {
                    AbstractInsnNode cur = legacyStart;
                    while (cur != null && cur != returnInsn) {
                        AbstractInsnNode next = cur.getNext();
                        m.instructions.remove(cur);
                        cur = next;
                    }
                }

                InsnList eventList = new InsnList();
                eventList.add(new TypeInsnNode(Opcodes.NEW, "net/neoforged/neoforge/client/event/EntityRenderersEvent$AddLayers"));
                eventList.add(new InsnNode(Opcodes.DUP));
                eventList.add(new VarInsnNode(Opcodes.ALOAD, 0));
                eventList.add(new FieldInsnNode(Opcodes.GETFIELD, node.name, renderersField, "Ljava/util/Map;"));
                eventList.add(new VarInsnNode(Opcodes.ALOAD, 0));
                eventList.add(new FieldInsnNode(Opcodes.GETFIELD, node.name, playerRenderersField, "Ljava/util/Map;"));
                eventList.add(new VarInsnNode(Opcodes.ALOAD, 2));
                eventList.add(new MethodInsnNode(
                        Opcodes.INVOKESPECIAL,
                        "net/neoforged/neoforge/client/event/EntityRenderersEvent$AddLayers",
                        "<init>",
                        "(Ljava/util/Map;Ljava/util/Map;Lnet/minecraft/client/renderer/entity/EntityRendererProvider$Context;)V",
                        false));
                eventList.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "net/neoforged/fml/ModLoader",
                        "postEvent",
                        "(Lnet/neoforged/bus/api/Event;)V",
                        false));

                m.instructions.insertBefore(returnInsn, eventList);
                m.maxStack = Math.max(m.maxStack, 5);
                return true;
            }
        }
        return false;
    }

    static boolean injectOutlineHighlight(ClassNode node) {
        for (MethodNode m : node.methods) {
            if (!"render".equals(m.name) || !RENDER_DESC.equals(m.desc)) {
                continue;
            }
            if (hasOutlineCall(m)) {
                return false;
            }
            InsnList list = new InsnList();
            list.add(new VarInsnNode(Opcodes.ALOAD, 1));
            list.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC, OUTLINE_OWNER, OUTLINE_NAME, OUTLINE_DESC, false));
            m.instructions.insert(list);
            m.maxStack = Math.max(m.maxStack, 1);
            return true;
        }
        return false;
    }

    private static boolean hasOutlineCall(MethodNode m) {
        for (AbstractInsnNode insn : m.instructions) {
            if (insn instanceof MethodInsnNode min
                    && min.getOpcode() == Opcodes.INVOKESTATIC
                    && OUTLINE_OWNER.equals(min.owner)
                    && OUTLINE_NAME.equals(min.name)
                    && OUTLINE_DESC.equals(min.desc)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ClassNode transform(ClassNode input, ITransformerVotingContext context) {
        if (inject(input)) {
            LOGGER.info("EntityRenderDispatcherTransformer: injected AddLayers and/or EpicFight outline hook");
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
