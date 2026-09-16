package net.pryzma.neoforge;

/*
 * Ranni: MAGIC DO NOT TOUCH.
 * Celian: Why does DH render the entire horizon during the shadow pass?!
 * Ranni: Because it blindly trusts OptiFine's rendering callbacks. I spent 4 hours tracing this to a matrix corruption.
 * If you remove this shadow guard, your GPU will scream and the Aether mod will crash. Leave it alone.
 */

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.ITransformer.Target;
import cpw.mods.modlauncher.api.ITransformerVotingContext;
import cpw.mods.modlauncher.api.TargetType;
import cpw.mods.modlauncher.api.TransformerVoteResult;

/**
 * Isolated compatibility transformer for Distant Horizons (DH).
 *
 * <p>Enables full LOD rendering and OptiFine coordination exclusively within Distant Horizons
 * without declaring a global {@code optifine} mod ID in {@code neoforge.mods.toml} (which would
 * cause mods like The Aether, Twilight Forest, and Create to crash or abort).
 *
 * <ol>
 *   <li><b>NeoforgeMain</b>: Rewrites the {@code "optifine"} accessor gate to {@code "pryzma"},
 *       prompting DH to bind its native OptiFine accessor directly before static initializers cache it.</li>
 *   <li><b>AbstractOptifineAccessor</b>: Remaps reflection lookups from {@code "net.optifine.shaders.Shaders"}
 *       to {@code "net.pryzma.shaders.Shaders"}, and {@code "ofFogType"} to {@code "prFogType"}.</li>
 *   <li><b>ClientApi</b>: Prepends a check {@code if (Shaders.isShadowPass) return;} to each LOD pass,
 *       preventing DH from re-rendering the entire LOD horizon during the shadow map pass (which causes
 *       massive FPS loss, matrix corruption, and invalid framebuffer writes).</li>
 *   <li><b>ModChecker</b>: Redirects any remaining {@code isLoaded("optifine")} queries to {@code "pryzma"}.</li>
 * </ol>
 */
public class DistantHorizonsCompatTransformer implements ITransformer<ClassNode> {
    private static final Logger LOGGER = LoggerFactory.getLogger("Pryzma");

    static final String TARGET_MOD_CHECKER = "com.seibel.distanthorizons.neoforge.wrappers.modAccessor.ModChecker";
    static final String TARGET_NEOFORGE_MAIN = "com.seibel.distanthorizons.neoforge.NeoforgeMain";
    static final String TARGET_OPTIFINE_ACCESSOR = "com.seibel.distanthorizons.core.wrapperInterfaces.modAccessor.AbstractOptifineAccessor";
    static final String TARGET_CLIENT_API = "com.seibel.distanthorizons.core.api.internal.ClientApi";

    static final Set<String> TARGETS = Set.of(
            TARGET_MOD_CHECKER,
            TARGET_NEOFORGE_MAIN,
            TARGET_OPTIFINE_ACCESSOR,
            TARGET_CLIENT_API
    );

    static final String OPTIFINE_MOD_ID = "optifine";
    static final String COMPAT_MOD_ID = "pryzma";
    static final String OPTIFINE_ACCESSOR_DESC = "Lcom/seibel/distanthorizons/core/wrapperInterfaces/modAccessor/IOptifineAccessor;";

    static final Map<String, String> REFLECTION_REMAPS = Map.of(
            "net.optifine.shaders.Shaders", "net.pryzma.shaders.Shaders",
            "ofFogType", "prFogType"
    );

    static final Set<String> LOD_PASSES = Set.of(
            "renderLods",
            "renderDeferredLodsForShaders",
            "renderFadeOpaque",
            "renderFadeTransparent"
    );

    static final String SHADERS_OWNER = "net/pryzma/shaders/Shaders";
    static final String SHADOW_PASS_FIELD = "isShadowPass";

    static final String MODLIST = "net/neoforged/fml/ModList";
    static final String IS_LOADED = "isLoaded";
    static final String IS_LOADED_DESC = "(Ljava/lang/String;)Z";
    static final String GET_MOD_FILE = "getModFileById";
    static final String GET_MOD_FILE_DESC = "(Ljava/lang/String;)Lnet/neoforged/neoforgespi/language/IModFileInfo;";

    static final String ADAPTER = "net/pryzma/reflect/ReflectorAdapter";
    static final String BRIDGE_IS_LOADED = "isModLoadedBridge";
    static final String BRIDGE_IS_LOADED_DESC = "(Lnet/neoforged/fml/ModList;Ljava/lang/String;)Z";
    static final String BRIDGE_GET_FILE = "getModFileByIdBridge";
    static final String BRIDGE_GET_FILE_DESC = "(Lnet/neoforged/fml/ModList;Ljava/lang/String;)Lnet/neoforged/neoforgespi/language/IModFileInfo;";

