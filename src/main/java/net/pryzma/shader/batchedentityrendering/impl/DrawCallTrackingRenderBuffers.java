package net.pryzma.shader.batchedentityrendering.impl;

public interface DrawCallTrackingRenderBuffers {
	int getDrawCalls();

	int getRenderTypes();

	void resetDrawCounts();
}
