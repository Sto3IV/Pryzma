package net.pryzma.neoforge;

/*
 * Ranni: Third-party mixins are not our code, but their crashes are our bug reports.
 */

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.ITransformer.Target;
import cpw.mods.modlauncher.api.ITransformerVotingContext;
import cpw.mods.modlauncher.api.TargetType;
import cpw.mods.modlauncher.api.TransformerVoteResult;

/**
 * Downgrades fatal {@code @Inject(locals = ...)} contracts in third-party mixins that target
 * methods Pryzma replaces wholesale.
 *
 * <p>Pryzma swaps in pre-compiled OptiFine bodies for ~430 vanilla types. The bytecode is
 * behaviourally equivalent but its local variable table is not: {@code LevelRenderer.renderLevel}
 * gains an extra {@code float}, two extra {@code boolean}s and carries a {@code Collection} where
 * vanilla carries an {@code Iterator}. Mixin compares the captured LVT against the handler
 * descriptor as an exact string, so any mod that captures locals in such a method fails the
 * comparison.
 *
 * <p>What the mod asked for decides what happens next:
 * <ul>
 *   <li>{@code CAPTURE_FAILHARD} throws {@code InjectionError} and aborts classloading. The game
 *       never reaches the main menu. This is Epic Fight's
 *       {@code yesman.epicfight.mixin.client.MixinLevelRenderer}.</li>
 *   <li>{@code CAPTURE_FAILEXCEPTION} replaces the handler with one that throws on every frame.</li>
 *   <li>{@code CAPTURE_FAILSOFT} logs one warning and skips the injection.</li>
 * </ul>
 *
 * <p>This transformer rewrites the first two to the third. FAILSOFT is chosen over {@code PRINT}
 * deliberately: {@code PRINT} unconditionally abandons the injection and dumps the full local
 * table on every launch, whereas FAILSOFT still applies the injection whenever the LVT does line
 * up, and costs one log line when it does not. The relaxation is therefore lossless where the mod
 * would have worked and degrades to exactly the mod author's own soft-failure path where it would
 * not. See {@code CallbackInjector.inject}: the {@code LocalCapture} switch routes FAILSOFT to
 * {@code warn} + {@code return}, and every other capturing mode to {@code throw}.
 */
public class MixinHardeningTransformer implements ITransformer<ClassNode> {
    private static final Logger LOGGER = LoggerFactory.getLogger("Pryzma");

    static final String LOCAL_CAPTURE = "Lorg/spongepowered/asm/mixin/injection/callback/LocalCapture;";
    static final String INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;";

    /** Capture modes that abort classloading or poison the handler. */
    static final Set<String> FATAL = Set.of("CAPTURE_FAILHARD", "CAPTURE_FAILEXCEPTION");

    /** The mode they are rewritten to: warn once, skip the injection, keep the game alive. */
    static final String RELAXED = "CAPTURE_FAILSOFT";

    /**
     * Mixins known to capture locals inside a method whose body Pryzma replaces. Adding an entry
     * here is the only step needed to cover a new mod; the rewrite itself is signature-agnostic.
     */
    static final Set<String> FRAGILE_MIXINS = Set.of(
            "yesman.epicfight.mixin.client.MixinLevelRenderer");

    /**
     * Relaxes every fatal {@code locals} enum reachable from a method annotation.
     *
     * @return the number of rewritten annotation values
     */
    public static int inject(ClassNode node) {
        int[] count = new int[1];
        for (MethodNode m : node.methods) {
            int before = count[0];
            relaxAll(m.visibleAnnotations, count);
            relaxAll(m.invisibleAnnotations, count);
            if (count[0] > before) {
                LOGGER.warn("Relaxed {} fatal local capture(s) to {} on {}.{}{}",
                        count[0] - before, RELAXED, node.name, m.name, m.desc);
            }
        }
        return count[0];
    }

    private static void relaxAll(List<AnnotationNode> annotations, int[] count) {
        if (annotations == null) {
            return;
        }
        for (AnnotationNode a : annotations) {
            relax(a, count);
        }
    }

    /** {@code AnnotationNode.values} is a flat {@code name, value, name, value} list. */
    private static void relax(AnnotationNode annotation, int[] count) {
        if (annotation == null || annotation.values == null) {
            return;
        }
        int before = count[0];
        for (int i = 1; i < annotation.values.size(); i += 2) {
            Object original = annotation.values.get(i);
            Object relaxed = relaxValue(original, count);
            if (relaxed != original) {
                annotation.values.set(i, relaxed);
            }
        }
        if (count[0] > before && INJECT.equals(annotation.desc)) {
            setAnnotationInt(annotation, "require", 0);
            setAnnotationInt(annotation, "expect", 0);
        }
    }

    private static void setAnnotationInt(AnnotationNode annotation, String name, int val) {
        if (annotation.values == null) {
            annotation.values = new ArrayList<>();
        } else if (!(annotation.values instanceof ArrayList)) {
            annotation.values = new ArrayList<>(annotation.values);
        }
        for (int i = 0; i < annotation.values.size(); i += 2) {
            if (name.equals(annotation.values.get(i))) {
                annotation.values.set(i + 1, Integer.valueOf(val));
                return;
            }
        }
        annotation.values.add(name);
        annotation.values.add(Integer.valueOf(val));
    }

    /** Enum values are {@code String[]{descriptor, constant}}; arrays are lists; nested annotations recurse. */
    private static Object relaxValue(Object value, int[] count) {
        if (value instanceof String[] enumValue) {
            if (enumValue.length == 2 && LOCAL_CAPTURE.equals(enumValue[0]) && FATAL.contains(enumValue[1])) {
                count[0]++;
                return new String[] { LOCAL_CAPTURE, RELAXED };
            }
            return value;
        }
        if (value instanceof AnnotationNode nested) {
            relax(nested, count);
            return nested;
        }
        if (value instanceof List<?> list) {
            // Copy on write: array-valued annotations are not guaranteed to be mutable lists.
            List<Object> copy = null;
            for (int i = 0; i < list.size(); i++) {
                Object original = list.get(i);
                Object relaxed = relaxValue(original, count);
                if (relaxed != original) {
                    if (copy == null) {
                        copy = new ArrayList<>(list);
                    }
                    copy.set(i, relaxed);
                }
            }
            return copy != null ? copy : value;
        }
        return value;
    }

    @Override
    public ClassNode transform(ClassNode input, ITransformerVotingContext context) {
        int relaxed = inject(input);
        if (relaxed > 0) {
            LOGGER.info("Hardened {}: {} local capture(s) no longer abort classloading against Pryzma's replaced bodies",
                    input.name, relaxed);
        }
        return input;
    }

    @Override
    public TransformerVoteResult castVote(ITransformerVotingContext context) {
        return TransformerVoteResult.YES;
    }

    @Override
    public Set<Target<ClassNode>> targets() {
        Set<Target<ClassNode>> targets = new LinkedHashSet<>();
        for (String mixin : FRAGILE_MIXINS) {
            targets.add(Target.targetClass(mixin));
        }
        return targets;
    }

    @Override
    public TargetType<ClassNode> getTargetType() {
        return TargetType.CLASS;
    }
}
