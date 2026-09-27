package net.pryzma.iris.batchedentityrendering.impl;

public interface MemoryTrackingBuffer {
	long getAllocatedSize();

	long getUsedSize();

	void freeAndDeleteBuffer();
}
