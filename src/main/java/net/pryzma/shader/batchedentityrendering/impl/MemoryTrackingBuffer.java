package net.pryzma.shader.batchedentityrendering.impl;

public interface MemoryTrackingBuffer {
	long getAllocatedSize();

	long getUsedSize();

	void freeAndDeleteBuffer();
}
