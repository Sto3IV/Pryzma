package net.pryzma.detail;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.GlowLichenBlock;
import net.minecraft.world.level.block.GrowingPlantBlock;
import net.minecraft.world.level.block.HangingRootsBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.extensions.IBlockExtension;

/**
 * Phase B4: decides on the chunk worker whether a block's model is a decorator, drawn only within the
 * detail distance. A decorator must leave nothing behind when it is not drawn: it cannot occlude or
 * hide a neighbour face (those faces were culled against it at mesh time), has no collision, block
 * entity or light, and is not a crop, sapling, fire or snow. Cheap rejections come first.
 */
public final class PrDetailClassifier {
    private static final TagKey<Block> DETAIL = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("pryzma", "detail"));
    private static final TagKey<Block> DETAIL_EXCLUDE = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("pryzma", "detail_exclude"));

    /** NeoForge lets a non-occluding block hide its neighbour's face; such a block cannot be dropped. */
    private static final ClassValue<Boolean> HIDES_FACES = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                return type.getMethod("hidesNeighborFace", BlockGetter.class, BlockPos.class, BlockState.class,
                        BlockState.class, Direction.class).getDeclaringClass() != IBlockExtension.class;
            } catch (NoSuchMethodException e) {
                return Boolean.TRUE;
            }
        }
    };

    /** Blocks that glow in any state (glow berries, glowing modded plants) stay whole, never a floating light. */
    private static final Map<Block, Boolean> EMISSIVE = new ConcurrentHashMap<>();

    private PrDetailClassifier() {
    }

    public static boolean isDetail(BlockState state, BlockAndTintGetter level, BlockPos pos) {
        // A BlockState subclass (Epic Fight's FractureBlockState) can override face hiding per state: never a decorator.
        if (state.canOcclude() || state.hasBlockEntity() || state.getClass() != BlockState.class) {
            return false;
        }
        Block block = state.getBlock();
        if (block instanceof CropBlock || block instanceof StemBlock || block instanceof AttachedStemBlock
                || block instanceof NetherWartBlock || block instanceof SweetBerryBushBlock || block instanceof PitcherCropBlock
                || block instanceof SaplingBlock || block instanceof BaseFireBlock || block instanceof SnowLayerBlock
                || HIDES_FACES.get(block.getClass()) || emissive(block) || state.getLightEmission(level, pos) > 0
                || !state.getCollisionShape(level, pos).isEmpty() || state.is(DETAIL_EXCLUDE)) {
            return false;
        }
        return block instanceof BushBlock || block instanceof GrowingPlantBlock || block instanceof VineBlock
                || block instanceof GlowLichenBlock || block instanceof HangingRootsBlock || block instanceof WebBlock
                || state.is(DETAIL) || state.is(BlockTags.FLOWERS);
    }

    private static boolean emissive(Block block) {
        Boolean known = EMISSIVE.get(block);
        if (known == null) {
            boolean any = false;
            for (BlockState s : block.getStateDefinition().getPossibleStates()) {
                if (s.getLightEmission(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) > 0) {
                    any = true;
                    break;
                }
            }
            known = any;
            EMISSIVE.put(block, known);
        }
        return known;
    }
}
