package net.pryzma.item;

import com.google.common.collect.MapMaker;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wraps a 2D item BakedModel to render as a completely flat sprite with zero extruded 1-pixel thickness.
 * Strips all perimeter/side quads (UP, DOWN, WEST, EAST) and collapses the front/back (SOUTH/NORTH) faces
 * to Z = 0.5F (local Z = 0.0F), perfectly matching classic Minecraft 1.2.5 flat billboard dropped items.
 */
public class FlatItemBakedModel extends BakedModelWrapper<BakedModel> {

    private static final Map<BakedModel, FlatItemBakedModel> CACHE = new MapMaker().weakKeys().makeMap();
    private static final int RAW_MID_Z = Float.floatToRawIntBits(0.5F);

    private record StdKey(@Nullable Direction side) {}
    private record ExtKey(@Nullable Direction side, @Nullable RenderType renderType) {}

    private final Map<StdKey, List<BakedQuad>> standardQuadCache = new ConcurrentHashMap<>();
    private final Map<ExtKey, List<BakedQuad>> extendedQuadCache = new ConcurrentHashMap<>();

    public static BakedModel of(BakedModel original) {
        if (original == null || original instanceof FlatItemBakedModel) {
            return original;
        }
        return CACHE.computeIfAbsent(original, FlatItemBakedModel::new);
    }

    public static void clearCache() {
        CACHE.clear();
    }

    public FlatItemBakedModel(BakedModel originalModel) {
        super(originalModel);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
        if (side != null && side.getAxis() != Direction.Axis.Z) {
            return Collections.emptyList();
        }
        return standardQuadCache.computeIfAbsent(new StdKey(side),
                k -> flattenQuadList(super.getQuads(state, side, rand)));
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand,
            ModelData extraData, @Nullable RenderType renderType) {
        if (side != null && side.getAxis() != Direction.Axis.Z) {
            return Collections.emptyList();
        }
        return extendedQuadCache.computeIfAbsent(new ExtKey(side, renderType),
                k -> flattenQuadList(super.getQuads(state, side, rand, extraData, renderType)));
    }

    @Override
    public BakedModel applyTransform(ItemDisplayContext transformType, PoseStack poseStack, boolean applyLeftHandTransform) {
        super.applyTransform(transformType, poseStack, applyLeftHandTransform);
        return this;
    }

    @Override
    public List<BakedModel> getRenderPasses(ItemStack itemStack, boolean fabulous) {
        List<BakedModel> passes = super.getRenderPasses(itemStack, fabulous);
        if (passes.size() <= 1) {
            return List.of(this);
        }
        List<BakedModel> wrapped = new ArrayList<>(passes.size());
        for (BakedModel pass : passes) {
            wrapped.add(FlatItemBakedModel.of(pass));
        }
        return wrapped;
    }

    @Override
    public ItemOverrides getOverrides() {
        return ItemOverrides.EMPTY;
    }

    private static List<BakedQuad> flattenQuadList(List<BakedQuad> quads) {
        if (quads == null || quads.isEmpty()) {
            return Collections.emptyList();
        }
        List<BakedQuad> flat = new ArrayList<>(quads.size());
        for (BakedQuad quad : quads) {
            Direction dir = quad.getDirection();
            if (dir != null && dir.getAxis() != Direction.Axis.Z) {
                continue;
            }
            if (dir == null) {
                int[] vertices = quad.getVertices();
                if (vertices.length >= 8) {
                    int normal = vertices[7];
                    byte nz = (byte) ((normal >> 16) & 0xFF);
                    if (nz == 0) {
                        continue;
                    }
                }
            }
            flat.add(flattenQuad(quad));
        }
        return flat.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(flat);
    }

    private static BakedQuad flattenQuad(BakedQuad quad) {
        int[] original = quad.getVertices();
        int[] flattened = original.clone();
        for (int v = 0; v < 4; v++) {
            flattened[v * 8 + 2] = RAW_MID_Z;
        }
        return new BakedQuad(flattened, quad.getTintIndex(), quad.getDirection(), quad.getSprite(), quad.isShade(),
                quad.hasAmbientOcclusion());
    }
}
