package net.pryzma.render;

import java.util.List;

import org.joml.Quaternionf;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.Font;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.UvMapping;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.pryzma.entity.texture.PrEntityTextures;

/**
 * A forwarding SubmitNodeCollector that captures only emissive geometry for the second pass,
 * submitting it at full brightness (0xF000F0) and dropping non-emissive geometry and overlays.
 */
public final class PrEmissiveSubmitNodeCollector implements SubmitNodeCollector {
    private static final int FULL_BRIGHT = 0xF000F0;
    private final SubmitNodeCollector delegate;

    private PrEmissiveSubmitNodeCollector(SubmitNodeCollector delegate) {
        this.delegate = delegate;
    }

    public static SubmitNodeCollector wrap(SubmitNodeCollector delegate) {
        return delegate instanceof PrEmissiveSubmitNodeCollector ? delegate : new PrEmissiveSubmitNodeCollector(delegate);
    }

    @Override
    public OrderedSubmitNodeCollector order(int order) {
        OrderedSubmitNodeCollector ordered = delegate.order(order);
        if (ordered instanceof SubmitNodeCollector sc) {
            return wrap(sc);
        }
        return wrap(delegate);
    }

    @Override
    public <S> void submitModel(Model<? super S> model, S state, PoseStack poseStack, RenderType renderType,
            int light, int overlay, int tint, UvMapping uvMapping, int outline) {
        Identifier tex = PrRenderTypes.texture(renderType);
        if (tex != null && PrEntityTextures.isEmissive(tex)) {
            delegate.submitModel(model, state, poseStack, renderType, FULL_BRIGHT, overlay, tint, uvMapping, outline);
        }
    }

    @Override
    public void submitModelPart(ModelPart part, PoseStack poseStack, RenderType renderType, int light, int overlay,
            UvMapping uvMapping, int outline, int tint) {
        Identifier tex = PrRenderTypes.texture(renderType);
        if (tex != null && PrEntityTextures.isEmissive(tex)) {
            delegate.submitModelPart(part, poseStack, renderType, FULL_BRIGHT, overlay, uvMapping, outline, tint);
        }
    }

    @Override
    public void submitCustomGeometry(PoseStack poseStack, RenderType renderType, CustomGeometryRenderer renderer) {
        Identifier tex = PrRenderTypes.texture(renderType);
        if (tex != null && PrEntityTextures.isEmissive(tex)) {
            delegate.submitCustomGeometry(poseStack, renderType, renderer);
        }
    }

    @Override
    public void submitShadow(PoseStack poseStack, float radius, List<EntityRenderState.ShadowPiece> pieces) {
    }

    @Override
    public void submitNameTag(PoseStack poseStack, Vec3 pos, int color, Component name, boolean dropShadow, int light,
            CameraRenderState camera) {
    }

    @Override
    public void submitText(PoseStack poseStack, float x, float y, FormattedCharSequence text, boolean dropShadow,
            Font.DisplayMode displayMode, int light, int color, int backgroundColor, int outlineColor) {
    }

    @Override
    public void submitTextBackground(PoseStack poseStack, float x, float y, float width, float height, int light,
            Font.DisplayMode displayMode, int color) {
    }

    @Override
    public void submitFlame(PoseStack poseStack, EntityRenderState state, Quaternionf rotation) {
    }

    @Override
    public void submitLeash(PoseStack poseStack, EntityRenderState.LeashState state) {
    }

    @Override
    public <S> void submitCrumblingOverlay(Model<? super S> model, S state, PoseStack poseStack, RenderType renderType,
            int light, int overlay, int tint, ModelFeatureRenderer.CrumblingOverlay crumbling) {
    }

    @Override
    public void submitMovingBlock(PoseStack poseStack, MovingBlockRenderState state, int light) {
    }

    @Override
    public void submitBlockModel(PoseStack poseStack, RenderType renderType, List<BlockStateModelPart> parts,
            int[] tint, int light, int overlay, int color) {
    }

    @Override
    public void submitBreakingBlockModel(PoseStack poseStack, List<BlockStateModelPart> parts, int light, boolean glowing) {
    }

    @Override
    public void submitShapeOutline(PoseStack poseStack, VoxelShape shape, RenderType renderType, int color,
            float lineWidth, boolean depthTest) {
    }

    @Override
    public void submitItem(PoseStack poseStack, ItemDisplayContext context, int light, int overlay, int tint,
            int[] tints, ItemQuads quads, ItemStackRenderState.FoilType foil) {
    }

    @Override
    public void submitQuadParticleGroup(QuadParticleRenderState state) {
    }

    @Override
    public void submitGizmoPrimitives(DrawableGizmoPrimitives.Group group, CameraRenderState camera, boolean depthTest) {
    }
}
