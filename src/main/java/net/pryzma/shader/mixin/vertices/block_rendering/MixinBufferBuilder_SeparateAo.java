package net.pryzma.shader.mixin.vertices.block_rendering;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.pryzma.shader.shaderpack.materialmap.WorldRenderingSettings;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = BufferBuilder.class, priority = 999)
public abstract class MixinBufferBuilder_SeparateAo implements VertexConsumer {

	@Override
	public void putBlockBakedQuad(float x, float y, float z, BakedQuad quad, QuadInstance quadInstance) {
		VertexConsumer.super.putBlockBakedQuad(x, y, z, quad, quadInstance);
	}

	@Override
	public void putBakedQuad(PoseStack.Pose pose, BakedQuad quad, QuadInstance quadInstance) {
		VertexConsumer.super.putBakedQuad(pose, quad, quadInstance);
	}
}
