package net.pryzma.entity.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.resources.ResourceLocation;

/**
 * A node of a CEM model tree. Vanilla parts are rebuilt as {@code PrCemPart}s that keep their
 * cubes, so the vanilla model code that holds them keeps animating them; custom parts carry the
 * boxes of the {@code .jem}. Rendering a part first lets the model run its animations for the
 * entity being drawn, and a part with its own texture draws into that texture's buffer.
 */
public final class PrCemPart extends ModelPart {
    private final PrCemGeometry.Quad[] quads;
    private final ResourceLocation texture;
    /** The {@code .jem} id of a custom part; {@code null} for rebuilt vanilla parts. */
    final String id;
    PrCemModel model;

    PrCemPart(List<Cube> cubes, Map<String, ModelPart> children, PrCemGeometry.Quad[] quads, ResourceLocation texture, String id) {
        super(cubes, children);
        this.quads = quads;
        this.texture = texture;
        this.id = id;
    }

    /** A copy of a vanilla part: same cubes, same pose, children to be filled by the caller. */
    static PrCemPart copyOf(ModelPart vanilla) {
        PrCemPart copy = new PrCemPart(new ArrayList<>(vanilla.cubes), new LinkedHashMap<>(), new PrCemGeometry.Quad[0], null, null);
        copy.setInitialPose(vanilla.getInitialPose());
        copy.loadPose(vanilla.getInitialPose());
        copy.visible = vanilla.visible;
        copy.skipDraw = vanilla.skipDraw;
        return copy;
    }

    /**
     * Custom parts keep their pose when vanilla resets a model ({@code HierarchicalModel} resets
     * every part each frame): their pose belongs to the {@code .jem} and its animations, as in
     * OptiFine, which skips {@code loadPose} for custom parts.
     */
    @Override
    public void loadPose(PartPose pose) {
        if (id == null) {
            super.loadPose(pose);
        }
    }

    /** The animation program of the tree this part belongs to, or {@code null}. */
    PrCemModel model() {
        return model;
    }

    PrCemGeometry.Quad[] quads() {
        return quads;
    }

    void addChild(String name, ModelPart child) {
        String unique = name;
        for (int n = 2; children.containsKey(unique); n++) {
            unique = name + "-" + n;
        }
        children.put(unique, child);
    }

    @Override
    public void render(PoseStack pose, VertexConsumer consumer, int light, int overlay, int color) {
        if (model != null) {
            model.animate();
        }
        if (!visible || (cubes.isEmpty() && children.isEmpty() && quads.length == 0)) {
            return;
        }
        VertexConsumer out = texture == null ? consumer : PrCemRender.bufferFor(texture, consumer);
        pose.pushPose();
        translateAndRotate(pose);
        if (!skipDraw) {
            PoseStack.Pose last = pose.last();
            for (Cube cube : cubes) {
                cube.compile(last, out, light, overlay, color);
            }
            PrCemGeometry.render(quads, last, out, light, overlay, color);
        }
        for (ModelPart child : children.values()) {
            child.render(pose, out, light, overlay, color);
        }
        pose.popPose();
    }
}
