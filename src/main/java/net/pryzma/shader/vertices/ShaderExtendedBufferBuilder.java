package net.pryzma.shader.vertices;

import com.mojang.blaze3d.vertex.VertexFormat;

public interface ShaderExtendedBufferBuilder {
	VertexFormat pryzma$format();

	VertexFormat.Mode pryzma$mode();

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
