package net.pryzma.iris.batchedentityrendering.impl;

public interface DrawCallTrackingRenderBuffers {
	int getDrawCalls();

	int getRenderTypes();

	void resetDrawCounts();
}
