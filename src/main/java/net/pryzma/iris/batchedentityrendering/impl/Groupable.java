package net.pryzma.iris.batchedentityrendering.impl;

public interface Groupable {
	void startGroup();

	boolean maybeStartGroup();

	void endGroup();
}
