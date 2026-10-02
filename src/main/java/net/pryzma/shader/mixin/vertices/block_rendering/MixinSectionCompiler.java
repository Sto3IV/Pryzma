package net.pryzma.shader.mixin.vertices.block_rendering;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.pryzma.shader.shaderpack.materialmap.WorldRenderingSettings;
import net.pryzma.shader.vertices.BlockContext;
import net.pryzma.shader.vertices.BlockSensitiveBufferBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Block context for the extended terrain format on the vanilla chunk path: each block and fluid is
 * meshed with its {@code block.properties} id ({@code mc_Entity}), light emission and section-local
 * position ({@code at_midBlock}), as PryzmaShaders supplies them on Sodium's chunk path. PryzmaShaders dropped this
 * for vanilla meshing once it required Sodium, which leaves every vanilla chunk vertex at id -1.
 *
 * <p>Runs on the chunk workers. The id map is replaced, never mutated, when a pipeline is built.
 */
@Mixin(SectionCompiler.class)
public class MixinSectionCompiler {
	/** ShadersMod render type of fluids ({@code mc_Entity.y}); solid blocks use 0. */
	@Unique
	private static final byte FLUID_RENDER_TYPE = 1;

	@WrapOperation(method = "compile(Lnet/minecraft/core/SectionPos;Lnet/minecraft/client/renderer/chunk/RenderChunkRegion;Lcom/mojang/blaze3d/vertex/VertexSorting;Lnet/minecraft/client/renderer/SectionBufferBuilderPack;Ljava/util/List;)Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/BlockRenderDispatcher;renderLiquid(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/material/FluidState;)V"))
	private void iris$liquidContext(BlockRenderDispatcher dispatcher, BlockPos pos, BlockAndTintGetter level, VertexConsumer consumer,
									BlockState blockState, FluidState fluidState, Operation<Void> original) {
		Object2IntMap<BlockState> ids = iris$ids(consumer);
		if (ids == null) {
			original.call(dispatcher, pos, level, consumer, blockState, fluidState);
			return;
		}
		BlockSensitiveBufferBuilder builder = (BlockSensitiveBufferBuilder) consumer;
		builder.beginBlock(ids.getInt(fluidState.createLegacyBlock()), FLUID_RENDER_TYPE, (byte) blockState.getLightEmission(),
			pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
		try {
			original.call(dispatcher, pos, level, consumer, blockState, fluidState);
		} finally {
			builder.endBlock();
		}
	}

	@WrapOperation(method = "compile(Lnet/minecraft/core/SectionPos;Lnet/minecraft/client/renderer/chunk/RenderChunkRegion;Lcom/mojang/blaze3d/vertex/VertexSorting;Lnet/minecraft/client/renderer/SectionBufferBuilderPack;Ljava/util/List;)Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/BlockRenderDispatcher;renderBatched(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;Lnet/neoforged/neoforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V"))
	private void iris$blockContext(BlockRenderDispatcher dispatcher, BlockState state, BlockPos pos, BlockAndTintGetter level, PoseStack pose,
								   VertexConsumer consumer, boolean checkSides, RandomSource random, ModelData modelData, RenderType renderType,
								   Operation<Void> original) {
		BlockSensitiveBufferBuilder builder = BlockContext.begin(consumer, state, pos);
		if (builder == null) {
			original.call(dispatcher, state, pos, level, pose, consumer, checkSides, random, modelData, renderType);
			return;
		}
		try {
			original.call(dispatcher, state, pos, level, pose, consumer, checkSides, random, modelData, renderType);
		} finally {
			builder.endBlock();
		}
	}

	/** The pack's id map while chunks are meshed in the extended format, else null. */
	@Unique
	private static Object2IntMap<BlockState> iris$ids(VertexConsumer consumer) {
		if (!WorldRenderingSettings.INSTANCE.shouldUseExtendedVertexFormat() || !(consumer instanceof BlockSensitiveBufferBuilder)) {
			return null;
		}
		return WorldRenderingSettings.INSTANCE.getBlockStateIds();
	}
}
