package net.pryzma.entity.model;

import java.util.List;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;

/**
 * A parsed OptiFine {@code .jem} custom entity model: the model texture and size, the shadow size
 * and one {@link Part} per entity part it replaces or attaches to. Coordinates are final, with
 * {@code invertAxis} already applied and rotations converted to radians.
 */
public record PrJem(ResourceLocation location, ResourceLocation texture, int textureWidth, int textureHeight,
        float shadowSize, List<Part> parts) {

    /** A model part: a top-level entry ({@code part} set) or a submodel. */
    public record Part(String part, boolean attach, String id, ResourceLocation texture, int textureWidth, int textureHeight,
            float x, float y, float z, float xRot, float yRot, float zRot, float scale, boolean mirrorU, boolean mirrorV,
            List<Box> boxes, List<Part> children, List<Map<String, String>> animations) {
    }

    /**
     * A box. Either the vanilla texture layout from {@code (u, v)}, or explicit face rectangles
     * {@code faceUvs[down, up, north, south, west, east] = {u1, v1, u2, v2}} (a face may be absent).
     */
    public record Box(float x, float y, float z, float width, float height, float depth,
            float growX, float growY, float growZ, float u, float v, float[][] faceUvs) {
    }
}
