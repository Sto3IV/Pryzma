package net.pryzma.shader.mixin.vertices.block_rendering;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.pryzma.shader.shaderpack.materialmap.WorldRenderingSettings;
import net.pryzma.shader.vertices.BlockContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Block context for the extended terrain format on the vanilla chunk path: each block and fluid is
 * meshed with its {@code block.properties} id ({@code mc_Entity}), light emission and section-local
 * position ({@code at_midBlock}), as PryzmaShaders supplies them on the terrain chunk path.
 *
 * <p>Runs on the chunk workers. The id map is replaced, never mutated, when a pipeline is built.
 */
@Mixin(SectionCompiler.class)
public class MixinSectionCompiler {
	/** ShadersMod render type of fluids ({@code mc_Entity.y}); solid blocks use 0. */
	@Unique
	private static final byte FLUID_RENDER_TYPE = 1;

	@WrapOperation(method = "compile(Lnet/minecraft/core/SectionPos;Lnet/minecraft/client/renderer/chunk/RenderSectionRegion;Lcom/mojang/blaze3d/vertex/VertexSorting;Lnet/minecraft/client/renderer/SectionBufferBuilderPack;Ljava/util/List;)Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/FluidRenderer;tesselate(Lnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/client/renderer/block/FluidRenderer$Output;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/material/FluidState;)V"))
	private void pryzma$liquidContext(FluidRenderer renderer, BlockAndTintGetter level, BlockPos pos,
									FluidRenderer.Output output, BlockState blockState, FluidState fluidState,
									Operation<Void> original) {
		if (!WorldRenderingSettings.INSTANCE.shouldUseExtendedVertexFormat()) {
			original.call(renderer, level, pos, output, blockState, fluidState);
			return;
		}
		Object2IntMap<BlockState> ids = WorldRenderingSettings.INSTANCE.getBlockStateIds();
		int blockId = ids != null ? ids.getInt(fluidState.createLegacyBlock()) : -1;
		byte emission = (byte) blockState.getLightEmission();
		BlockContext.set(blockId, FLUID_RENDER_TYPE, emission, pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
		try {
			original.call(renderer, level, pos, output, blockState, fluidState);
		} finally {
			BlockContext.clear();
		}
	}

	@WrapOperation(method = "compile(Lnet/minecraft/core/SectionPos;Lnet/minecraft/client/renderer/chunk/RenderSectionRegion;Lcom/mojang/blaze3d/vertex/VertexSorting;Lnet/minecraft/client/renderer/SectionBufferBuilderPack;Ljava/util/List;)Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/ModelBlockRenderer;tesselateBlock(Lnet/minecraft/client/renderer/block/BlockQuadOutput;FFFLnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;J)V"))
	private void pryzma$blockContext(ModelBlockRenderer blockRenderer, BlockQuadOutput output,
								   float x, float y, float z, BlockAndTintGetter region, BlockPos pos,
								   BlockState state, BlockStateModel model, long seed,
								   Operation<Void> original) {
		if (!WorldRenderingSettings.INSTANCE.shouldUseExtendedVertexFormat()) {
			original.call(blockRenderer, output, x, y, z, region, pos, state, model, seed);
			return;
		}
		Object2IntMap<BlockState> ids = WorldRenderingSettings.INSTANCE.getBlockStateIds();
		int blockId = ids != null ? ids.getInt(state) : -1;
		byte emission = (byte) state.getLightEmission();
		BlockContext.set(blockId, (byte) 0, emission, pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
		try {
			original.call(blockRenderer, output, x, y, z, region, pos, state, model, seed);
		} finally {
			BlockContext.clear();
		}
	}
}
