package net.pryzma.shader.mixin.vertices;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import com.mojang.renderpearl.api.vertex.VertexFormatElement;
import net.pryzma.shader.PryzmaShaders;
import net.pryzma.shader.uniforms.CapturedRenderingState;
import net.pryzma.shader.vertices.BlockContext;
import net.pryzma.shader.vertices.BlockSensitiveBufferBuilder;
import net.pryzma.shader.vertices.BufferBuilderPolygonView;
import net.pryzma.shader.vertices.ExtendedDataHelper;
import net.pryzma.shader.vertices.ExtendingBufferBuilder;
import net.pryzma.shader.vertices.ImmediateState;
import net.pryzma.shader.vertices.MojangBufferAccessor;
import net.pryzma.shader.vertices.NormI8;
import net.pryzma.shader.vertices.NormalHelper;
import net.pryzma.shader.vertices.ShaderExtendedBufferBuilder;
import net.pryzma.shader.vertices.ShaderVertexFormats;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;

/**
 * Dynamically and transparently extends the vanilla vertex formats with additional data
 */
@Mixin(BufferBuilder.class)
public abstract class MixinBufferBuilder implements VertexConsumer, BlockSensitiveBufferBuilder, ExtendingBufferBuilder, ShaderExtendedBufferBuilder {
	@Unique
	private final BufferBuilderPolygonView polygon = new BufferBuilderPolygonView();
	@Unique
	private final Vector3f normal = new Vector3f();
	@Unique
	private final long[] vertexOffsets = new long[4];
	@Shadow
	private int elementsToFill;
	@Shadow
	@Final
	private PrimitiveTopology primitiveTopology;
	@Shadow
	@Final
	private VertexFormat format;
	@Shadow
	private long vertexPointer;
	@Shadow
	private int vertices;
	@Unique
	private boolean extending;
	@Unique
	private boolean pryzma$isTerrain;
	@Unique
	private boolean injectNormalAndUV1;
	@Unique
	private int pryzma$vertexCount;
	@Unique
	private int currentBlock = -1;
	@Unique
	private byte currentRenderType = -1;
	/** Light emission of the block being meshed; at_midBlock.w, as PryzmaShaders writes it on the terrain chunk path. */
	@Unique
	private byte currentBlockEmission;
	@Unique
	private int currentLocalPosX;
	@Unique
	private int currentLocalPosY;
	@Unique
	private int currentLocalPosZ;
	@Shadow
	@Final
	private ByteBufferBuilder buffer;

	@Shadow
	public abstract VertexConsumer setNormal(float f, float g, float h);

