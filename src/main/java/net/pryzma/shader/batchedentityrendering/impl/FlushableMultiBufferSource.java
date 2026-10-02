package net.pryzma.shader.batchedentityrendering.impl;

public interface FlushableMultiBufferSource {
	void flushNonTranslucentContent();

	void flushTranslucentContent();
}
