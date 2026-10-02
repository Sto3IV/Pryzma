package net.pryzma.shader.batchedentityrendering.impl;

public interface Groupable {
	void startGroup();

	boolean maybeStartGroup();

	void endGroup();
}
