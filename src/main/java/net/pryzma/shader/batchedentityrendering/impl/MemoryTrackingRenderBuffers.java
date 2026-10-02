package net.pryzma.shader.batchedentityrendering.impl;

public interface MemoryTrackingRenderBuffers {
	long getEntityBufferAllocatedSize();

	long getMiscBufferAllocatedSize();

	int getMaxBegins();

	void freeAndDeleteBuffers();
}