    public static boolean inject(ClassNode node) {
        String name = node.name.replace('/', '.');
        boolean modified = false;

        if (TARGET_MOD_CHECKER.equals(name)) {
            modified |= injectModChecker(node);
        }
        if (TARGET_NEOFORGE_MAIN.equals(name)) {
            modified |= injectNeoforgeMain(node);
        }
        if (TARGET_OPTIFINE_ACCESSOR.equals(name)) {
            modified |= injectAbstractOptifineAccessor(node);
        }
        if (TARGET_CLIENT_API.equals(name)) {
            modified |= injectClientApiShadowGuard(node);
        }

        return modified;
    }

    static boolean injectModChecker(ClassNode node) {
        boolean changed = false;
        for (MethodNode m : node.methods) {
            for (AbstractInsnNode insn : m.instructions.toArray()) {
                if (insn instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKEVIRTUAL && MODLIST.equals(call.owner)) {
                    if (IS_LOADED.equals(call.name) && IS_LOADED_DESC.equals(call.desc)) {
                        m.instructions.set(insn, new MethodInsnNode(Opcodes.INVOKESTATIC, ADAPTER, BRIDGE_IS_LOADED, BRIDGE_IS_LOADED_DESC, false));
                        changed = true;
                    } else if (GET_MOD_FILE.equals(call.name) && GET_MOD_FILE_DESC.equals(call.desc)) {
                        m.instructions.set(insn, new MethodInsnNode(Opcodes.INVOKESTATIC, ADAPTER, BRIDGE_GET_FILE, BRIDGE_GET_FILE_DESC, false));
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    static boolean injectNeoforgeMain(ClassNode node) {
        boolean changed = false;
        for (MethodNode m : node.methods) {
            for (AbstractInsnNode insn : m.instructions) {
                if (insn instanceof LdcInsnNode ldc && OPTIFINE_MOD_ID.equals(ldc.cst)) {
                    AbstractInsnNode next = nextReal(insn.getNext());
                    if (next instanceof LdcInsnNode nextLdc && nextLdc.cst instanceof Type t
                            && OPTIFINE_ACCESSOR_DESC.equals(t.getDescriptor())) {
                        ldc.cst = COMPAT_MOD_ID;
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    static boolean injectAbstractOptifineAccessor(ClassNode node) {
        boolean changed = false;
        for (MethodNode m : node.methods) {
            for (AbstractInsnNode insn : m.instructions) {
                if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof String str) {
                    String replacement = REFLECTION_REMAPS.get(str);
                    if (replacement != null) {
                        ldc.cst = replacement;
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    static boolean injectClientApiShadowGuard(ClassNode node) {
        boolean changed = false;
        for (MethodNode m : node.methods) {
            if ("()V".equals(m.desc) && LOD_PASSES.contains(m.name)) {
                if ((m.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0 || isShadowGuarded(m)) {
                    continue;
                }
                LabelNode proceed = new LabelNode();
                InsnList guard = new InsnList();
                guard.add(new FieldInsnNode(Opcodes.GETSTATIC, SHADERS_OWNER, SHADOW_PASS_FIELD, "Z"));
                guard.add(new JumpInsnNode(Opcodes.IFEQ, proceed));
                guard.add(new InsnNode(Opcodes.RETURN));
                guard.add(proceed);
                m.instructions.insert(guard);
                m.maxStack = Math.max(m.maxStack, 1);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean isShadowGuarded(MethodNode m) {
        AbstractInsnNode first = nextReal(m.instructions.getFirst());
        return first instanceof FieldInsnNode f
                && SHADERS_OWNER.equals(f.owner)
                && SHADOW_PASS_FIELD.equals(f.name);
    }

    private static AbstractInsnNode nextReal(AbstractInsnNode insn) {
        while (insn != null && insn.getOpcode() < 0) {
            insn = insn.getNext();
        }
        return insn;
    }

    @Override
    public ClassNode transform(ClassNode input, ITransformerVotingContext context) {
        if (inject(input)) {
            LOGGER.info("Distant Horizons patched for isolated Pryzma compatibility: {}", input.name);
        }
        return input;
    }

    @Override
    public TransformerVoteResult castVote(ITransformerVotingContext context) {
        return TransformerVoteResult.YES;
    }

    @Override
    public Set<Target<ClassNode>> targets() {
        return TARGETS.stream().map(Target::targetClass).collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public TargetType<ClassNode> getTargetType() {
        return TargetType.CLASS;
    }
}
