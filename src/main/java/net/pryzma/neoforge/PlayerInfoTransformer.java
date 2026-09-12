package net.pryzma.neoforge;

/*
 * Ranni: sometimes I wonder if I should just quit and become a farmer.
 */

import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
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
 * Injects OptiFine / Pryzma cape resolution into {@link net.minecraft.client.multiplayer.PlayerInfo#getSkin()}
 * and {@link net.minecraft.client.player.AbstractClientPlayer#getSkin()}.
 * This ensures that cape rendering in vanilla NeoForge CapeLayer (which queries PlayerSkin directly)
 * seamlessly uses OptiFine capes with priority over Mojang capes, while preserving Mojang capes
 * when OptiFine capes are absent or disabled.
 */
public class PlayerInfoTransformer implements ITransformer<ClassNode> {
    private static final Logger LOGGER = LoggerFactory.getLogger("Pryzma");

    static final String TARGET_PLAYER_INFO = "net.minecraft.client.multiplayer.PlayerInfo";
    static final String TARGET_CLIENT_PLAYER = "net.minecraft.client.player.AbstractClientPlayer";

    static final String METHOD_NAME = "getSkin";
    static final String METHOD_DESC = "()Lnet/minecraft/client/resources/PlayerSkin;";

    static final String CAPE_UTILS = "net/pryzma/player/CapeUtils";
    static final String PATCH_METHOD = "patchPlayerSkin";
    static final String PATCH_DESC = "(Lcom/mojang/authlib/GameProfile;Lnet/minecraft/client/resources/PlayerSkin;)Lnet/minecraft/client/resources/PlayerSkin;";

    public static boolean inject(ClassNode node) {
        boolean changed = false;
        String name = node.name.replace('/', '.');

        if (TARGET_PLAYER_INFO.equals(name)) {
            for (MethodNode mn : node.methods) {
                if (METHOD_NAME.equals(mn.name) && METHOD_DESC.equals(mn.desc)) {
                    for (AbstractInsnNode insn : mn.instructions.toArray()) {
                        if (insn.getOpcode() == Opcodes.ARETURN) {
                            InsnList patch = new InsnList();
                            patch.add(new VarInsnNode(Opcodes.ALOAD, 0));
                            patch.add(new MethodInsnNode(
                                    Opcodes.INVOKEVIRTUAL,
                                    "net/minecraft/client/multiplayer/PlayerInfo",
                                    "getProfile",
                                    "()Lcom/mojang/authlib/GameProfile;",
                                    false));
                            patch.add(new InsnNode(Opcodes.SWAP));
                            patch.add(new MethodInsnNode(
                                    Opcodes.INVOKESTATIC,
                                    CAPE_UTILS,
                                    PATCH_METHOD,
                                    PATCH_DESC,
                                    false));
                            mn.instructions.insertBefore(insn, patch);
                            changed = true;
                        }
                    }
                    mn.maxStack = Math.max(mn.maxStack, 3);
                }
            }
        } else if (TARGET_CLIENT_PLAYER.equals(name)) {
            for (MethodNode mn : node.methods) {
                if (METHOD_NAME.equals(mn.name) && METHOD_DESC.equals(mn.desc)) {
                    for (AbstractInsnNode insn : mn.instructions.toArray()) {
                        if (insn.getOpcode() == Opcodes.ARETURN) {
                            InsnList patch = new InsnList();
                            patch.add(new VarInsnNode(Opcodes.ALOAD, 0));
                            patch.add(new MethodInsnNode(
                                    Opcodes.INVOKEVIRTUAL,
                                    "net/minecraft/world/entity/player/Player",
                                    "getGameProfile",
                                    "()Lcom/mojang/authlib/GameProfile;",
                                    false));
                            patch.add(new InsnNode(Opcodes.SWAP));
                            patch.add(new MethodInsnNode(
                                    Opcodes.INVOKESTATIC,
                                    CAPE_UTILS,
                                    PATCH_METHOD,
                                    PATCH_DESC,
                                    false));
                            mn.instructions.insertBefore(insn, patch);
                            changed = true;
                        }
                    }
                    mn.maxStack = Math.max(mn.maxStack, 3);
                }
            }
        }

        return changed;
    }

    @Override
    public ClassNode transform(ClassNode input, ITransformerVotingContext context) {
        if (inject(input)) {
            LOGGER.info("Injected OptiFine cape hook into {}.getSkin()", input.name.replace('/', '.'));
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
                Target.targetClass(TARGET_PLAYER_INFO),
                Target.targetClass(TARGET_CLIENT_PLAYER));
    }

    @Override
    public TargetType<ClassNode> getTargetType() {
        return TargetType.CLASS;
    }
}
