package net.pryzma.neoforge;

/*
 * Ranni: Please work, I have a family.
 */

import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.ITransformer.Target;
import cpw.mods.modlauncher.api.ITransformerVotingContext;
import cpw.mods.modlauncher.api.TargetType;
import cpw.mods.modlauncher.api.TransformerVoteResult;

/**
 * Pryzma's class replacer minus the classes whose NeoForge original must stay loaded because the
 * payload copy was compiled against Forge. OptiFine's additions to those are grafted onto the
 * originals by dedicated transformers.
 */
final class FilteredReplacementTransformer implements ITransformer<ClassNode> {
    private static final Logger LOGGER = LoggerFactory.getLogger("Pryzma");

    /**
     * Outer classes kept as NeoForge ships them; nest members ({@code Outer$*}) follow their outer class.
     * <ul>
     * <li>{@code BlockEntity}: the payload saves/loads through Forge capabilities (NoSuchMethodError
     * {@code getCapabilities}) and lacks NeoForgeData, data attachments, capability invalidation and the
     * {@code setData/removeData/syncData} overrides. See {@link BlockEntityTransformer}.</li>
     * </ul>
     */
    static final Set<String> KEEP_NEOFORGE = Set.of(
            "net.minecraft.world.level.block.entity.BlockEntity",
            "net.minecraft.world.entity.Mob",
            "net.minecraft.client.resources.model.WeightedBakedModel",
            "net.minecraft.client.renderer.entity.layers.CapeLayer");

    private final ITransformer<ClassNode> delegate;
    private final Set<String> keep;

    FilteredReplacementTransformer(ITransformer<ClassNode> delegate, Set<String> keep) {
        this.delegate = delegate;
        this.keep = keep;
    }

    static boolean withheld(String className, Set<String> keep) {
        String name = className.replace('/', '.');
        int nest = name.indexOf('$');
        return keep.contains(nest < 0 ? name : name.substring(0, nest));
    }

    @Override
    public Set<Target<ClassNode>> targets() {
        Set<Target<ClassNode>> kept = new HashSet<>();
        Set<String> skipped = new TreeSet<>();
        for (Target<ClassNode> target : delegate.targets()) {
            if (withheld(target.className(), keep)) {
                skipped.add(target.className());
            } else {
                kept.add(target);
            }
        }
        LOGGER.info("Pryzma replacement withheld, NeoForge originals kept: {}", skipped);
        return kept;
    }

    @Override
    public ClassNode transform(ClassNode input, ITransformerVotingContext context) {
        return delegate.transform(input, context);
    }

    @Override
    public TransformerVoteResult castVote(ITransformerVotingContext context) {
        return delegate.castVote(context);
    }

    @Override
    public TargetType<ClassNode> getTargetType() {
        return delegate.getTargetType();
    }
}
