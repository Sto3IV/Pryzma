package net.pryzma.neoforge;

/*
 * Ranni: Temporary workaround. Will fix properly in v2.0 (Note: v2.0 was cancelled).
 */

import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
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
 * NeoForge 21.1 adds {@code public Map<ResourceLocation, TextureAtlasSprite> getTextures()}
 * to {@link net.minecraft.client.renderer.texture.TextureAtlas}.
 * NeoForge's {@link net.neoforged.neoforge.client.textures.FluidSpriteCache#reload()} invokes
 * this method to index all block atlas textures.
 *
 * Pryzma's pre-compiled {@code TextureAtlas} from OptiFine lacks this method, causing
 * {@link NoSuchMethodError} on texture reload which prevents fluid sprite resolution.
 *
 * Additionally, patches {@code isAbsoluteLocationPath(String)} in {@code TextureAtlas} to delegate
 * to {@link net.pryzma.util.PathPackScan#isAbsoluteLocationPath(String)}, recognizing both
 * {@code optifine/} and legacy {@code mcpatcher/} sprite paths as absolute atlas locations.
 */
public class TextureAtlasTransformer implements ITransformer<ClassNode> {
    private static final Logger LOGGER = LoggerFactory.getLogger("Pryzma");
    static final String TARGET = "net.minecraft.client.renderer.texture.TextureAtlas";
    static final String OWNER = "net/minecraft/client/renderer/texture/TextureAtlas";
    static final String SCAN_OWNER = "net/pryzma/util/PathPackScan";

    public static boolean inject(ClassNode node) {
        boolean injected = false;
        boolean hasGetTextures = false;
        for (MethodNode m : node.methods) {
            if ("getTextures".equals(m.name) && "()Ljava/util/Map;".equals(m.desc)) {
                hasGetTextures = true;
                break;
            }
        }

        if (!hasGetTextures) {
            MethodNode m = new MethodNode(
                    Opcodes.ACC_PUBLIC,
                    "getTextures",
                    "()Ljava/util/Map;",
                    "()Ljava/util/Map<Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;>;",
                    null);
            m.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            m.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, OWNER, "texturesByName", "Ljava/util/Map;"));
            m.instructions.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    "java/util/Collections",
                    "unmodifiableMap",
                    "(Ljava/util/Map;)Ljava/util/Map;",
                    false));
            m.instructions.add(new InsnNode(Opcodes.ARETURN));
            m.maxStack = 1;
            m.maxLocals = 1;
            node.methods.add(m);
            injected = true;
        }

        for (MethodNode m : node.methods) {
            if ("isAbsoluteLocationPath".equals(m.name) && "(Ljava/lang/String;)Z".equals(m.desc)) {
                boolean alreadyPatched = false;
                for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (insn instanceof MethodInsnNode mi
                            && "isAbsoluteLocationPath".equals(mi.name)
                            && SCAN_OWNER.equals(mi.owner)) {
                        alreadyPatched = true;
                        break;
                    }
                }
                if (!alreadyPatched) {
                    m.instructions.clear();
                    m.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    m.instructions.add(new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            SCAN_OWNER,
                            "isAbsoluteLocationPath",
                            "(Ljava/lang/String;)Z",
                            false));
                    m.instructions.add(new InsnNode(Opcodes.IRETURN));
                    m.maxStack = 1;
                    m.maxLocals = 1;
                    injected = true;
                }
                break;
            }
        }

        return injected;
    }

    @Override
    public ClassNode transform(ClassNode input, ITransformerVotingContext context) {
        if (inject(input)) {
            LOGGER.info("Injected getTextures() and isAbsoluteLocationPath hook into {}", input.name);
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
