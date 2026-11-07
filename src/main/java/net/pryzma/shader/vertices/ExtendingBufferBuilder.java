package net.pryzma.shader.vertices;

import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.vertex.VertexFormat;

public interface ExtendingBufferBuilder {
	void pryzma$beginWithoutExtending(PrimitiveTopology topology, VertexFormat vertexFormat);
}
