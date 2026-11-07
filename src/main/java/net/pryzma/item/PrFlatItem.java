package net.pryzma.item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.joml.Vector3f;
import org.joml.Vector3fc;

import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.core.Direction;

/**
 * Transforms ItemQuads of 2D dropped items into completely flat billboard sprites with zero extruded 1-pixel thickness.
 * Strips all perimeter/side quads (UP, DOWN, WEST, EAST) and collapses the front/back (SOUTH/NORTH) faces
 * to Z = 0.5F (centered), perfectly matching classic Minecraft 1.2.5 flat billboard dropped items.
 */
public final class PrFlatItem {
    private static final Map<ItemQuads, ItemQuads> CACHE = new ConcurrentHashMap<>();

    private PrFlatItem() {
    }

    public static ItemQuads flatten(ItemQuads quads) {
        if (quads == null || quads.isEmpty()) {
            return quads;
        }
        return CACHE.computeIfAbsent(quads, PrFlatItem::flattenDirect);
    }

    public static void clearCache() {
        CACHE.clear();
    }

    private static ItemQuads flattenDirect(ItemQuads original) {
        List<BakedQuad> all = original.all();
        if (all == null || all.isEmpty()) {
            return original;
        }
        List<BakedQuad> flat = new ArrayList<>(all.size());
        for (BakedQuad quad : all) {
            Direction dir = quad.direction();
            if (dir != null && dir.getAxis() != Direction.Axis.Z) {
                // Strip side/perimeter quads (extruded 1-pixel thickness)
                continue;
            }
            Vector3fc p0 = quad.position0();
            Vector3fc p1 = quad.position1();
            Vector3fc p2 = quad.position2();
            Vector3fc p3 = quad.position3();
            Vector3f np0 = new Vector3f(p0.x(), p0.y(), 0.5F);
            Vector3f np1 = new Vector3f(p1.x(), p1.y(), 0.5F);
            Vector3f np2 = new Vector3f(p2.x(), p2.y(), 0.5F);
            Vector3f np3 = new Vector3f(p3.x(), p3.y(), 0.5F);
            flat.add(new BakedQuad(np0, np1, np2, np3, quad.packedUV0(), quad.packedUV1(), quad.packedUV2(), quad.packedUV3(), quad.direction(), quad.materialInfo()));
        }
        return ItemQuads.split(flat);
    }
}