	@ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true)
	private static VertexFormat pryzma$extendFormat(VertexFormat format) {
		if (ImmediateState.skipExtension.get() || !PryzmaShaders.isPackInUseQuick()) {
			return format;
		}

		if (format == DefaultVertexFormat.BLOCK || format == ShaderVertexFormats.TERRAIN) {
			return ShaderVertexFormats.TERRAIN;
		} else if (format == DefaultVertexFormat.ENTITY || format == ShaderVertexFormats.ENTITY) {
			return ShaderVertexFormats.ENTITY;
		} else if (format == DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP || format == ShaderVertexFormats.GLYPH) {
			return ShaderVertexFormats.GLYPH;
		}

		return format;
	}

	@Inject(method = "<init>", at = @At("RETURN"))
	private void pryzma$initExtensionState(ByteBufferBuilder buffer, PrimitiveTopology primitiveTopology, VertexFormat format, CallbackInfo ci) {
		if (format == ShaderVertexFormats.TERRAIN) {
			this.extending = true;
			this.pryzma$isTerrain = true;
			this.injectNormalAndUV1 = false;
		} else if (format == ShaderVertexFormats.ENTITY) {
			this.extending = true;
			this.pryzma$isTerrain = false;
			this.injectNormalAndUV1 = false;
		} else if (format == ShaderVertexFormats.GLYPH) {
			this.extending = true;
			this.pryzma$isTerrain = false;
			this.injectNormalAndUV1 = true;
		} else {
			this.extending = false;
			this.pryzma$isTerrain = false;
			this.injectNormalAndUV1 = false;
		}
	}

	@Inject(method = "addVertex(FFF)Lcom/mojang/blaze3d/vertex/VertexConsumer;", at = @At("RETURN"))
	private void injectMidBlock(float x, float y, float z, CallbackInfoReturnable<VertexConsumer> cir) {
		if (!this.extending) {
			return;
		}

		if (this.pryzma$isTerrain) {
			int block = currentBlock;
			byte rType = currentRenderType;
			byte emiss = currentBlockEmission;
			int lx = currentLocalPosX;
			int ly = currentLocalPosY;
			int lz = currentLocalPosZ;

			if (block == -1) {
				BlockContext.State s = BlockContext.current();
				if (s.active) {
					block = s.block;
					rType = s.renderType;
					emiss = s.emission;
					lx = s.localX;
					ly = s.localY;
					lz = s.localZ;
				}
			}

			long midBlockOffset = this.vertexPointer + ShaderVertexFormats.MID_BLOCK_ELEMENT.offset();
			MemoryUtil.memPutInt(midBlockOffset, ExtendedDataHelper.computeMidBlock(x, y, z, lx, ly, lz));
			MemoryUtil.memPutByte(midBlockOffset + 3, emiss);

			long entityOffset = this.vertexPointer + ShaderVertexFormats.ENTITY_ELEMENT.offset();
			MemoryUtil.memPutShort(entityOffset, (short) block);
			MemoryUtil.memPutShort(entityOffset + 2, rType);
		} else {
			long offset = this.vertexPointer + ShaderVertexFormats.ENTITY_ID_ELEMENT.offset();
			MemoryUtil.memPutShort(offset, (short) CapturedRenderingState.INSTANCE.getCurrentRenderedEntity());
			MemoryUtil.memPutShort(offset + 2, (short) CapturedRenderingState.INSTANCE.getCurrentRenderedBlockEntity());
			MemoryUtil.memPutShort(offset + 4, (short) CapturedRenderingState.INSTANCE.getCurrentRenderedItem());
		}
	}

	@Inject(method = "endLastVertex", at = @At("HEAD"))
	private void pryzma$beforeNext(CallbackInfo ci) {
		if (this.vertices == 0 || !extending) {
			return;
		}

		if (injectNormalAndUV1) {
			this.setNormal(0, 1, 0);
		}

		if (primitiveTopology != PrimitiveTopology.QUADS && primitiveTopology != PrimitiveTopology.TRIANGLES) {
			return;
		}

		vertexOffsets[pryzma$vertexCount] = vertexPointer - ((MojangBufferAccessor) buffer).getPointer();
		pryzma$vertexCount++;

		if (primitiveTopology == PrimitiveTopology.QUADS && pryzma$vertexCount == 4 || primitiveTopology == PrimitiveTopology.TRIANGLES && pryzma$vertexCount == 3) {
			fillExtendedData(pryzma$vertexCount);
		}
	}

	@Unique
	private int lastBlock = -1;

	@Override
	public void beginBlock(int block, byte renderType, byte blockEmission, int localPosX, int localPosY, int localPosZ) {
		currentBlock = block;
		currentRenderType = renderType;
		currentBlockEmission = blockEmission;
		currentLocalPosX = localPosX;
		currentLocalPosY = localPosY;
		currentLocalPosZ = localPosZ;
	}

	@Override
	public void overrideBlock(int block) {
		lastBlock = currentBlock;
		currentBlock = block;
	}

	@Override
	public void restoreBlock() {
		if (lastBlock != -1) {
			currentBlock = lastBlock;
			lastBlock = -1;
		}
	}

	@Override
	public void endBlock() {
		currentBlock = -1;
		currentRenderType = -1;
		currentBlockEmission = 0;
	}

	@Override
	public void ignoreMidBlock(boolean b) {

	}

	@Unique
	private void fillExtendedData(int vertexAmount) {
		pryzma$vertexCount = 0;

		int stride = format.getVertexSize();
		polygon.setup(((MojangBufferAccessor) buffer).getPointer(), vertexOffsets, stride, vertexAmount);

		float midU = 0;
		float midV = 0;

		for (int vertex = 0; vertex < vertexAmount; vertex++) {
			midU += polygon.u(vertex);
			midV += polygon.v(vertex);
		}

		midU /= vertexAmount;
		midV /= vertexAmount;

		int midTexOffset = format.getElement("mc_midTexCoord").offset();
		int normalOffset = format.getElement("Normal").offset();
		int tangentOffset = format.getElement("at_tangent").offset();
		if (vertexAmount == 3) {
			for (int vertex = 0; vertex < vertexAmount; vertex++) {
				long newPointer = ((MojangBufferAccessor) buffer).getPointer() + vertexOffsets[vertex];
				int vertexNormal = MemoryUtil.memGetInt(newPointer + normalOffset);

				int tangent = NormalHelper.computeTangentSmooth(NormI8.unpackX(vertexNormal), NormI8.unpackY(vertexNormal), NormI8.unpackZ(vertexNormal), polygon);

				MemoryUtil.memPutFloat(newPointer + midTexOffset, midU);
				MemoryUtil.memPutFloat(newPointer + midTexOffset + 4, midV);
				MemoryUtil.memPutInt(newPointer + tangentOffset, tangent);
			}
		} else {
			boolean recalculateNormal = ImmediateState.isRenderingLevel;
			NormalHelper.computeFaceNormal(normal, polygon);
			int packedNormal = 0;
			if (recalculateNormal) {
				packedNormal = NormI8.pack(normal.x, normal.y, normal.z, 0.0f);
			}
			int tangent = NormalHelper.computeTangent(normal.x, normal.y, normal.z, polygon);

			for (int vertex = 0; vertex < vertexAmount; vertex++) {
				long newPointer = ((MojangBufferAccessor) buffer).getPointer() + vertexOffsets[vertex];

				MemoryUtil.memPutFloat(newPointer + midTexOffset, midU);
				MemoryUtil.memPutFloat(newPointer + midTexOffset + 4, midV);
				if (recalculateNormal) {
					MemoryUtil.memPutInt(newPointer + normalOffset, packedNormal);
				}
				MemoryUtil.memPutInt(newPointer + tangentOffset, tangent);
			}
		}

		Arrays.fill(vertexOffsets, 0);
	}

	@Override
	public void pryzma$beginWithoutExtending(PrimitiveTopology topology, VertexFormat vertexFormat) {
		this.extending = false;
	}

	@Override
	public VertexFormat pryzma$format() {
		return this.format;
	}

	@Override
	public PrimitiveTopology pryzma$mode() {
		return this.primitiveTopology;
	}

	@Override
	public boolean pryzma$extending() {
		return this.extending;
	}

	@Override
	public boolean pryzma$isTerrain() {
		return this.pryzma$isTerrain;
	}

	@Override
	public boolean pryzma$injectNormalAndUV1() {
		return this.injectNormalAndUV1;
	}

	@Override
	public int pryzma$vertexCount() {
		return this.pryzma$vertexCount;
	}

	@Override
	public void pryzma$incrementVertexCount() {
		this.pryzma$vertexCount++;
	}

	@Override
	public void pryzma$resetVertexCount() {
		this.pryzma$vertexCount = 0;
	}

	@Override
	public short pryzma$currentBlock() {
		return (short) this.currentBlock;
	}

	@Override
	public short pryzma$currentRenderType() {
		return this.currentRenderType;
	}

	@Override
	public int pryzma$currentLocalPosX() {
		return this.currentLocalPosX;
	}

	@Override
	public int pryzma$currentLocalPosY() {
		return this.currentLocalPosY;
	}

	@Override
	public int pryzma$currentLocalPosZ() {
		return this.currentLocalPosZ;
	}
}
