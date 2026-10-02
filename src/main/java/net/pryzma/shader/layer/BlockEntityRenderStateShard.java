package net.pryzma.shader.layer;

import net.minecraft.client.renderer.RenderStateShard;

public final class BlockEntityRenderStateShard extends RenderStateShard {
	public static final BlockEntityRenderStateShard INSTANCE = new BlockEntityRenderStateShard();

	private BlockEntityRenderStateShard() {
		super("pryzma:is_block_entity", GbufferPrograms::beginBlockEntities, GbufferPrograms::endBlockEntities);
	}
}
