package net.pryzma.shader.vertices;

import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.vertex.VertexFormat;

public interface ShaderExtendedBufferBuilder {
	VertexFormat pryzma$format();

	PrimitiveTopology pryzma$mode();

	boolean pryzma$extending();

	boolean pryzma$isTerrain();

	boolean pryzma$injectNormalAndUV1();

	int pryzma$vertexCount();

	void pryzma$incrementVertexCount();

	void pryzma$resetVertexCount();

	short pryzma$currentBlock();

	short pryzma$currentRenderType();

	int pryzma$currentLocalPosX();

	int pryzma$currentLocalPosY();

	int pryzma$currentLocalPosZ();
}
